package com.streamshell.core.watch

import com.streamshell.core.library.Bookmark
import com.streamshell.core.library.HistoryEntry
import com.streamshell.core.model.AppSettings
import com.streamshell.core.model.SearchEngine
import com.streamshell.core.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup file (JSON) with bookmarks, history, Watch later, resume positions, watch time, imported channels and
 * settings. No passwords, cookies or sign-ins are in it (StreamShell never has them). Restoring replaces those
 * lists; anything malformed is skipped, and settings pass the normal sanitizer when saved.
 */
data class BackupContent(
    val bookmarks: List<Bookmark>,
    val history: List<HistoryEntry>,
    val watch: WatchData,
    val settings: AppSettings
)

object Backup {
    const val FORMAT = "streamshell-backup"
    const val VERSION = 2
    const val MAX_BYTES = 8_000_000

    /** Watch parts stored as JSON objects (the others are arrays). */
    private val OBJECT_KEYS = setOf("stats", "groups", "speeds", "chstats")

    fun encode(c: BackupContent, now: Long = System.currentTimeMillis()): String {
        val w = WatchSerializer.encode(c.watch)
        return JSONObject()
            .put("format", FORMAT).put("version", VERSION).put("created", now)
            .put("bookmarks", JSONArray(com.streamshell.core.library.LibrarySerializer.encodeBookmarks(c.bookmarks)))
            .put("history", JSONArray(com.streamshell.core.library.LibrarySerializer.encodeHistory(c.history)))
            .put("watch", JSONObject().also { o -> w.forEach { (k, v) -> o.put(k, if (k in OBJECT_KEYS) JSONObject(v) else JSONArray(v)) } })
            .put("settings", settingsToJson(c.settings))
            .toString(2)
    }

