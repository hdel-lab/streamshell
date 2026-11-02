package com.streamshell.core.privacy

import com.streamshell.core.model.AppSettings
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemMeta : FilterMetaStore {
    var json: String? = null
    override suspend fun load() = json
    override suspend fun save(json: String) {
        this.json = json
    }
}

private class MemFiles : FilterFileStore {
    val map = HashMap<String, ByteArray>()
    var failWrites = false
    override fun read(id: String) = map[id]
    override fun writeAtomic(id: String, bytes: ByteArray): Boolean {
        if (failWrites) return false
        map[id] = bytes
        return true
    }
    override fun delete(id: String) {
        map.remove(id)
    }
}

private class FakeDownloader : FilterListDownloader {
    val responses = HashMap<String, () -> DownloadResult>()
    val calls = ArrayList<List<String?>>()
    override fun download(url: String, allowedHosts: Set<String>, etag: String?, lastModified: String?): DownloadResult {
        calls += listOf(url, etag, lastModified)
        return (responses[url] ?: { throw IOException("offline") })()
    }
}

private const val BUILTIN = "||builtin-ads.test^"
private val EP_URL = FilterSources.recommended.first { it.id == "easyprivacy" }.url
private fun data(text: String, etag: String? = "\"v1\"") = DownloadResult.Data(text.toByteArray(), etag, null)

@OptIn(ExperimentalCoroutinesApi::class)
class FilterRepositoryTest {
    private val meta = MemMeta()
    private val files = MemFiles()
    private val dl = FakeDownloader()
    private var now = 1_000L

    private fun repo() = DefaultFilterListRepository({ BUILTIN.toByteArray() }, meta, files, dl, Dispatchers.Unconfined) { now }

    private fun ready(r: DefaultFilterListRepository): EngineState.Ready = r.engineState.value as EngineState.Ready

    @Test fun offlineStartUsesBuiltinThenCachedLists() = runTest {
        val r = repo()
        r.initialize()
        assertEquals(listOf("builtin"), ready(r).sourceIds)
        assertTrue(ready(r).engine.evaluate("builtin-ads.test", null) is FilterDecision.Block)

        dl.responses[EP_URL] = { data("||tracker.test^") }
        r.updateAll()

        // New process: same stores, no network at all.
        val r2 = repo()
        dl.responses.clear()
        r2.initialize()
        assertTrue("easyprivacy" in ready(r2).sourceIds)
        val d = ready(r2).engine.evaluate("tracker.test", null)
        assertEquals(FilterDecision.Block("||tracker.test^", "easyprivacy"), d)
    }

    @Test fun successfulUpdateStoresListAndStatus() = runTest {
        val r = repo()
        r.initialize()
        dl.responses[EP_URL] = { data("||a.test^\n||b.test^\n##cosmetic\n", etag = "\"e1\"") }
        val s = r.updateAll(setOf("easyprivacy"))
        assertEquals(UpdateSummary(1, 0, 0), s)
        val st = r.entries.value.first { it.subscription.id == "easyprivacy" }.status
        assertEquals(2, st.ruleCount)
        assertEquals(1_000L, st.lastSuccessMs)
        assertEquals("\"e1\"", st.etag)
        assertNull(st.error)
        assertNotNull(files.read("easyprivacy"))
    }

    @Test fun corruptUpdateKeepsLastKnownGood() = runTest {
        val r = repo()
        r.initialize()
        dl.responses[EP_URL] = { data("||good.test^") }
        r.updateAll(setOf("easyprivacy"))
        val good = files.read("easyprivacy")!!.toList()

        now = 2_000
        dl.responses[EP_URL] = { data("<html>captive portal</html>") } // parses to zero rules
        val s = r.updateAll(setOf("easyprivacy"))
        assertEquals(1, s.failed)
        assertEquals(good, files.read("easyprivacy")!!.toList())
        assertTrue(ready(r).engine.evaluate("good.test", null) is FilterDecision.Block)
        val st = r.entries.value.first { it.subscription.id == "easyprivacy" }.status
        assertNotNull(st.error)
        assertEquals(1_000L, st.lastSuccessMs) // last SUCCESS is not advanced by a failure
        assertEquals(2_000L, st.lastAttemptMs)
    }

