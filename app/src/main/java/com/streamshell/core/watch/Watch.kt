package com.streamshell.core.watch

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.streamshell.core.sponsor.SponsorBlockApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/*
 * Everything StreamShell remembers about watching, kept only on this device:
 * where you stopped in a video (resume / "Continue watching"), "Watch later", watch time per day, and the
 * channel list imported from Google Takeout (with their public RSS feeds as a "Latest" list).
 * The rules are pure functions (unit-tested); the repository only stores their results.
 */

data class WatchPosition(val key: String, val url: String, val title: String, val position: Double, val duration: Double, val updatedAt: Long) {
    val progress: Float get() = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
}

data class WatchLaterItem(val url: String, val title: String, val addedAt: Long)

data class Channel(val id: String, val title: String) {
    val url: String get() = "https://m.youtube.com/channel/$id"
}

data class FeedVideo(val videoId: String, val title: String, val channel: String, val published: Long, val channelId: String = "") {
    val url: String get() = "https://m.youtube.com/watch?v=$videoId"
}

data class WatchData(
    val positions: List<WatchPosition> = emptyList(),
    val later: List<WatchLaterItem> = emptyList(),
    /** Seconds watched per day, key "yyyy-MM-dd" (local time). */
    val stats: Map<String, Long> = emptyMap(),
    val channels: List<Channel> = emptyList(),
    /** Channel groups: group name → channel ids (a channel is in at most one group; empty groups disappear). */
    val groups: Map<String, List<String>> = emptyMap(),
    /** Local playlists, in the user's order. */
    val playlists: List<Playlist> = emptyList(),
    /** Remembered playback speed per channel (channel name in lower case → percent). */
    val channelSpeeds: Map<String, Int> = emptyMap(),
    /** Seconds watched per channel (channel name as YouTube shows it). */
    val channelStats: Map<String, Long> = emptyMap()
)

data class Playlist(val name: String, val items: List<WatchLaterItem> = emptyList())

/** Pure rules for local playlists (unit-tested). Videos are kept once per list (same video key). */
object Playlists {
    const val MAX_LISTS = 50
    const val MAX_ITEMS = 500
    const val MAX_NAME = 40

    fun clean(name: String?): String? = name?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_NAME)?.takeIf { it.isNotEmpty() }

    /** A new empty list; an existing name (without case) or too many lists → unchanged. */
    fun create(lists: List<Playlist>, name: String): List<Playlist> {
        val n = clean(name) ?: return lists
        if (lists.size >= MAX_LISTS || lists.any { it.name.equals(n, ignoreCase = true) }) return lists
        return lists + Playlist(n)
    }

    fun rename(lists: List<Playlist>, from: String, to: String): List<Playlist> {
        val n = clean(to) ?: return lists
        if (lists.any { it.name.equals(n, ignoreCase = true) && it.name != from }) return lists
        return lists.map { if (it.name == from) it.copy(name = n) else it }
    }

    fun delete(lists: List<Playlist>, name: String): List<Playlist> = lists.filterNot { it.name == name }

    /** Adds the video at the end (creating the list when it does not exist yet); a video already in the list stays where it is. */
    fun add(lists: List<Playlist>, name: String, url: String, title: String, now: Long): List<Playlist> {
        if (!url.startsWith("https://")) return lists
        val withList = if (lists.none { it.name == name }) create(lists, name) else lists
        val target = clean(name) ?: return lists
        val key = WatchRules.keyOf(url)
        return withList.map { p ->
            if (!p.name.equals(target, ignoreCase = true) || p.items.any { WatchRules.keyOf(it.url) == key } || p.items.size >= MAX_ITEMS) {
                p
            } else {
                p.copy(items = p.items + WatchLaterItem(url, title.take(300), now))
            }
        }
    }

    fun remove(lists: List<Playlist>, name: String, index: Int): List<Playlist> = lists.map { p ->
        if (p.name == name && index in p.items.indices) p.copy(items = p.items.filterIndexed { i, _ -> i != index }) else p
    }

    fun move(lists: List<Playlist>, name: String, index: Int, delta: Int): List<Playlist> = lists.map { p ->
        val j = index + delta
        if (p.name != name || index !in p.items.indices || j !in p.items.indices) {
            p
        } else {
            p.copy(items = p.items.toMutableList().also { it.add(j, it.removeAt(index)) })
        }
    }

    fun contains(lists: List<Playlist>, name: String, url: String?): Boolean = url != null && lists.firstOrNull { it.name == name }?.items?.any { WatchRules.keyOf(it.url) == WatchRules.keyOf(url) } == true
}