    /** Throws IllegalArgumentException with a user-readable reason when the file is not a StreamShell backup. */
    fun decode(text: String, base: AppSettings = AppSettings()): BackupContent {
        require(text.length <= MAX_BYTES) { "The file is too large." }
        val o = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("This is not a StreamShell backup file.") }
        require(o.optString("format") == FORMAT) { "This is not a StreamShell backup file." }
        require(o.optInt("version") in 1..VERSION) { "This backup was made by a newer StreamShell." }
        val w = o.optJSONObject("watch") ?: JSONObject()
        return BackupContent(
            bookmarks = com.streamshell.core.library.LibrarySerializer.decodeBookmarks(o.optJSONArray("bookmarks")?.toString()),
            history = com.streamshell.core.library.LibrarySerializer.decodeHistory(o.optJSONArray("history")?.toString()),
            watch = WatchSerializer.decode { k -> w.opt(k)?.toString() },
            settings = o.optJSONObject("settings")?.let { settingsFromJson(it, base) } ?: base
        )
    }

    fun settingsToJson(s: AppSettings): JSONObject = JSONObject()
        .put("themeMode", s.themeMode.name).put("dynamicColor", s.dynamicColor).put("searchEngine", s.searchEngine.name)
        .put("saveHistory", s.saveHistory).put("backgroundPlayback", s.backgroundPlayback).put("autoPip", s.autoPip)
        .put("youtubeCompact", s.youtubeCompact).put("defaultInstance", s.defaultInstance).put("sponsorBlock", s.sponsorBlock)
        .put("skipYouTubeAds", s.skipYouTubeAds).put("autoplay", s.autoplay).put("swipeToMinimize", s.swipeToMinimize)
        .put("pullToRefresh", s.pullToRefresh).put("playbackSpeed", s.playbackSpeed).put("doubleTapSeek", s.doubleTapSeek)
        .put("backgroundAudioOnly", s.backgroundAudioOnly).put("volumeBoost", s.volumeBoost).put("normalizeVolume", s.normalizeVolume)
        .put("breakReminderMinutes", s.breakReminderMinutes).put("resumePlayback", s.resumePlayback)
        .put("sponsorCategories", JSONArray(s.sponsorCategories.toList())).put("filteringEnabled", s.filteringEnabled)
        .put("customBlockedDomains", JSONArray(s.customBlockedDomains.toList())).put("customAllowedDomains", JSONArray(s.customAllowedDomains.toList()))
        .put("filterExemptSites", JSONArray(s.filterExemptSites.toList())).put("updateIntervalHours", s.updateIntervalHours)
        .put("siteBars", com.streamshell.core.browser.SiteBar.encode(s.siteBars))
        .put("returnDislikes", s.returnDislikes).put("seekSeconds", s.seekSeconds).put("qualityWifi", s.qualityWifi).put("qualityMobile", s.qualityMobile)
        .put("swipeControls", s.swipeControls).put("hiddenFeedParts", JSONArray(s.hiddenFeedParts.toList())).put("deArrow", s.deArrow)
        .put("sponsorMarkers", s.sponsorMarkers).put("cleanShareLinks", s.cleanShareLinks)
        .put("thirdPartyCookies", s.thirdPartyCookies).put("searchSuggestions", s.searchSuggestions)
        .put("feedPreviews", s.feedPreviews).put("shortsAsVideos", s.shortsAsVideos).put("hideWatched", s.hideWatched)
        .put("blockedChannels", JSONArray(s.blockedChannels.toList())).put("blockedKeywords", JSONArray(s.blockedKeywords.toList()))
        .put("leavePlayer", s.leavePlayer).put("captions", s.captions).put("captionsLanguage", s.captionsLanguage)
        .put("amoledBlack", s.amoledBlack).put("accent", s.accent).put("playerActionRow", s.playerActionRow)
        .put("carRailLeft", s.carRailLeft).put("carSource", s.carSource).put("yieldAudio", s.yieldAudio)

    fun settingsFromJson(o: JSONObject, d: AppSettings): AppSettings {
        fun b(k: String, def: Boolean) = if (o.has(k)) o.optBoolean(k, def) else def
        fun i(k: String, def: Int) = if (o.has(k)) o.optInt(k, def) else def
        fun set(k: String, def: Set<String>) = o.optJSONArray(k)?.let { a -> (0 until minOf(a.length(), 1000)).map { a.optString(it) }.filter { it.isNotBlank() }.toSet() } ?: def
        return d.copy(
            themeMode = runCatching { ThemeMode.valueOf(o.optString("themeMode")) }.getOrDefault(d.themeMode),
            dynamicColor = b("dynamicColor", d.dynamicColor),
            searchEngine = runCatching { SearchEngine.valueOf(o.optString("searchEngine")) }.getOrDefault(d.searchEngine),
            saveHistory = b("saveHistory", d.saveHistory), backgroundPlayback = b("backgroundPlayback", d.backgroundPlayback),
            autoPip = b("autoPip", d.autoPip), youtubeCompact = b("youtubeCompact", d.youtubeCompact),
            defaultInstance = o.optString("defaultInstance", d.defaultInstance), sponsorBlock = b("sponsorBlock", d.sponsorBlock),
            skipYouTubeAds = b("skipYouTubeAds", d.skipYouTubeAds), autoplay = b("autoplay", d.autoplay),
            swipeToMinimize = b("swipeToMinimize", d.swipeToMinimize), pullToRefresh = b("pullToRefresh", d.pullToRefresh),
            playbackSpeed = i("playbackSpeed", d.playbackSpeed), doubleTapSeek = b("doubleTapSeek", d.doubleTapSeek),
            backgroundAudioOnly = b("backgroundAudioOnly", d.backgroundAudioOnly), volumeBoost = i("volumeBoost", d.volumeBoost),
            normalizeVolume = b("normalizeVolume", d.normalizeVolume), breakReminderMinutes = i("breakReminderMinutes", d.breakReminderMinutes),
            resumePlayback = b("resumePlayback", d.resumePlayback),
            sponsorCategories = set("sponsorCategories", d.sponsorCategories), filteringEnabled = b("filteringEnabled", d.filteringEnabled),
            customBlockedDomains = set("customBlockedDomains", d.customBlockedDomains), customAllowedDomains = set("customAllowedDomains", d.customAllowedDomains),
            filterExemptSites = set("filterExemptSites", d.filterExemptSites), updateIntervalHours = i("updateIntervalHours", d.updateIntervalHours),
            siteBars = if (o.has("siteBars")) com.streamshell.core.browser.SiteBar.decode(o.optString("siteBars")) else d.siteBars,
            returnDislikes = b("returnDislikes", d.returnDislikes), seekSeconds = i("seekSeconds", d.seekSeconds),
            qualityWifi = o.optString("qualityWifi", d.qualityWifi), qualityMobile = o.optString("qualityMobile", d.qualityMobile),
            swipeControls = b("swipeControls", d.swipeControls), hiddenFeedParts = set("hiddenFeedParts", d.hiddenFeedParts),
            deArrow = b("deArrow", d.deArrow), sponsorMarkers = b("sponsorMarkers", d.sponsorMarkers), cleanShareLinks = b("cleanShareLinks", d.cleanShareLinks),
            thirdPartyCookies = b("thirdPartyCookies", d.thirdPartyCookies), searchSuggestions = b("searchSuggestions", d.searchSuggestions),
            feedPreviews = b("feedPreviews", d.feedPreviews), shortsAsVideos = b("shortsAsVideos", d.shortsAsVideos), hideWatched = b("hideWatched", d.hideWatched),
            blockedChannels = set("blockedChannels", d.blockedChannels), blockedKeywords = set("blockedKeywords", d.blockedKeywords),
            leavePlayer = o.optString("leavePlayer", d.leavePlayer), captions = o.optString("captions", d.captions),
            captionsLanguage = o.optString("captionsLanguage", d.captionsLanguage),
            amoledBlack = b("amoledBlack", d.amoledBlack), accent = o.optString("accent", d.accent), playerActionRow = b("playerActionRow", d.playerActionRow),
            carRailLeft = b("carRailLeft", d.carRailLeft),
            carSource = o.optString("carSource", d.carSource).takeIf { it == "youtube" || it == "music" } ?: d.carSource,
            yieldAudio = b("yieldAudio", d.yieldAudio)
        )
    }
}
