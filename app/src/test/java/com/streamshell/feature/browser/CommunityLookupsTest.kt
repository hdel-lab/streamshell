package com.streamshell.feature.browser

import com.streamshell.core.community.DeArrowRepository
import com.streamshell.core.community.DislikeRepository
import com.streamshell.core.model.AppSettings
import com.streamshell.core.sponsor.SponsorBlockRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CommunityLookups with fake HTTP answers (no network): what reaches the page, and when nothing does. */
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityLookupsTest {
    private val url = "https://m.youtube.com/watch?v=dQw4w9WgXcQ"

    /** Waits for the look-ups started in this test (they switch to the IO dispatcher inside the repositories). */
    private suspend fun kotlinx.coroutines.CoroutineScope.finish() {
        coroutineContext[kotlinx.coroutines.Job]?.children?.toList()?.forEach { it.join() }
    }

    private fun sponsorRepo(calls: MutableList<String>) = SponsorBlockRepository({ u ->
        calls += u
        200 to """[{"videoID":"dQw4w9WgXcQ","segments":[{"segment":[10.0,30.0],"category":"sponsor","UUID":"a","actionType":"skip"}]}]"""
    })

    @Test fun segmentsLookedUpOncePerVideoAndTab() = runTest {
        val calls = ArrayList<String>()
        val applied = ArrayList<String>()
        val c = CommunityLookups(this, { AppSettings() }, { _, _ -> }, { id, vid, segs -> applied += "$id:$vid:${segs.size}" }, repos = CommunityRepos(sponsor = sponsorRepo(calls)))
        c.lookUpSegments(1, url)
        c.lookUpSegments(1, url)
        finish()
        assertEquals(listOf("1:dQw4w9WgXcQ:1"), applied)
        c.forgetSegments()
        c.lookUpSegments(1, url)
        finish()
        assertEquals(2, applied.size)
    }

    @Test fun nothingWhenSwitchedOffOrNoVideo() = runTest {
        val calls = ArrayList<String>()
        val scripts = ArrayList<String>()
        val off = AppSettings(sponsorBlock = false, returnDislikes = false, deArrow = false)
        val c = CommunityLookups(
            this,
            { off },
            { _, s -> scripts += s },
            { _, _, _ -> scripts += "segments" },
            CommunityRepos(
                sponsor = sponsorRepo(calls),
                dislikes = DislikeRepository {
                    calls += it
                    200 to "{}"
                },
                deArrow = DeArrowRepository {
                    calls += it
                    200 to "{}"
                }
            )
        )
        c.lookUpSegments(1, url)
        c.lookUpSegments(1, "https://example.com/")
        c.dislikes(1, "dQw4w9WgXcQ")
        c.titles(1, listOf("dQw4w9WgXcQ"))
        finish()
        assertTrue(calls.isEmpty() && scripts.isEmpty())
    }

    @Test fun dislikeAndTitleAnswersGoToThePage() = runTest {
        val scripts = ArrayList<String>()
        val on = AppSettings(returnDislikes = true, deArrow = true)
        val c = CommunityLookups(
            this,
            { on },
            { id, s -> scripts += "$id $s" },
            { _, _, _ -> },
            CommunityRepos(
                dislikes = DislikeRepository { 200 to """{"id":"dQw4w9WgXcQ","dislikes":42}""" },
                deArrow = DeArrowRepository { 200 to """{"dQw4w9WgXcQ":{"titles":[{"title":"Better","original":false,"votes":1}]}}""" }
            )
        )
        c.dislikes(3, "dQw4w9WgXcQ")
        c.titles(3, listOf("dQw4w9WgXcQ"))
        finish()
        assertTrue(scripts.any { it.startsWith("3 ") && it.contains("__ssDislikes(\"dQw4w9WgXcQ\",42)") })
        assertTrue(scripts.any { it.contains("__ssDeArrow({\"dQw4w9WgXcQ\":\"Better\"})") })
    }
}