/** Remembered speed per channel and watch time per channel (pure, unit-tested). */
object ChannelRules {
    const val MAX_SPEEDS = 300
    const val MAX_STATS = 200

    fun keyOf(channel: String): String? = channel.replace(Regex("\\s+"), " ").trim().lowercase().take(100).takeIf { it.isNotEmpty() }

    /** [percent] = null forgets the channel. */
    fun setSpeed(map: Map<String, Int>, channel: String, percent: Int?): Map<String, Int> {
        val k = keyOf(channel) ?: return map
        val without = map - k
        if (percent == null) return without
        return (mapOf(k to percent.coerceIn(50, 200)) + without).entries.take(MAX_SPEEDS).associate { it.key to it.value }
    }

    fun speedOf(map: Map<String, Int>, channel: String?): Int? = channel?.let(::keyOf)?.let { map[it] }

    fun addTime(stats: Map<String, Long>, channel: String?, seconds: Long): Map<String, Long> {
        val name = channel?.replace(Regex("\\s+"), " ")?.trim()?.take(100)?.takeIf { it.isNotEmpty() } ?: return stats
        if (seconds <= 0) return stats
        val next = stats + (name to ((stats[name] ?: 0L) + seconds))
        return next.entries.sortedByDescending { it.value }.take(MAX_STATS).associate { it.key to it.value }
    }

    fun top(stats: Map<String, Long>, n: Int = 5): List<Pair<String, Long>> = stats.entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }
}

/** Pure rules for channel groups (unit-tested). */
object ChannelGroups {
    const val MAX_GROUPS = 30
    const val MAX_NAME = 30

    fun clean(name: String?): String? = name?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_NAME)?.takeIf { it.isNotEmpty() }

    /** Puts [channelId] into [group] (null = no group); a channel leaves its old group; empty groups are removed. */
    fun assign(groups: Map<String, List<String>>, channelId: String, group: String?): Map<String, List<String>> {
        val g = clean(group)
        val without = groups.mapValues { (_, ids) -> ids.filterNot { it == channelId } }.filterValues { it.isNotEmpty() }
        if (g == null) return without
        if (g !in without && without.size >= MAX_GROUPS) return without
        return without + (g to ((without[g] ?: emptyList()) + channelId))
    }

    fun groupOf(groups: Map<String, List<String>>, channelId: String): String? = groups.entries.firstOrNull { channelId in it.value }?.key

    /** Feed filtered to a group (null = all). */
    fun filter(videos: List<FeedVideo>, groups: Map<String, List<String>>, group: String?): List<FeedVideo> {
        if (group == null) return videos
        val ids = groups[group]?.toSet() ?: return emptyList()
        return videos.filter { it.channelId in ids }
    }
}

object WatchRules {
    const val MAX_POSITIONS = 200
    const val MAX_LATER = 300
    const val MAX_CHANNELS = 2000
    const val STATS_DAYS = 30

    /** Shorter media (clips, previews, ads) is not remembered. */
    const val MIN_DURATION = 60.0
    const val MIN_RESUME = 10.0

    /** Within this many seconds of the end a video counts as finished. */
    const val END_MARGIN = 20.0

    /** One key per video: the YouTube ID, otherwise the address without its #fragment. */
    fun keyOf(url: String): String = SponsorBlockApi.videoIdOf(url)?.let { "yt:$it" } ?: url.substringBefore('#')

