package com.streamshell.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Where text typed into the address bar goes when it is not a web address. */
enum class SearchEngine(val label: String, val queryUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q="),
    YOUTUBE("YouTube", "https://m.youtube.com/results?search_query=")
}

/** Persistent, user-visible settings. Contains no platform objects. */
data class AppSettings(
    // Dark is the primary look of the Liquid Glass design; Light and System stay available.
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Opt-in: neutrals follow the Android 12+ wallpaper palette; the StreamShell accent is kept. */
    val dynamicColor: Boolean = false,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    /** Visited pages are kept on this device (max. 500) when on. */
    val saveHistory: Boolean = true,
    /** Media keeps playing when you leave the app or lock the screen (with a notification and controls). */
    val backgroundPlayback: Boolean = true,
    /** Leaving the app while a video plays switches to picture-in-picture. */
    val autoPip: Boolean = true,
    /** Hide YouTube's own top and bottom bars on m.youtube.com; StreamShell shows its YouTube bar instead. */
    val youtubeCompact: Boolean = true,
    /** Third-party cookies in the browser (needed for Google sign-in across Google's domains). */
    val thirdPartyCookies: Boolean = true,
    /** Search suggestions while typing in the site search (the typed text goes to suggestqueries.google.com). */
    val searchSuggestions: Boolean = true,
    /** What StreamShell opens on start: "" = start page, or a SiteApp name (YOUTUBE, YOUTUBE_MUSIC) = that instance. */
    val defaultInstance: String = "",
    /** Jump over community-marked segments in YouTube videos (SponsorBlock). */
    val sponsorBlock: Boolean = true,
    /** Mute, fast-forward and auto-press "Skip" on YouTube video ads; hide ad cards in the feed. */
    val skipYouTubeAds: Boolean = true,
    /** Start YouTube videos by themselves when their page opens (and right after an ad). */
    val autoplay: Boolean = true,
    /** Swipe down on a playing YouTube video to shrink it into the mini player. */
    val swipeToMinimize: Boolean = true,
    /** Pull down at the top of a page to reload it. */
    val pullToRefresh: Boolean = true,
    /** Playback speed in percent (50…200) for videos; applied to every video until changed. */
    val playbackSpeed: Int = 100,
    /** Double-tap the left/right half of a video to jump 10 s (not on YouTube, which has its own). */
    val doubleTapSeek: Boolean = true,
    /** In the background, ask YouTube for its lowest video quality (sound stays), saving data and battery. */
    val backgroundAudioOnly: Boolean = false,
    /** Volume boost in percent (100 = off, 150, 200, 300); YouTube only. */
    val volumeBoost: Int = 100,
    /** Even out loud and quiet parts (dynamic compression); YouTube only. */
    val normalizeVolume: Boolean = false,
    /** Remind me to take a break after this many minutes of continuous watching; 0 = off. */
    val breakReminderMinutes: Int = 0,
    /** Ask to continue a video where you stopped (and remember positions). */
    val resumePlayback: Boolean = true,
    val sponsorCategories: Set<String> = com.streamshell.core.sponsor.SponsorCategory.defaults,
    val filteringEnabled: Boolean = true,
    /** Keep host-level diagnostics (hosts only, in memory). Off = only anonymous counters. */
    val detailedLogging: Boolean = true,
    /** Domains the user always blocks / always allows (allow wins; see LayeredFilter). */
    val customBlockedDomains: Set<String> = emptySet(),
    val customAllowedDomains: Set<String> = emptySet(),
    /** Sites (page host and its sub-domains) on which nothing is blocked. */
    val filterExemptSites: Set<String> = emptySet(),
    /** Automatic filter-list refresh every N hours; 0 = manual only. */
    val updateIntervalHours: Int = 24,
    /** Bottom bar per site app (SiteApp name → item keys in order, see SiteBar); missing = default bar. */
    val siteBars: Map<String, List<String>> = emptyMap(),
    /** Show dislike counts on YouTube videos (Return YouTube Dislike, returnyoutubedislikeapi.com). */
    val returnDislikes: Boolean = false,
    /** Length of the jump of the ±buttons (notification, headset, playback dialog, double-tap): 5, 10, 15 or 30 s. */
    val seekSeconds: Int = 10,
    /** YouTube video quality to ask for on Wi-Fi / on mobile data (see VideoQuality); "auto" = YouTube decides. */
    val qualityWifi: String = "auto",
    val qualityMobile: String = "auto",
    /** Fullscreen: swipe up/down on the left half for brightness, on the right half for volume. */
    val swipeControls: Boolean = true,
    /** Parts of YouTube's feeds to hide (see FeedFilter: "shorts", "posts", "playables"). */
    val hiddenFeedParts: Set<String> = emptySet(),
    /** Community titles instead of clickbait titles (DeArrow, sponsor.ajay.app). Opt-in. */
    val deArrow: Boolean = false,
    /** SponsorBlock segments drawn as coloured marks on YouTube's timeline. */
    val sponsorMarkers: Boolean = true,
    /** Shared YouTube links lose their tracking parameters (si=, feature=, pp=, utm_…). */
    val cleanShareLinks: Boolean = true,
    /** Ad and jump diagnostics: page scripts log what the player does (kept in memory only, for sending to the developer). */
    val diagnostics: Boolean = false,
    /** With [diagnostics]: trace the whole app (touches, actions, page loads, scripts, playback quality, jank, settings). */
    val traceEverything: Boolean = false,
    /** With [diagnostics]: a small log button on every screen. */
    val logButton: Boolean = true,
    /** Muted video previews in YouTube's feed. Off: the feed loads no video streams (thumbnails only, no accidental opens). */
    val feedPreviews: Boolean = false,
    /** Open Shorts links as normal videos (the normal player, resume, SponsorBlock). */
    val shortsAsVideos: Boolean = false,
    /** Hide feed tiles of videos already watched (history and finished videos). */
    val hideWatched: Boolean = false,
    /** Feed tiles of these channels are hidden (names, compared without case). */
    val blockedChannels: Set<String> = emptySet(),
    /** Feed tiles whose title contains one of these words are hidden (without case). */
    val blockedKeywords: Set<String> = emptySet(),
    /** Back from the video player: "mini" = mini player, "close" = close the video. */
    val leavePlayer: String = "mini",
    /** YouTube captions for new videos: "default" = YouTube decides, "on", "off". */
    val captions: String = "default",
    /** Caption language (ISO code, e.g. "en", "de", "bs"); "" = YouTube's choice. */
    val captionsLanguage: String = "",
    /** Pure black backgrounds in the dark theme (OLED screens). */
    val amoledBlack: Boolean = false,
    /** Accent colour (see AccentColors). */
    val accent: String = "blue",
    /** On an open YouTube video the bottom bar becomes the sliding player bar (sections and player actions in one row). */
    val playerActionRow: Boolean = true,
    /** Android Auto: the control rail at the left edge of the car screen (false = right edge). */
    val carRailLeft: Boolean = true,
    /** Android Auto: what opens first in the car, "youtube" or "music" (switchable on the car screen's rail). */
    val carSource: String = "youtube",
    /** Pause for phone/internet calls and lower the volume while another app's navigation or radar voice speaks. */
    val yieldAudio: Boolean = true
)