    @Test fun binaryAndOversizedDownloadsAreRejected() = runTest {
        val r = repo()
        r.initialize()
        dl.responses[EP_URL] = { DownloadResult.Data(byteArrayOf(0, 1, 2), null, null) }
        assertEquals(1, r.updateAll(setOf("easyprivacy")).failed)
        dl.responses[EP_URL] = { DownloadResult.Data(ByteArray(FilterListValidator.MAX_BYTES + 1) { 'a'.code.toByte() }, null, null) }
        assertEquals(1, r.updateAll(setOf("easyprivacy")).failed)
        assertNull(files.read("easyprivacy"))
        assertTrue(ready(r).engine.evaluate("builtin-ads.test", null) is FilterDecision.Block)
    }

    @Test fun networkFailureShowsErrorAndKeepsRules() = runTest {
        val r = repo()
        r.initialize()
        dl.responses[EP_URL] = { data("||good.test^") }
        r.updateAll(setOf("easyprivacy"))
        dl.responses.clear() // now offline
        val s = r.updateAll(setOf("easyprivacy"))
        assertEquals(1, s.failed)
        assertEquals("offline", r.entries.value.first { it.subscription.id == "easyprivacy" }.status.error)
        assertTrue(ready(r).engine.evaluate("good.test", null) is FilterDecision.Block)
    }

    @Test fun failedStorageWriteKeepsOldCopy() = runTest {
        val r = repo()
        r.initialize()
        dl.responses[EP_URL] = { data("||old.test^") }
        r.updateAll(setOf("easyprivacy"))
        files.failWrites = true
        dl.responses[EP_URL] = { data("||new.test^") }
        assertEquals(1, r.updateAll(setOf("easyprivacy")).failed)
        assertTrue(ready(r).engine.evaluate("old.test", null) is FilterDecision.Block)
        assertTrue(ready(r).engine.evaluate("new.test", null) is FilterDecision.Allow)
    }

    @Test fun etagIsSentAndNotModifiedIsHandled() = runTest {
        val r = repo()
        r.initialize()
        dl.responses[EP_URL] = { data("||a.test^", etag = "\"e1\"") }
        r.updateAll(setOf("easyprivacy"))
        dl.responses[EP_URL] = { DownloadResult.NotModified }
        now = 5_000
        val s = r.updateAll(setOf("easyprivacy"))
        assertEquals(UpdateSummary(0, 1, 0), s)
        assertEquals("\"e1\"", dl.calls.last()[1])
        assertEquals(5_000L, r.entries.value.first { it.subscription.id == "easyprivacy" }.status.lastSuccessMs)
    }

    @Test fun validatorsAreNotSentWithoutACachedCopy() = runTest {
        val r = repo()
        r.initialize()
        dl.responses[EP_URL] = { data("||a.test^", etag = "\"e1\"") }
        r.updateAll(setOf("easyprivacy"))
        files.delete("easyprivacy")
        r.updateAll(setOf("easyprivacy"))
        assertNull(dl.calls.last()[1])
    }

    @Test fun addValidatesAndRequiresTrustForNewHosts() = runTest {
        val r = repo()
        r.initialize()
        assertTrue(r.add("x", "http://insecure.test/list.txt") is AddResult.Invalid)
        assertTrue(r.add("", "https://lists.example.org/a.txt") is AddResult.Invalid)
        assertTrue(r.add("x", "https://easylist.to/easylist/easylist.txt") is AddResult.Invalid) // duplicate
        val need = r.add("Mine", "https://lists.example.org/a.txt")
        assertEquals(AddResult.NeedsTrust("lists.example.org"), need)
        assertTrue("lists.example.org" !in r.trustedHosts.value)
        val ok = r.add("Mine", "https://lists.example.org/a.txt", trustHost = true) as AddResult.Added
        assertTrue("lists.example.org" in r.trustedHosts.value)
        assertTrue(r.entries.value.any { it.subscription.id == ok.id && !it.subscription.recommended })
    }

