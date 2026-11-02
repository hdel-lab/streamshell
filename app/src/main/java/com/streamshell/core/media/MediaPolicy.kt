package com.streamshell.core.media

/** What the page's HTML5 media is doing, as reported by [MediaScripts.PROBE]. */
enum class MediaState { NONE, PAUSED, PLAYING }

/**
 * Generic HTML5 media control, the same for every site: find <video>/<audio> elements in the top
 * document and call the standard play()/pause(). Nothing else on the page is read or changed.
 * Limitation: media inside cross-origin iframes (e.g. embedded players on other sites) is not reachable.
 */
object MediaScripts {
    /**
     * Reports "state|left|top|width|height|devicePixelRatio" of the largest playing video (else the largest video),
     * or just the state when there is no video element. Position is read-only (getBoundingClientRect).
     */
    const val PROBE = "(function(){var all=document.querySelectorAll('video,audio');var st=all.length?'paused':'none';" +
        "var best=null,area=0,bestPlaying=false;" +
        "for(var i=0;i<all.length;i++){var v=all[i];var p=!v.paused&&!v.ended&&v.readyState>2;if(p)st='playing';" +
        "if(v.tagName==='VIDEO'){var r=v.getBoundingClientRect();var a=r.width*r.height;" +
        "if((p&&!bestPlaying)||(p===bestPlaying&&a>area)){best=r;area=a;bestPlaying=p;}}}" +
        "return best?[st,best.left,best.top,best.width,best.height,window.devicePixelRatio||1].join('|'):st;})()"

    /** Jumps the playing (or first) video/audio by %d seconds, kept inside the media. */
    // On YouTube the jump goes through YouTube's own player (seekTo): setting the <video>'s time behind its back made the
    // player pull the picture back and forth before it settled.
    fun seekBy(seconds: Int) = "(function(d){try{if(window.__ssDiagLog)window.__ssDiagLog('app: jump '+d+' s');}catch(e){}var mp=document.getElementById('movie_player');" +
        "if(mp&&mp.seekTo&&mp.getCurrentTime&&mp.getDuration){var c=mp.getCurrentTime(),du=mp.getDuration();if(isFinite(c)&&du>0){mp.seekTo(Math.max(0,Math.min(du-0.5,c+d)),true);return 'yt';}}" +
        "var m=document.querySelectorAll('video,audio');var v=null;" +
        "for(var i=0;i<m.length;i++){if(!m[i].paused){v=m[i];break;}}if(!v&&m.length)v=m[0];" +
        "if(v&&isFinite(v.duration)&&v.duration>0){v.currentTime=Math.max(0,Math.min(v.duration-0.5,v.currentTime+d));}return 'ok';})(" + seconds + ")"

    /**
     * [on] = another app's navigation / radar voice speaks: the page's media plays at a quarter of its volume; off = the
     * volume it had before. Each element remembers its own volume (window.__ssDucked marks the state for new elements).
     */
    fun duck(on: Boolean) = "(function(on){window.__ssDucked=on;var m=document.querySelectorAll('video,audio');for(var i=0;i<m.length;i++){var v=m[i];try{" +
        "if(on){if(v.__ssVol==null){v.__ssVol=v.volume;v.volume=Math.min(v.volume,0.25);}}else if(v.__ssVol!=null){v.volume=v.__ssVol;v.__ssVol=null;}}catch(x){}}return 'ok';})(" + on + ")"

    /**
     * [silent] = this page may not play media now (another tab is the one playing, e.g. the mini player while you scroll
     * the YouTube feed): its media is paused and anything that starts by itself (feed previews) is paused at once.
     * Installed once per page; switching back allows playing again.
     */
    fun silent(on: Boolean) = "(function(on){window.__ssSilent=on;if(!window.__ssSilentHook){window.__ssSilentHook=true;" +
        "document.addEventListener('playing',function(e){var m=e.target;if(window.__ssSilent&&m&&m.pause){try{m.pause();}catch(x){}}},true);}" +
        "if(on){var m=document.querySelectorAll('video,audio');for(var i=0;i<m.length;i++){try{m[i].pause();}catch(x){}}}return 'ok';})(" + on + ")"