    fun thumbnailOf(url: String): String? = SponsorBlockApi.videoIdOf(url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

    /**
     * Whether positions are kept for [url] at all. On YouTube only for a real video page (an ID): the feed's muted tile
     * previews were saved under the home page's address and every start "continued" them (0.4.4 log: 83 s).
     */
    fun remembers(url: String): Boolean = !com.streamshell.core.browser.UrlHosts.isYouTube(com.streamshell.core.browser.UrlHosts.hostOf(url)) || SponsorBlockApi.videoIdOf(url) != null

    /** Saved positions without the ones [remembers] would not keep (once, for lists saved before 0.4.5). */
    fun withoutPageEntries(list: List<WatchPosition>): List<WatchPosition> = list.filter { remembers(it.url) }

    fun record(list: List<WatchPosition>, url: String, title: String, position: Double, duration: Double, now: Long): List<WatchPosition> {
        if (!url.startsWith("https://") || url.length > 2048 || !remembers(url)) return list
        if (!position.isFinite() || !duration.isFinite() || duration < MIN_DURATION) return list
        val key = keyOf(url)
        val rest = list.filterNot { it.key == key }
        // Finished (or barely started): nothing to continue.
        if (position >= duration - END_MARGIN) return rest
        if (position < MIN_RESUME) return list
        val keepTitle = title.ifBlank { list.firstOrNull { it.key == key }?.title.orEmpty() }.take(300)
        return (listOf(WatchPosition(key, url, keepTitle, position, duration, now)) + rest).take(MAX_POSITIONS)
    }

    /** A resume decision: where to continue (null = not at all) and why, for the diagnostics log. */
    data class Resume(val at: Double?, val reason: String)

    /** Whether to continue [url] when its player is at [current] seconds right after the start. */
    fun resumeDecision(list: List<WatchPosition>, url: String, current: Double): Resume {
        if (!remembers(url)) return Resume(null, "not a video page")
        if (current >= 5) return Resume(null, "already at ${current.toInt()} s")
        val p = list.firstOrNull { it.key == keyOf(url) } ?: return Resume(null, "no saved position")
        val at = resumeAt(list, url) ?: return Resume(null, "saved ${p.position.toInt()} s is too near the start or end")
        return Resume(at, "saved")
    }

    /** Where to continue [url], or null to start at the beginning. */
    fun resumeAt(list: List<WatchPosition>, url: String): Double? {
        if (!remembers(url)) return null
        val p = list.firstOrNull { it.key == keyOf(url) } ?: return null
        return p.position.takeIf { it >= MIN_RESUME && it < p.duration - END_MARGIN }
    }

    fun continueWatching(list: List<WatchPosition>, max: Int = 12): List<WatchPosition> = list.sortedByDescending { it.updatedAt }.take(max)

    fun toggleLater(list: List<WatchLaterItem>, url: String, title: String, now: Long): List<WatchLaterItem> {
        val key = keyOf(url)
        return if (list.any { keyOf(it.url) == key }) {
            list.filterNot { keyOf(it.url) == key }
        } else {
            (listOf(WatchLaterItem(url, title.take(300), now)) + list).take(MAX_LATER)
        }
    }

    fun isLater(list: List<WatchLaterItem>, url: String?): Boolean = url != null && list.any { keyOf(it.url) == keyOf(url) }

    fun addWatchTime(stats: Map<String, Long>, day: String, seconds: Long): Map<String, Long> {
        if (seconds <= 0) return stats
        val next = stats + (day to ((stats[day] ?: 0L) + seconds))
        return next.keys.sortedDescending().take(STATS_DAYS).associateWith { next.getValue(it) }
    }

    /** Last [days] days ending [today] (oldest first), missing days as 0. */
    fun lastDays(stats: Map<String, Long>, today: java.time.LocalDate, days: Int = 7): List<Pair<java.time.LocalDate, Long>> = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (stats[it.toString()] ?: 0L) }

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "$h h $m min" else "$m min"
    }

    fun formatClock(seconds: Double): String {
        val s = seconds.toLong().coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}

/**
 * Google Takeout "subscriptions.csv" (YouTube and YouTube Music > subscriptions). Columns are found by their
 * content (the header is translated in other languages): the channel ID is the "UC…" value, the title the last
 * column. Quoted fields with commas are handled.
 */
object TakeoutImport {
    private val channelId = Regex("^UC[A-Za-z0-9_-]{22}$")

