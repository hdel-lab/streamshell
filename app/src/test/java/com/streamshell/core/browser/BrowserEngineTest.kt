package com.streamshell.core.browser

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.streamshell.core.model.AppSettings
import com.streamshell.core.privacy.DomainFilterEngine
import com.streamshell.core.privacy.DomainRule
import com.streamshell.core.privacy.EngineState
import com.streamshell.core.privacy.FilterDecision
import com.streamshell.core.privacy.InMemoryBlockedRequestRepository
import com.streamshell.core.privacy.PrivacyCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** Exercises the real engine wiring (filter at the WebView request hook, navigation policy) on Robolectric's WebView shadow. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BrowserEngineTest {
    private val repo = InMemoryBlockedRequestRepository()
    private val engineRules = DomainFilterEngine(listOf(DomainRule("ads.test", false, false, "builtin")))
    private val privacy = PrivacyCoordinator(
        CoroutineScope(Dispatchers.Unconfined),
        MutableStateFlow(AppSettings()),
        MutableStateFlow<EngineState>(EngineState.Ready(engineRules, 1, listOf("builtin"))),
        repo
    )
    private val net = object : NetworkMonitor {
        override val isOnline = MutableStateFlow(true)
    }
    private val blocked = ArrayList<String>()
    private val external = ArrayList<String>()
    private val engine = BrowserEngine(ApplicationProvider.getApplicationContext(), privacy, net).also {
        it.listener = object : EngineListener {
            override fun onUrlChanged(tabId: Long, url: String) {}
            override fun onTitle(tabId: Long, url: String?, title: String) {}
            override fun onVisited(tabId: Long, url: String, title: String) {}
            override fun onNavigationBlocked(tabId: Long, url: String, host: String, decision: FilterDecision.Block) {
                blocked += host
            }
            override fun onExternal(uri: String) {
                external += uri
            }
            override fun onDownload(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {}
        }
    }

    private fun req(url: String, mainFrame: Boolean = false) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private fun webView(id: Long) = engine.attach(id, ApplicationProvider.getApplicationContext()) as WebView

    @Test fun pagesLoadAndHttpIsUpgraded() {
        assertEquals(BrowserEngine.LoadResult.STARTED, engine.load(1, "http://example.com/a"))
        assertEquals("https://example.com/a", shadowOf(webView(1)).lastLoadedUrl)
    }

    @Test fun blockedSiteIsNotLoadedAndCanBeOpenedAnyway() {
        assertEquals(BrowserEngine.LoadResult.BLOCKED, engine.load(1, "https://ads.test/landing"))
        assertEquals(listOf("ads.test"), blocked)
        assertNull(if (engine.hasView(1)) shadowOf(webView(1)).lastLoadedUrl else null)
        assertEquals(BrowserEngine.LoadResult.STARTED, engine.load(1, "https://ads.test/landing", bypassFilter = true))
        assertEquals("https://ads.test/landing", shadowOf(webView(1)).lastLoadedUrl)
    }

    @Test fun subResourceRequestsAreReallyBlockedAndCounted() {
        engine.load(1, "https://news.example/")
        val wv = webView(1)
        val client = shadowOf(wv).webViewClient
        client.onPageStarted(wv, "https://news.example/", null)
        val r = client.shouldInterceptRequest(wv, req("https://cdn.ads.test/pixel.gif?id=secret"))
        assertEquals(403, r!!.statusCode)
        assertEquals(0, r.data.available())
        assertNull(client.shouldInterceptRequest(wv, req("https://cdn.example/app.js")))
        assertNull(client.shouldInterceptRequest(wv, req("https://ads.test/", mainFrame = true))) // main frame is never answered here
        ShadowLooper.idleMainLooper()
        assertEquals(1L, repo.blockedCount.value)
        assertEquals("cdn.ads.test", repo.recent.value.single().host)
        assertEquals(1, engine.pages.value[1L]?.blocked)
    }

    @Test fun linksThatLeaveTheBrowserGoToOtherAppsOnlyByPolicy() {
        engine.load(1, "https://news.example/")
        val wv = webView(1)
        val client = shadowOf(wv).webViewClient
        assertTrue(client.shouldOverrideUrlLoading(wv, req("mailto:a@b.de", mainFrame = true)))
        assertEquals(listOf("mailto:a@b.de"), external)
        assertTrue(client.shouldOverrideUrlLoading(wv, req("javascript:alert(1)", mainFrame = true)))
        assertEquals(1, external.size)
        assertTrue(client.shouldOverrideUrlLoading(wv, req("https://ads.test/x", mainFrame = true))) // blocked page
        assertEquals(listOf("ads.test"), blocked)
        assertEquals(false, client.shouldOverrideUrlLoading(wv, req("https://vimeo.com/1", mainFrame = true)))
        assertEquals(false, client.shouldOverrideUrlLoading(wv, req("https://cdn.example/frame", mainFrame = false)))
    }

    @Test fun closingATabDestroysItsState() {
        engine.load(1, "https://example.com/")
        assertTrue(engine.hasView(1))
        engine.close(1)
        assertTrue(!engine.hasView(1))
        assertNull(engine.pages.value[1L])
    }
}