    /** Plays the main video again if it is paused mid-way (not ended), through YouTube's player when there is one. */
    const val RESUME = "(function(){var m=document.querySelectorAll('video');var v=m.length?m[0]:null;for(var i=0;i<m.length;i++){if(m[i].currentTime>0){v=m[i];break;}}" +
        "if(!v||!v.paused||v.ended)return 'no';var mp=document.getElementById('movie_player');try{if(mp&&mp.playVideo)mp.playVideo();}catch(e){}" +
        "if(v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}return 'ok';})()"

    // Play/pause go through YouTube's player when the page has one (like its own buttons), so its controls agree;
    // calling only the <video> could leave YouTube's player showing the wrong state.
    const val PAUSE = "(function(){var mp=document.getElementById('movie_player');try{if(mp&&mp.pauseVideo)mp.pauseVideo();}catch(e){}" +
        "var m=document.querySelectorAll('video,audio');for(var i=0;i<m.length;i++){m[i].pause();}return 'ok';})()"
    const val PLAY = "(function(){var mp=document.getElementById('movie_player');try{if(mp&&mp.playVideo){mp.playVideo();}}catch(e){}" +
        "var m=document.querySelectorAll('video,audio');var v=null;" +
        "for(var i=0;i<m.length;i++){if(m[i].readyState>0){v=m[i];break;}}" +
        "if(v&&v.paused){var p=v.play();if(p&&p.catch){p.catch(function(){});}}return 'ok';})()"

    /** evaluateJavascript returns a JSON value, e.g. "\"playing\"". Anything unexpected means "no media". */
    fun parse(result: String?): MediaState = parseProbe(result).state

    /** Parses [PROBE]'s result; the video rectangle is converted from CSS pixels to view pixels. */
    fun parseProbe(result: String?): MediaProbe {
        val parts = result?.trim()?.trim('"')?.split('|') ?: return MediaProbe(MediaState.NONE, null)
        val state = when (parts[0]) {
            "playing" -> MediaState.PLAYING
            "paused" -> MediaState.PAUSED
            else -> MediaState.NONE
        }
        if (state == MediaState.NONE || parts.size != 6) return MediaProbe(state, null)
        val n = parts.drop(1).map { it.toFloatOrNull() ?: return MediaProbe(state, null) }
        val d = n[4].takeIf { it > 0f && it < 10f } ?: return MediaProbe(state, null)
        if (n[2] <= 0f || n[3] <= 0f) return MediaProbe(state, null)
        return MediaProbe(state, VideoRect(n[0] * d, n[1] * d, n[2] * d, n[3] * d))
    }
}

/** A video's position inside the web view, in view pixels. */
data class VideoRect(val left: Float, val top: Float, val width: Float, val height: Float)

data class MediaProbe(val state: MediaState, val video: VideoRect?)

/** Scale and offset that make the [video] area of a page fill a picture-in-picture window. */
data class PipCrop(val scale: Float, val translateX: Float, val translateY: Float)

/** What to do when the app leaves the screen (home button, other app, screen off). */
enum class LeaveAction {
    /** In picture-in-picture: the activity is visible, keep everything running. */
    KEEP_FOR_PIP,

    /** Background playback: keep the page "visible" to the site and the media playing, with a notification. */
    CONTINUE_IN_BACKGROUND,

    /** Pause media and the web view. */
    PAUSE
}

object MediaPolicy {
    fun onLeave(inPip: Boolean, backgroundEnabled: Boolean, media: MediaState): LeaveAction = when {
        inPip -> LeaveAction.KEEP_FOR_PIP
        backgroundEnabled && media == MediaState.PLAYING -> LeaveAction.CONTINUE_IN_BACKGROUND
        else -> LeaveAction.PAUSE
    }