    @Test fun listCountIsCapped() = runTest {
        val r = repo()
        r.initialize()
        var added = 0
        for (i in 0 until 40) if (r.add("L$i", "https://easylist.to/l$i.txt") is AddResult.Added) added++
        assertEquals(DefaultFilterListRepository.MAX_LISTS - FilterSources.recommended.size, added)
    }

    @Test fun removeDeletesUserListsButOnlyDisablesRecommended() = runTest {
        val r = repo()
        r.initialize()
        val id = (r.add("Mine", "https://easylist.to/mine.txt") as AddResult.Added).id
        dl.responses["https://easylist.to/mine.txt"] = { data("||mine.test^") }
        r.updateAll(setOf(id))
        r.remove(id)
        assertTrue(r.entries.value.none { it.subscription.id == id })
        assertNull(files.read(id))
        r.remove("easyprivacy")
        assertTrue(r.entries.value.any { it.subscription.id == "easyprivacy" })
    }

    @Test fun disablingAListRemovesItsRulesImmediately() = runTest {
        val r = repo()
        r.initialize()
        dl.responses[EP_URL] = { data("||tracker.test^") }
        r.updateAll(setOf("easyprivacy"))
        r.setEnabled("easyprivacy", false)
        assertTrue(ready(r).engine.evaluate("tracker.test", null) is FilterDecision.Allow)
        r.setEnabled("easyprivacy", true)
        assertTrue(ready(r).engine.evaluate("tracker.test", null) is FilterDecision.Block)
    }

    @Test fun resetRestoresRecommendedDefaultsAndClearsCaches() = runTest {
        val r = repo()
        r.initialize()
        dl.responses[EP_URL] = { data("||tracker.test^") }
        r.updateAll(setOf("easyprivacy"))
        r.add("Mine", "https://lists.example.org/a.txt", trustHost = true)
        r.setEnabled("easylist", true)
        r.resetToDefaults()
        assertEquals(FilterSources.recommended.map { it.id }, r.entries.value.map { it.subscription.id })
        assertEquals(FilterSources.defaultEnabled, r.entries.value.filter { it.subscription.enabled }.map { it.subscription.id }.toSet())
        assertEquals(FilterSources.defaultTrustedHosts, r.trustedHosts.value)
        assertTrue(files.map.isEmpty())
    }

    @Test fun invalidBuiltinFailsSafely() = runTest {
        val r = DefaultFilterListRepository({ ByteArray(0) }, meta, files, dl, Dispatchers.Unconfined) { now }
        r.initialize()
        assertTrue(r.engineState.value is EngineState.Failed)
    }

    @Test fun corruptCachedFileIsIgnoredNotFatal() = runTest {
        val r = repo()
        r.initialize()
        dl.responses[EP_URL] = { data("||tracker.test^") }
        r.updateAll(setOf("easyprivacy"))
        files.map["easyprivacy"] = byteArrayOf(0, 0, 0) // disk corruption
        val r2 = repo()
        r2.initialize()
        assertTrue(r2.engineState.value is EngineState.Ready)
        assertTrue(ready(r2).engine.evaluate("builtin-ads.test", null) is FilterDecision.Block)
    }

