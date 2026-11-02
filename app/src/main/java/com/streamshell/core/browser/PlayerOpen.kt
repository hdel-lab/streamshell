package com.streamshell.core.browser

/**
 * How a video is opened in the one player tab, and how the in-page switch is checked afterwards (0.6.0, after the log of
 * 2026-11-01 16:11: a video tapped 1.5 s after the prepared player started loading was switched in a page that was not
 * ready, and never played).
 */
object PlayerOpen {
    enum class Mode {
        /** Same page, new address (history + popstate): no new page load. */
        IN_PAGE,

        /** The player page is still loading (e.g. the prepared player): the video address is loaded directly. */
        FULL_LOAD_STILL_LOADING,

        /** No usable player page yet: the video address is loaded. */
        FULL_LOAD
    }

    fun mode(hasPlayerPage: Boolean, pageLoading: Boolean): Mode = when {
        !hasPlayerPage -> Mode.FULL_LOAD
        pageLoading -> Mode.FULL_LOAD_STILL_LOADING
        else -> Mode.IN_PAGE
    }

    /** Page script: "videoId|playerState" of YouTube's player ("" / -9 when unknown). */
    const val CHECK = "(function(){try{var mp=document.getElementById('movie_player');if(!mp||!mp.getVideoData)return '|-9';" +
        "var id=(mp.getVideoData()||{}).video_id||'';var s=mp.getPlayerState?mp.getPlayerState():-9;return id+'|'+s;}catch(e){return '|-9';}})()"

    data class Check(val videoId: String, val state: Int)

    fun parse(raw: String?): Check {
        val s = raw?.trim()?.trim('"').orEmpty()
        val i = s.lastIndexOf('|')
        if (i < 0) return Check("", UNKNOWN)
        return Check(s.substring(0, i), s.substring(i + 1).toIntOrNull() ?: UNKNOWN)
    }

    /**
     * True when the in-page switch worked: the player carries the new video and, with autoplay on, has started
     * (playing 1, paused 2 by the user or a script, buffering 3; an ad also shows as one of these). Not started (-1) or
     * only cued (5) with autoplay on means the page did not take the switch → load the address.
     */
    fun switched(check: Check, target: String, autoplay: Boolean): Boolean {
        val started = check.state !in setOf(UNSTARTED, CUED, UNKNOWN)
        return check.videoId == target && (!autoplay || started)
    }

    fun stateName(state: Int): String = when (state) {
        UNSTARTED -> "not started"
        0 -> "ended"
        1 -> "playing"
        2 -> "paused"
        3 -> "buffering"
        CUED -> "cued"
        else -> "unknown"
    }

    const val UNSTARTED = -1
    const val CUED = 5
    const val UNKNOWN = -9
}