/** Accent colours offered in Settings (key → ARGB). */
object AccentColors {
    val OPTIONS: List<Pair<String, Long>> = listOf(
        "blue" to 0xFF4C8DFF,
        "red" to 0xFFFF4E45,
        "green" to 0xFF2ECC71,
        "purple" to 0xFFA06BFF,
        "orange" to 0xFFFF9F2E,
        "pink" to 0xFFFF5FA2
    )
    fun valid(k: String) = OPTIONS.any { it.first == k }
}

/** YouTube's quality names, as its player API takes them. */
object VideoQuality {
    val OPTIONS = listOf("auto" to "Auto", "hd2160" to "2160p", "hd1440" to "1440p", "hd1080" to "1080p", "hd720" to "720p", "large" to "480p", "medium" to "360p", "small" to "240p")
    fun valid(q: String) = OPTIONS.any { it.first == q }
}

/** Full-screen panels above the browser. */
enum class Overlay(val route: String?) {
    None(null),
    Tabs("tabs"),
    Bookmarks("bookmarks"),
    History("history"),
    Settings("settings"),

    /** AdBlock & Privacy; reached from Settings or the shield, closing it returns to where it came from. */
    Privacy("privacy"),
    WatchLater("watch-later"),
    Subscriptions("subscriptions"),
    WatchTime("watch-time"),

    /** StreamShell's own library: playlists, Watch later, subscriptions, watch time, history. */
    Library("library"),
    Diagnostics("diagnostics");

    companion object {
        fun fromRoute(route: String?): Overlay = entries.firstOrNull { it.route != null && it.route == route } ?: None
    }
}
