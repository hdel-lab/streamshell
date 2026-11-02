package com.streamshell.feature.browser

import com.streamshell.core.browser.YouTubeExtras
import com.streamshell.core.community.DeArrowRepository
import com.streamshell.core.community.DislikeRepository
import com.streamshell.core.model.AppSettings
import com.streamshell.core.sponsor.SkipSegment
import com.streamshell.core.sponsor.SponsorBlockApi
import com.streamshell.core.sponsor.SponsorBlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The three community data sources (replaceable by fakes in tests). */
data class CommunityRepos(
    val sponsor: SponsorBlockRepository = SponsorBlockRepository(),
    val dislikes: DislikeRepository = DislikeRepository(),
    val deArrow: DeArrowRepository = DeArrowRepository()
)

/**
 * Look-ups of community data for YouTube pages — SponsorBlock segments, dislike counts (Return YouTube Dislike) and
 * DeArrow titles — and handing the answers to the page. Runs in the owner's [scope] (cancelled with it), reads the
 * current [settings] at each request, and talks to the page only through [runInPage] / [applySegments], so it can be
 * tested with fake repositories and without a WebView.
 */
class CommunityLookups(
    private val scope: CoroutineScope,
    private val settings: () -> AppSettings,
    private val runInPage: (tabId: Long, script: String) -> Unit,
    private val applySegments: (tabId: Long, videoId: String, segments: List<SkipSegment>) -> Unit,
    private val repos: CommunityRepos = CommunityRepos()
) {
    private val sponsor get() = repos.sponsor
    private val dislikes get() = repos.dislikes
    private val deArrow get() = repos.deArrow

    /** Video whose segments were asked for, per tab (one look-up per video and tab). */
    private val sponsorVideo = HashMap<Long, String>()

    /** New SponsorBlock settings: the next video is looked up again. */
    fun forgetSegments() = sponsorVideo.clear()

    fun lookUpSegments(tabId: Long, url: String) {
        val s = settings()
        val videoId = SponsorBlockApi.videoIdOf(url)
        if (!s.sponsorBlock || videoId == null || sponsorVideo[tabId] == videoId) return
        sponsorVideo[tabId] = videoId
        scope.launch {
            val segs = sponsor.segments(videoId, s.sponsorCategories)
            // Still the same video in that tab (the user may have moved on while the answer came).
            if (segs.isNotEmpty() && sponsorVideo[tabId] == videoId) applySegments(tabId, videoId, segs)
        }
    }

    fun dislikes(tabId: Long, videoId: String) {
        if (!settings().returnDislikes) return
        scope.launch { runInPage(tabId, YouTubeExtras.dislikes(videoId, dislikes.dislikes(videoId))) }
    }

    fun titles(tabId: Long, videoIds: List<String>) {
        if (!settings().deArrow) return
        scope.launch {
            val titles = deArrow.titles(videoIds)
            if (titles.isNotEmpty()) runInPage(tabId, YouTubeExtras.deArrowTitles(titles))
        }
    }
}