    /** Auto picture-in-picture only while something is actually playing (or a video is fullscreen). */
    fun shouldAutoPip(autoPipEnabled: Boolean, supported: Boolean, fullscreenVideo: Boolean, media: MediaState): Boolean = autoPipEnabled && supported && (fullscreenVideo || media == MediaState.PLAYING)

    /** The media notification/service runs while media plays and background playback is on. */
    fun wantsPlaybackService(backgroundEnabled: Boolean, media: MediaState, serviceRunning: Boolean): Boolean = when {
        !backgroundEnabled -> false

        media == MediaState.PLAYING -> true

        // Paused from the notification: keep the controls so the user can resume.
        media == MediaState.PAUSED -> serviceRunning

        else -> false
    }

    /**
     * How to show only the video in PiP: the web view keeps its normal size ([viewW] x [viewH]) and is
     * scaled/moved so that [video] fills the PiP window ([boxW] x [boxH]). Null = show the whole page.
     */
    fun pipCrop(viewW: Int, viewH: Int, video: VideoRect?, boxW: Int, boxH: Int): PipCrop? {
        if (video == null || viewW <= 0 || viewH <= 0 || boxW <= 0 || boxH <= 0) return null
        val l = video.left.coerceIn(0f, viewW.toFloat())
        val t = video.top.coerceIn(0f, viewH.toFloat())
        val w = (minOf(video.left + video.width, viewW.toFloat()) - l)
        val h = (minOf(video.top + video.height, viewH.toFloat()) - t)
        // A video mostly off-screen or tiny (an ad thumbnail, a preview) is not worth zooming into.
        if (w < viewW * 0.25f || h < 48f) return null
        val s = minOf(boxW / w, boxH / h)
        return PipCrop(s, -l * s + (boxW - w * s) / 2f, -t * s + (boxH - h * s) / 2f)
    }

    /** Video aspect ratio for the PiP window, clamped to what Android accepts (1:2.39 .. 2.39:1). */
    fun pipAspect(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 16 to 9
        val r = width.toDouble() / height
        return when {
            r > 2.39 -> 239 to 100
            r < 1 / 2.39 -> 100 to 239
            else -> width to height
        }
    }
}

/** How a web page's media can be controlled from outside the page (notification, PiP buttons, audio focus). */
interface MediaControls {
    fun play()
    fun pause()

    /** Pause and end background playback. */
    fun stop()

    /** Jump forward/back in the playing media. */
    fun seekBy(seconds: Int) {}

    /** Next / previous (headset, lock screen): next track where the page has tracks, else a jump. */
    fun track(next: Boolean) {
        seekBy(if (next) MediaBridge.seekSeconds else -MediaBridge.seekSeconds)
    }

    /** Stop everything and close StreamShell (notification "Exit"). */
    fun exit() {
        stop()
    }
}

/** Process-wide link between the playback service and the browser that owns the media. */
object MediaBridge {
    @Volatile var controls: MediaControls? = null

    @Volatile var title: String = ""

    @Volatile var state: MediaState = MediaState.NONE

    /** Page of the playing media; used for the notification thumbnail (YouTube video picture). */
    @Volatile var pageUrl: String? = null

    /** Set by the activity: closes the app's task (notification "Exit"). */
    @Volatile var onExit: (() -> Unit)? = null

    /** Length of the ± jumps (Settings › Playback), for the notification and headset buttons. */
    @Volatile var seekSeconds: Int = PlaybackService.SEEK_SECONDS

    /** YouTube's still picture for the playing video, or null for other sites. */
    fun thumbnailUrl(): String? = com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(pageUrl)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

    /** Bumped on every change so the notification can refresh. */
    @Volatile var listener: (() -> Unit)? = null

    fun update(title: String, state: MediaState, pageUrl: String? = this.pageUrl) {
        val changed = title != this.title || state != this.state || pageUrl != this.pageUrl
        this.title = title
        this.state = state
        this.pageUrl = pageUrl
        if (changed) listener?.invoke()
    }
}
