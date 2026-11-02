package com.streamshell.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.streamshell.core.model.AppSettings
import com.streamshell.core.model.SearchEngine
import com.streamshell.core.model.ThemeMode
import com.streamshell.core.privacy.DomainInput
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
    suspend fun reset()
}

/** Keeps stored values sane even if the store was edited or corrupted. */
object SettingsSanitizer {
    fun sanitize(s: AppSettings): AppSettings = s.copy(
        // Only well-formed bare domains survive; the store may have been edited or corrupted.
        customBlockedDomains = s.customBlockedDomains.mapNotNull { DomainInput.normalize(it) }.take(MAX_CUSTOM).toSet(),
        customAllowedDomains = s.customAllowedDomains.mapNotNull { DomainInput.normalize(it) }.take(MAX_CUSTOM).toSet(),
        filterExemptSites = s.filterExemptSites.mapNotNull { DomainInput.normalize(it) }.take(MAX_CUSTOM).toSet(),
        updateIntervalHours = if (s.updateIntervalHours in ALLOWED_INTERVALS) s.updateIntervalHours else 24,
        defaultInstance = s.defaultInstance.takeIf { n -> n.isEmpty() || com.streamshell.core.browser.SiteApp.entries.any { it.name == n } } ?: "",
        sponsorCategories = s.sponsorCategories.filter { com.streamshell.core.sponsor.SponsorCategory.of(it) != null }.toSet(),
        playbackSpeed = if (s.playbackSpeed in SPEEDS) s.playbackSpeed else 100,
        volumeBoost = if (s.volumeBoost in BOOSTS) s.volumeBoost else 100,
        breakReminderMinutes = if (s.breakReminderMinutes in REMINDERS) s.breakReminderMinutes else 0,
        siteBars = com.streamshell.core.browser.SiteBar.sanitize(s.siteBars),
        seekSeconds = if (s.seekSeconds in SEEKS) s.seekSeconds else 10,
        qualityWifi = s.qualityWifi.takeIf { com.streamshell.core.model.VideoQuality.valid(it) } ?: "auto",
        qualityMobile = s.qualityMobile.takeIf { com.streamshell.core.model.VideoQuality.valid(it) } ?: "auto",
        hiddenFeedParts = s.hiddenFeedParts.filter { it in com.streamshell.core.browser.FeedFilter.PARTS.map { p -> p.first } }.toSet(),
        blockedChannels = cleanWords(s.blockedChannels),
        blockedKeywords = cleanWords(s.blockedKeywords),
        leavePlayer = s.leavePlayer.takeIf { it == "mini" || it == "close" } ?: "mini",
        captions = s.captions.takeIf { it in CAPTIONS } ?: "default",
        captionsLanguage = s.captionsLanguage.trim().lowercase().takeIf { Regex("[a-z]{2,3}(-[a-z0-9]{2,8})?").matches(it) } ?: "",
        accent = s.accent.takeIf { com.streamshell.core.model.AccentColors.valid(it) } ?: "blue"
    )

    /** Feed-filter words: trimmed, single spaces, 1–60 characters, at most [MAX_WORDS]. */
    fun cleanWords(words: Set<String>): Set<String> = words.mapNotNull { w ->
        w.replace(Regex("\\s+"), " ").trim().take(60).takeIf { it.isNotEmpty() }
    }.distinctBy { it.lowercase() }.take(MAX_WORDS).toSet()

    val CAPTIONS = listOf("default", "on", "off")
    const val MAX_WORDS = 300

    val SPEEDS = listOf(50, 75, 100, 125, 150, 175, 200)
    val BOOSTS = listOf(100, 150, 200, 300)
    val REMINDERS = listOf(0, 30, 60, 90, 120)
    val SEEKS = listOf(5, 10, 15, 30)