    fun parse(csv: String): List<Channel> {
        val out = LinkedHashMap<String, Channel>()
        csv.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val cells = splitCsv(line)
            val id = cells.firstOrNull { channelId.matches(it.trim()) }?.trim() ?: cells.firstNotNullOfOrNull { c ->
                Regex("/channel/(UC[A-Za-z0-9_-]{22})").find(c)?.groupValues?.get(1)
            } ?: return@forEach
            val title = cells.lastOrNull()?.trim()?.takeIf { it.isNotEmpty() && !it.contains("youtube.com") && it != id } ?: id
            out.putIfAbsent(id, Channel(id, title.take(200)))
        }
        return out.values.take(WatchRules.MAX_CHANNELS).toList()
    }

    internal fun splitCsv(line: String): List<String> {
        val cells = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }

                ch == '"' -> quoted = !quoted

                ch == ',' && !quoted -> {
                    cells += sb.toString()
                    sb.clear()
                }

                else -> sb.append(ch)
            }
            i++
        }
        cells += sb.toString()
        return cells
    }
}

/**
 * NewPipe's subscriptions export (Settings › Content › Export subscriptions): JSON with
 * `subscriptions: [{ service_id: 0, url: "https://www.youtube.com/channel/UC…", name: "…" }]`. Only YouTube channels
 * (service 0) with a channel ID are taken.
 */
object NewPipeImport {
    private val id = Regex("/channel/(UC[A-Za-z0-9_-]{22})")

    fun parse(json: String): List<Channel> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val subs = root.optJSONArray("subscriptions") ?: return emptyList()
        val out = LinkedHashMap<String, Channel>()
        for (i in 0 until minOf(subs.length(), WatchRules.MAX_CHANNELS * 2)) {
            val o = subs.optJSONObject(i) ?: continue
            if (o.optInt("service_id", 0) != 0) continue
            val cid = id.find(o.optString("url"))?.groupValues?.get(1) ?: continue
            out.putIfAbsent(cid, Channel(cid, o.optString("name").trim().take(200).ifBlank { cid }))
        }
        return out.values.take(WatchRules.MAX_CHANNELS).toList()
    }
}

/** A subscriptions file of either kind: NewPipe JSON (starts with "{") or Google Takeout CSV. */
object SubscriptionImport {
    fun parse(text: String): List<Channel> = if (text.trimStart().startsWith("{")) NewPipeImport.parse(text) else TakeoutImport.parse(text)
}

/** YouTube's public channel feed (https://www.youtube.com/feeds/videos.xml?channel_id=…), parsed without a DOM. */
object ChannelFeed {
    fun urlOf(channelId: String) = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

    fun parse(xml: String): List<FeedVideo> {
        val channel = Regex("<author>\\s*<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.let(::unescape).orEmpty()
        return Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
            val e = m.groupValues[1]
            val id = Regex("<yt:videoId>([A-Za-z0-9_-]{11})</yt:videoId>").find(e)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(e)?.groupValues?.get(1)?.let(::unescape).orEmpty()
            val published = Regex("<published>(.*?)</published>").find(e)?.groupValues?.get(1)
                ?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L
            val author = Regex("<name>(.*?)</name>").find(e)?.groupValues?.get(1)?.let(::unescape) ?: channel
            FeedVideo(id, title.take(300), author.ifBlank { channel }.take(200), published)
        }.toList()
    }

    /** Newest first, Shorts-length titles kept; at most [max]. */
    fun merge(feeds: List<List<FeedVideo>>, max: Int = 120): List<FeedVideo> = feeds.flatten().distinctBy { it.videoId }.sortedByDescending { it.published }.take(max)

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
}

object WatchSerializer {
    fun encode(d: WatchData): Map<String, String> = mapOf(
        "positions" to JSONArray().also { a ->
            d.positions.forEach { a.put(JSONObject().put("k", it.key).put("u", it.url).put("t", it.title).put("p", it.position).put("d", it.duration).put("w", it.updatedAt)) }
        }.toString(),
        "later" to JSONArray().also { a -> d.later.forEach { a.put(JSONObject().put("u", it.url).put("t", it.title).put("a", it.addedAt)) } }.toString(),
        "stats" to JSONObject().also { o -> d.stats.forEach { (k, v) -> o.put(k, v) } }.toString(),
        "channels" to JSONArray().also { a -> d.channels.forEach { a.put(JSONObject().put("i", it.id).put("t", it.title)) } }.toString(),
        "groups" to JSONObject().also { o -> d.groups.forEach { (k, v) -> o.put(k, JSONArray(v)) } }.toString(),
        "playlists" to JSONArray().also { a ->
            d.playlists.forEach { p ->
                a.put(JSONObject().put("n", p.name).put("i", JSONArray().also { ia -> p.items.forEach { ia.put(JSONObject().put("u", it.url).put("t", it.title).put("a", it.addedAt)) } }))
            }
        }.toString(),
        "speeds" to JSONObject().also { o -> d.channelSpeeds.forEach { (k, v) -> o.put(k, v) } }.toString(),
        "chstats" to JSONObject().also { o -> d.channelStats.forEach { (k, v) -> o.put(k, v) } }.toString()
    )

