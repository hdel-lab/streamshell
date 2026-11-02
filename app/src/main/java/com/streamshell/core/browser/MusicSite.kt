package com.streamshell.core.browser

/**
 * YouTube Music in StreamShell (0.6.2). The page keeps its own top bar (search, account) and player; with "YouTube app
 * mode" on, its bottom tab bar is hidden because StreamShell's bar already holds Home, Explore, Library and search —
 * the same arrangement as on YouTube.
 */
object MusicSite {
    const val ORIGIN = "https://music.youtube.com"
    val origins = setOf(ORIGIN)
    const val STYLE_ID = "streamshell-compact-music"

    const val CSS = "ytmusic-pivot-bar-renderer{display:none!important}" +
        "ytmusic-app-layout{--ytmusic-pivot-bar-height:0px!important}" +
        "ytmusic-app-layout[is-bauhaus-sidenav-enabled] #content{padding-bottom:0!important}"

    val INSTALL: String = "(function(){if(document.getElementById('$STYLE_ID'))return 'ok';var s=document.createElement('style');s.id='$STYLE_ID';" +
        "s.textContent=" + org.json.JSONObject.quote(CSS) + ";(document.head||document.documentElement).appendChild(s);return 'ok';})()"

    fun isMusic(host: String?): Boolean = SiteApp.of(host) == SiteApp.YOUTUBE_MUSIC
}

/**
 * The page's own media session (YouTube and YouTube Music both register "next track" / "previous track" handlers for
 * the system's media controls). A document-start script keeps a copy of the handlers the page registers, so the car's
 * buttons can call exactly what the page offers; without a handler a page button is clicked, else nothing happens.
 * Read-only otherwise: title and artist come from the page's media-session metadata.
 */
object MediaSessionScripts {
    val origins = YouTubeSite.origins + MusicSite.origins

    const val CAPTURE = "(function(){if(window.__ssMS)return;window.__ssMS={};try{var ms=navigator.mediaSession;if(!ms||!ms.setActionHandler)return;" +
        "var orig=ms.setActionHandler.bind(ms);ms.setActionHandler=function(a,h){try{window.__ssMS[a]=h;}catch(e){}return orig(a,h);};}catch(e){}})();"

    const val NEXT = "nexttrack"
    const val PREVIOUS = "previoustrack"

    /** Runs [action] ("nexttrack" / "previoustrack"); answers "handler", "button" or "none". */
    fun action(action: String): String {
        val a = if (action == NEXT) NEXT else PREVIOUS
        val buttons = if (a == NEXT) ".next-button,.ytp-next-button,button.player-control-next" else ".previous-button,.ytp-prev-button,button.player-control-previous"
        return "(function(a){var h=window.__ssMS&&window.__ssMS[a];if(typeof h==='function'){try{h({action:a});return 'handler';}catch(e){}}" +
            "var b=document.querySelector('$buttons');if(b){b.click();return 'button';}" +
            "if(a==='previoustrack'){var v=document.querySelector('video,audio');if(v){v.currentTime=0;return 'start';}}return 'none';})('$a')"
    }

    /** "title\u0001artist" of what plays, from the page's media session, else "". */
    const val NOW_PLAYING = "(function(){try{var m=navigator.mediaSession&&navigator.mediaSession.metadata;if(m&&m.title)return m.title+'\\u0001'+(m.artist||'');}catch(e){}return '';})()"

    /** (title, artist) from [NOW_PLAYING]'s answer, or null. */
    fun parseNowPlaying(raw: String?): Pair<String, String>? {
        val s = raw?.let { r -> runCatching { org.json.JSONTokener(r).nextValue() as? String }.getOrNull() }.orEmpty()
        val title = s.substringBefore('\u0001').trim().take(MAX)
        val artist = s.substringAfter('\u0001', "").trim().take(MAX)
        return (title to artist).takeIf { title.isNotEmpty() }
    }

    private const val MAX = 120
}