    const val MAX_CUSTOM = 1000
    val ALLOWED_INTERVALS = setOf(0, 12, 24, 72, 168)
}

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val engine = stringPreferencesKey("search_engine")
        val saveHistory = booleanPreferencesKey("save_history")
        val background = booleanPreferencesKey("background_playback")
        val autoPip = booleanPreferencesKey("auto_pip")
        val ytCompact = booleanPreferencesKey("youtube_compact")
        val thirdParty = booleanPreferencesKey("third_party_cookies")
        val suggest = booleanPreferencesKey("search_suggestions")
        val defaultInstance = stringPreferencesKey("default_instance")
        val sponsor = booleanPreferencesKey("sponsorblock")
        val skipAds = booleanPreferencesKey("skip_youtube_ads")
        val autoplay = booleanPreferencesKey("youtube_autoplay")
        val swipeMin = booleanPreferencesKey("swipe_to_minimize")
        val pullRefresh = booleanPreferencesKey("pull_to_refresh")
        val speed = intPreferencesKey("playback_speed")
        val doubleTap = booleanPreferencesKey("double_tap_seek")
        val audioOnly = booleanPreferencesKey("background_audio_only")
        val boost = intPreferencesKey("volume_boost")
        val normalize = booleanPreferencesKey("normalize_volume")
        val breakMin = intPreferencesKey("break_reminder_minutes")
        val resume = booleanPreferencesKey("resume_playback")
        val sponsorCats = stringSetPreferencesKey("sponsorblock_categories")
        val filtering = booleanPreferencesKey("filtering_enabled")
        val logging = booleanPreferencesKey("detailed_logging")
        val customBlocked = stringSetPreferencesKey("custom_blocked_domains")
        val customAllowed = stringSetPreferencesKey("custom_allowed_domains")
        val exempt = stringSetPreferencesKey("filter_exempt_sites")
        val interval = intPreferencesKey("update_interval_hours")
        val siteBars = stringPreferencesKey("site_bars")
        val dislikes = booleanPreferencesKey("return_dislikes")
        val seek = intPreferencesKey("seek_seconds")
        val qWifi = stringPreferencesKey("quality_wifi")
        val qMobile = stringPreferencesKey("quality_mobile")
        val swipeCtl = booleanPreferencesKey("swipe_controls")
        val feedHide = stringSetPreferencesKey("hidden_feed_parts")
        val deArrow = booleanPreferencesKey("dearrow")
        val markers = booleanPreferencesKey("sponsor_markers")
        val cleanShare = booleanPreferencesKey("clean_share_links")
        val diag = booleanPreferencesKey("diagnostics")
        val traceAll = booleanPreferencesKey("trace_everything")
        val logButton = booleanPreferencesKey("log_button")
        val previews = booleanPreferencesKey("feed_previews")
        val shorts = booleanPreferencesKey("shorts_as_videos")
        val hideWatched = booleanPreferencesKey("hide_watched")
        val blockedCh = stringSetPreferencesKey("blocked_channels")
        val blockedKw = stringSetPreferencesKey("blocked_keywords")
        val leave = stringPreferencesKey("leave_player")
        val captions = stringPreferencesKey("captions")
        val captionsLang = stringPreferencesKey("captions_language")
        val amoled = booleanPreferencesKey("amoled_black")
        val accent = stringPreferencesKey("accent")
        val actionRow = booleanPreferencesKey("player_action_row")
        val carRailLeft = booleanPreferencesKey("car_rail_left")
        val carSource = stringPreferencesKey("car_source")
        val yieldAudio = booleanPreferencesKey("yield_audio")
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return SettingsSanitizer.sanitize(
            AppSettings(
                themeMode = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
                dynamicColor = this[Keys.dynamic] ?: d.dynamicColor,
                searchEngine = this[Keys.engine]?.let { runCatching { SearchEngine.valueOf(it) }.getOrNull() } ?: d.searchEngine,
                saveHistory = this[Keys.saveHistory] ?: d.saveHistory,
                backgroundPlayback = this[Keys.background] ?: d.backgroundPlayback,
                autoPip = this[Keys.autoPip] ?: d.autoPip,
                youtubeCompact = this[Keys.ytCompact] ?: d.youtubeCompact,
                thirdPartyCookies = this[Keys.thirdParty] ?: d.thirdPartyCookies,
                searchSuggestions = this[Keys.suggest] ?: d.searchSuggestions,
                defaultInstance = this[Keys.defaultInstance] ?: d.defaultInstance,
                sponsorBlock = this[Keys.sponsor] ?: d.sponsorBlock,
                skipYouTubeAds = this[Keys.skipAds] ?: d.skipYouTubeAds,
                autoplay = this[Keys.autoplay] ?: d.autoplay,
                swipeToMinimize = this[Keys.swipeMin] ?: d.swipeToMinimize,
                pullToRefresh = this[Keys.pullRefresh] ?: d.pullToRefresh,
                playbackSpeed = this[Keys.speed] ?: d.playbackSpeed,
                doubleTapSeek = this[Keys.doubleTap] ?: d.doubleTapSeek,
                backgroundAudioOnly = this[Keys.audioOnly] ?: d.backgroundAudioOnly,
                volumeBoost = this[Keys.boost] ?: d.volumeBoost,
                normalizeVolume = this[Keys.normalize] ?: d.normalizeVolume,
                breakReminderMinutes = this[Keys.breakMin] ?: d.breakReminderMinutes,
                resumePlayback = this[Keys.resume] ?: d.resumePlayback,
                sponsorCategories = this[Keys.sponsorCats] ?: d.sponsorCategories,
                filteringEnabled = this[Keys.filtering] ?: d.filteringEnabled,
                detailedLogging = this[Keys.logging] ?: d.detailedLogging,
                customBlockedDomains = this[Keys.customBlocked] ?: d.customBlockedDomains,
                customAllowedDomains = this[Keys.customAllowed] ?: d.customAllowedDomains,
                filterExemptSites = this[Keys.exempt] ?: d.filterExemptSites,
                updateIntervalHours = this[Keys.interval] ?: d.updateIntervalHours,
                siteBars = com.streamshell.core.browser.SiteBar.decode(this[Keys.siteBars]),
                returnDislikes = this[Keys.dislikes] ?: d.returnDislikes,
                seekSeconds = this[Keys.seek] ?: d.seekSeconds,
                qualityWifi = this[Keys.qWifi] ?: d.qualityWifi,
                qualityMobile = this[Keys.qMobile] ?: d.qualityMobile,
                swipeControls = this[Keys.swipeCtl] ?: d.swipeControls,
                hiddenFeedParts = this[Keys.feedHide] ?: d.hiddenFeedParts,
                deArrow = this[Keys.deArrow] ?: d.deArrow,
                sponsorMarkers = this[Keys.markers] ?: d.sponsorMarkers,
                cleanShareLinks = this[Keys.cleanShare] ?: d.cleanShareLinks,
                diagnostics = this[Keys.diag] ?: d.diagnostics,
                traceEverything = this[Keys.traceAll] ?: d.traceEverything,
                logButton = this[Keys.logButton] ?: d.logButton,
                feedPreviews = this[Keys.previews] ?: d.feedPreviews,
                shortsAsVideos = this[Keys.shorts] ?: d.shortsAsVideos,
                hideWatched = this[Keys.hideWatched] ?: d.hideWatched,
                blockedChannels = this[Keys.blockedCh] ?: d.blockedChannels,
                blockedKeywords = this[Keys.blockedKw] ?: d.blockedKeywords,
                leavePlayer = this[Keys.leave] ?: d.leavePlayer,
                captions = this[Keys.captions] ?: d.captions,
                captionsLanguage = this[Keys.captionsLang] ?: d.captionsLanguage,
                amoledBlack = this[Keys.amoled] ?: d.amoledBlack,
                accent = this[Keys.accent] ?: d.accent,
                playerActionRow = this[Keys.actionRow] ?: d.playerActionRow,
                carRailLeft = this[Keys.carRailLeft] ?: d.carRailLeft,
                carSource = (this[Keys.carSource] ?: d.carSource).takeIf { it == "youtube" || it == "music" } ?: d.carSource,
                yieldAudio = this[Keys.yieldAudio] ?: d.yieldAudio
            )
        )
    }

    override val settings: Flow<AppSettings> = store.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { it.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val n = SettingsSanitizer.sanitize(transform(p.toSettings()))
            p[Keys.theme] = n.themeMode.name
            p[Keys.dynamic] = n.dynamicColor
            p[Keys.engine] = n.searchEngine.name
            p[Keys.saveHistory] = n.saveHistory
            p[Keys.background] = n.backgroundPlayback
            p[Keys.autoPip] = n.autoPip
            p[Keys.ytCompact] = n.youtubeCompact
            p[Keys.thirdParty] = n.thirdPartyCookies
            p[Keys.suggest] = n.searchSuggestions
            p[Keys.defaultInstance] = n.defaultInstance
            p[Keys.sponsor] = n.sponsorBlock
            p[Keys.skipAds] = n.skipYouTubeAds
            p[Keys.autoplay] = n.autoplay
            p[Keys.swipeMin] = n.swipeToMinimize
            p[Keys.pullRefresh] = n.pullToRefresh
            p[Keys.speed] = n.playbackSpeed
            p[Keys.doubleTap] = n.doubleTapSeek
            p[Keys.audioOnly] = n.backgroundAudioOnly
            p[Keys.boost] = n.volumeBoost
            p[Keys.normalize] = n.normalizeVolume
            p[Keys.breakMin] = n.breakReminderMinutes
            p[Keys.resume] = n.resumePlayback
            p[Keys.sponsorCats] = n.sponsorCategories
            p[Keys.filtering] = n.filteringEnabled
            p[Keys.logging] = n.detailedLogging
            p[Keys.customBlocked] = n.customBlockedDomains
            p[Keys.customAllowed] = n.customAllowedDomains
            p[Keys.exempt] = n.filterExemptSites
            p[Keys.interval] = n.updateIntervalHours
            p[Keys.siteBars] = com.streamshell.core.browser.SiteBar.encode(n.siteBars)
            p[Keys.dislikes] = n.returnDislikes
            p[Keys.seek] = n.seekSeconds
            p[Keys.qWifi] = n.qualityWifi
            p[Keys.qMobile] = n.qualityMobile
            p[Keys.swipeCtl] = n.swipeControls
            p[Keys.feedHide] = n.hiddenFeedParts
            p[Keys.deArrow] = n.deArrow
            p[Keys.markers] = n.sponsorMarkers
            p[Keys.cleanShare] = n.cleanShareLinks
            p[Keys.diag] = n.diagnostics
            p[Keys.traceAll] = n.traceEverything
            p[Keys.logButton] = n.logButton
            p[Keys.previews] = n.feedPreviews
            p[Keys.shorts] = n.shortsAsVideos
            p[Keys.hideWatched] = n.hideWatched
            p[Keys.blockedCh] = n.blockedChannels
            p[Keys.blockedKw] = n.blockedKeywords
            p[Keys.leave] = n.leavePlayer
            p[Keys.captions] = n.captions
            p[Keys.captionsLang] = n.captionsLanguage
            p[Keys.amoled] = n.amoledBlack
            p[Keys.accent] = n.accent
            p[Keys.actionRow] = n.playerActionRow
            p[Keys.carRailLeft] = n.carRailLeft
            p[Keys.carSource] = n.carSource
            p[Keys.yieldAudio] = n.yieldAudio
        }
    }

    override suspend fun reset() {
        store.edit { it.clear() }
    }
}