    fun decode(raw: (String) -> String?): WatchData = WatchData(
        positions = arr(raw("positions")).mapNotNull { o ->
            val u = o.optString("u").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            WatchPosition(o.optString("k").ifBlank { WatchRules.keyOf(u) }, u, o.optString("t"), o.optDouble("p", 0.0), o.optDouble("d", 0.0), o.optLong("w"))
        }.take(WatchRules.MAX_POSITIONS),
        later = arr(raw("later")).mapNotNull { o -> o.optString("u").takeIf { it.startsWith("https://") }?.let { WatchLaterItem(it, o.optString("t"), o.optLong("a")) } }.take(WatchRules.MAX_LATER),
        stats = runCatching { JSONObject(raw("stats") ?: "{}").let { o -> o.keys().asSequence().associateWith { o.optLong(it) } } }.getOrDefault(emptyMap()),
        channels = arr(raw("channels")).mapNotNull { o -> o.optString("i").takeIf { it.startsWith("UC") }?.let { Channel(it, o.optString("t")) } }.take(WatchRules.MAX_CHANNELS),
        groups = runCatching {
            val o = JSONObject(raw("groups") ?: "{}")
            o.keys().asSequence().mapNotNull { k ->
                val name = ChannelGroups.clean(k) ?: return@mapNotNull null
                val a = o.optJSONArray(k) ?: return@mapNotNull null
                val ids = (0 until minOf(a.length(), WatchRules.MAX_CHANNELS)).map { a.optString(it) }.filter { it.startsWith("UC") }.distinct()
                if (ids.isEmpty()) null else name to ids
            }.take(ChannelGroups.MAX_GROUPS).toMap()
        }.getOrDefault(emptyMap()),
        playlists = arr(raw("playlists")).mapNotNull { o ->
            val name = Playlists.clean(o.optString("n")) ?: return@mapNotNull null
            val items = o.optJSONArray("i")?.let { ia ->
                (0 until minOf(ia.length(), Playlists.MAX_ITEMS)).mapNotNull { ia.optJSONObject(it) }
                    .mapNotNull { io -> io.optString("u").takeIf { it.startsWith("https://") }?.let { WatchLaterItem(it, io.optString("t").take(300), io.optLong("a")) } }
            }.orEmpty()
            Playlist(name, items)
        }.distinctBy { it.name.lowercase() }.take(Playlists.MAX_LISTS),
        channelSpeeds = obj(raw("speeds")).let { o -> o.keys().asSequence().mapNotNull { k -> ChannelRules.keyOf(k)?.let { it to o.optInt(k, 100).coerceIn(50, 200) } }.take(ChannelRules.MAX_SPEEDS).toMap() },
        channelStats = obj(raw("chstats")).let { o -> o.keys().asSequence().map { it.take(100) to o.optLong(it).coerceAtLeast(0) }.take(ChannelRules.MAX_STATS).toMap() }
    )

    private fun obj(raw: String?): JSONObject = runCatching { JSONObject(raw ?: "{}") }.getOrDefault(JSONObject())

    private fun arr(raw: String?): List<JSONObject> = runCatching {
        val a = JSONArray(raw ?: "[]")
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }.getOrDefault(emptyList())
}

class WatchRepository(private val store: DataStore<Preferences>) {
    private fun key(name: String) = stringPreferencesKey("watch_$name")

    val data: Flow<WatchData> = store.data.catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { p -> WatchSerializer.decode { p[key(it)] } }

    suspend fun update(f: (WatchData) -> WatchData) {
        store.edit { p ->
            val cur = WatchSerializer.decode { p[key(it)] }
            val next = f(cur)
            if (next != cur) WatchSerializer.encode(next).forEach { (k, v) -> p[key(k)] = v }
        }
    }
}