    @Test fun statusResolverIsHonest() {
        val ready = EngineState.Ready(DomainFilterEngine.EMPTY, 7, emptyList())
        assertEquals(PrivacyStatus.Disabled, PrivacyStatusResolver.resolve(FilterPolicy(false), ready))
        assertEquals(PrivacyStatus.Active(7), PrivacyStatusResolver.resolve(FilterPolicy(true), ready))
        assertEquals(PrivacyStatus.Initializing, PrivacyStatusResolver.resolve(FilterPolicy(true), EngineState.Initializing))
        assertEquals(PrivacyStatus.Failed("x"), PrivacyStatusResolver.resolve(FilterPolicy(true), EngineState.Failed("x")))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyCoordinatorTest {
    private val engine = DomainFilterEngine(
        listOf(
            DomainRule("ads.test", false, false, "builtin"),
            DomainRule("tracker.test", false, true, "easyprivacy"),
            DomainRule("doubleclick.net", false, false, "easylist"),
            DomainRule("googlevideo.com", false, false, "easylist") // a bad list rule that would break video
        )
    )

    private fun TestScope.coordinator(settings: AppSettings = AppSettings(), repo: BlockedRequestRepository = InMemoryBlockedRequestRepository()): Pair<PrivacyCoordinator, BlockedRequestRepository> {
        val s = MutableStateFlow(settings)
        val e = MutableStateFlow<EngineState>(EngineState.Ready(engine, 4, listOf("builtin")))
        val c = PrivacyCoordinator(backgroundScope, s, e, repo) { 42L }
        advanceUntilIdle()
        return c to repo
    }

    @Test fun subResourcesAreBlockedAndPagesAreNot() = runTest(UnconfinedTestDispatcher()) {
        val (c, _) = coordinator()
        assertTrue(c.decideRequest("https://ads.test/pixel.gif", "https://news.example/", isMainFrame = false) is FilterDecision.Block)
        assertEquals(FilterDecision.Allow(AllowReason.MAIN_FRAME), c.decideRequest("https://ads.test/", null, isMainFrame = true))
        assertTrue(c.decideRequest("https://cdn.example/app.js", "https://news.example/", false) is FilterDecision.Allow)
        assertEquals(FilterDecision.Allow(AllowReason.NOT_HTTP), c.decideRequest("blob:https://x/1", "https://news.example/", false))
        assertEquals(FilterDecision.Allow(AllowReason.NOT_HTTP), c.decideRequest("data:image/png;base64,AA", "https://news.example/", false))
    }

    @Test fun thirdPartyRulesUseThePageHost() = runTest(UnconfinedTestDispatcher()) {
        val (c, _) = coordinator()
        assertTrue(c.decideRequest("https://tracker.test/t.js", "https://news.example/", false) is FilterDecision.Block)
        assertEquals(FilterDecision.Allow(AllowReason.FIRST_PARTY), c.decideRequest("https://tracker.test/t.js", "https://www.tracker.test/", false))
    }

    @Test fun functionalHostsSurviveBadListRulesButNotUserRules() = runTest(UnconfinedTestDispatcher()) {
        val (c, _) = coordinator()
        assertEquals(FilterDecision.Allow(AllowReason.FUNCTIONAL), c.decideRequest("https://rr1---sn-x.googlevideo.com/videoplayback", "https://m.youtube.com/", false))
        val (strict, _) = coordinator(AppSettings(customBlockedDomains = setOf("googlevideo.com")))
        assertTrue(strict.decideRequest("https://rr1.googlevideo.com/v", "https://m.youtube.com/", false) is FilterDecision.Block)
        assertTrue(c.decideRequest("https://static.doubleclick.net/x", "https://m.youtube.com/", false) is FilterDecision.Block)
    }

    @Test fun siteExemptionTurnsBlockingOffForThatSiteOnly() = runTest(UnconfinedTestDispatcher()) {
        val (c, _) = coordinator(AppSettings(filterExemptSites = setOf("news.example")))
        assertEquals(FilterDecision.Allow(AllowReason.SITE_EXEMPT), c.decideRequest("https://ads.test/x", "https://www.news.example/a", false))
        assertTrue(c.decideRequest("https://ads.test/x", "https://other.example/", false) is FilterDecision.Block)
        assertEquals(FilterDecision.Allow(AllowReason.SITE_EXEMPT), c.decideNavigation("https://sub.news.example/"))
    }

    @Test fun navigationChecksTheTargetSite() = runTest(UnconfinedTestDispatcher()) {
        val (c, _) = coordinator()
        assertTrue(c.decideNavigation("https://ads.test/landing") is FilterDecision.Block)
        assertTrue(c.decideNavigation("https://tracker.test/") is FilterDecision.Allow) // third-party-only rule does not apply to the page itself
        assertTrue(c.decideNavigation("https://example.com/") is FilterDecision.Allow)
    }

    @Test fun masterToggleOffAllowsEverythingAndCountsNothing() = runTest(UnconfinedTestDispatcher()) {
        val (c, repo) = coordinator(AppSettings(filteringEnabled = false))
        assertEquals(FilterDecision.Allow(AllowReason.DISABLED), c.decideRequest("https://ads.test/x", "https://a.example/", false))
        assertEquals(FilterDecision.Allow(AllowReason.DISABLED), c.decideNavigation("https://ads.test/"))
        c.recordAllowed("https://ads.test/x")
        assertEquals(0L, repo.observedCount.value)
    }

    @Test fun userAllowRestoresARequest() = runTest(UnconfinedTestDispatcher()) {
        val (c, _) = coordinator(AppSettings(customAllowedDomains = setOf("ads.test")))
        assertEquals(FilterDecision.Allow(AllowReason.USER_ALLOW), c.decideRequest("https://ads.test/x", "https://a.example/", false))
    }

    @Test fun blockedCountsOnlyWhenRecordedAndStoresHostOnly() = runTest(UnconfinedTestDispatcher()) {
        val (c, repo) = coordinator()
        val d = c.decideRequest("https://sub.ads.test/p?q=secret-term", "https://a.example/", false) as FilterDecision.Block
        assertEquals(0L, repo.blockedCount.value) // a decision alone is not a block
        c.recordBlocked("sub.ads.test", d)
        assertEquals(1L, repo.blockedCount.value)
        val rec = repo.recent.value.single()
        assertEquals("sub.ads.test", rec.host)
        assertEquals("builtin", rec.source)
        assertTrue(rec.toString().contains("secret").not())
    }

    @Test fun allowedRequestsAreCountedByHost() = runTest(UnconfinedTestDispatcher()) {
        val (c, repo) = coordinator()
        c.recordAllowed("https://cdn.example/app.js?token=abc")
        assertEquals(1L, repo.observedCount.value)
        assertEquals(mapOf("cdn.example" to 1), repo.observedHosts.value)
    }

    @Test fun detailedLoggingOffKeepsCountersOnly() = runTest(UnconfinedTestDispatcher()) {
        val (c, repo) = coordinator(AppSettings(detailedLogging = false))
        c.recordAllowed("https://cdn.example/x")
        assertTrue(repo.observedHosts.value.isEmpty())
        c.recordBlocked("ads.test", FilterDecision.Block("||ads.test^", "builtin"))
        assertEquals("(logging off)", repo.recent.value.single().host)
    }

    @Test fun recentLogIsBounded() {
        val repo = InMemoryBlockedRequestRepository(capacity = 5)
        repeat(20) { repo.recordBlocked(BlockedRequest("h$it.test", "||h^", null, it.toLong())) }
        assertEquals(5, repo.recent.value.size)
        assertEquals(20L, repo.blockedCount.value)
        assertEquals("h19.test", repo.recent.value.first().host)
    }

    @Test fun functionalHostList() {
        for (h in listOf("accounts.google.com", "rr3---sn-4g5e6nsz.googlevideo.com", "i.ytimg.com", "yt3.ggpht.com", "www.gstatic.com", "m.youtube.com", "f.vimeocdn.com")) {
            assertTrue(h, FunctionalHosts.isFunctional(h))
        }
        for (h in listOf("doubleclick.net", "googleads.g.doubleclick.net", "googlesyndication.com", "evil-googlevideo.com", "googlevideo.com.evil.test")) {
            assertTrue(h, !FunctionalHosts.isFunctional(h))
        }
    }
}
