package com.streamshell.core.community

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** GET for small JSON answers: https only, the given hosts only, no redirects, no cookies, 10 s, 1 MB. */
fun interface JsonHttp {
    @Throws(IOException::class)
    fun get(url: String): Pair<Int, String>
}

class UrlConnectionJsonHttp(private val hosts: Set<String>) : JsonHttp {
    override fun get(url: String): Pair<Int, String> {
        val u = URL(url)
        require(u.protocol == "https" && u.host in hosts) { "refusing $url" }
        val c = (u.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = c.responseCode
            if (code !in 200..299) return code to ""
            val out = java.io.ByteArrayOutputStream()
            c.inputStream.use { s ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    if (out.size() + n > 1_000_000) throw IOException("answer too large")
                    out.write(buf, 0, n)
                }
            }
            return code to out.toString("UTF-8")
        } finally {
            c.disconnect()
        }
    }
}

private class Lru<V>(private val max: Int) {
    private val m = object : LinkedHashMap<String, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?) = size > max
    }

    @Synchronized fun get(k: String): V? = m[k]

    @Synchronized fun has(k: String) = m.containsKey(k)

    @Synchronized fun put(k: String, v: V) {
        m[k] = v
    }
}

/**
 * Return YouTube Dislike (https://returnyoutubedislike.com): estimated dislike counts. The video ID of the open video is
 * sent to returnyoutubedislikeapi.com (its API takes no hash prefix). Failures mean "unknown" (nothing is shown).
 */
class DislikeRepository(private val http: JsonHttp = UrlConnectionJsonHttp(setOf(HOST))) {
    private val cache = Lru<Long>(128)

    suspend fun dislikes(videoId: String): Long? {
        cache.get(videoId)?.let { return it }
        val n = withContext(Dispatchers.IO) {
            runCatching {
                val (code, body) = http.get("https://$HOST/votes?videoId=$videoId")
                if (code == 200) parse(body, videoId) else null
            }.getOrNull()
        } ?: return null
        cache.put(videoId, n)
        return n
    }

    companion object {
        const val HOST = "returnyoutubedislikeapi.com"
        fun parse(body: String, videoId: String): Long? = runCatching {
            val o = JSONObject(body)
            if (o.optString("id") != videoId) return null
            o.optLong("dislikes", -1).takeIf { it >= 0 }
        }.getOrNull()
    }
}

/**
 * DeArrow (https://dearrow.ajay.app): community titles in place of clickbait titles. Like SponsorBlock, only the first
 * 4 characters of the SHA-256 hash of each video ID are sent to sponsor.ajay.app; the server answers with all videos of
 * that prefix and the match happens on the phone. Data: DeArrow contributors, CC BY-NC-SA 4.0.
 */
class DeArrowRepository(private val http: JsonHttp = UrlConnectionJsonHttp(setOf(com.streamshell.core.sponsor.SponsorBlockApi.HOST))) {
    /** Per prefix: video id → title (only videos with a usable community title). */
    private val cache = Lru<Map<String, String>>(256)
    private val sem = Semaphore(4)

    suspend fun titles(ids: List<String>): Map<String, String> {
        val valid = ids.filter { Regex("[A-Za-z0-9_-]{11}").matches(it) }.distinct().take(60)
        val byPrefix = valid.groupBy { com.streamshell.core.sponsor.SponsorBlockApi.hashPrefix(it) }
        val out = HashMap<String, String>()
        coroutineScope {
            byPrefix.map { (prefix, vids) ->
                async(Dispatchers.IO) {
                    val map = cache.get(prefix) ?: sem.withPermit {
                        runCatching {
                            val (code, body) = http.get("https://${com.streamshell.core.sponsor.SponsorBlockApi.HOST}/api/branding/$prefix?service=YouTube")
                            when (code) {
                                200 -> parse(body)
                                404 -> emptyMap()
                                else -> null
                            }
                        }.getOrNull()?.also { cache.put(prefix, it) }
                    } ?: emptyMap()
                    vids.mapNotNull { v -> map[v]?.let { v to it } }
                }
            }.forEach { d -> d.await().forEach { (k, v) -> out[k] = v } }
        }
        return out
    }

    companion object {
        /**
         * The prefix answer {"<videoId>": {"titles": [{title, original, votes, locked}], …}} → id → title. A title is used
         * like the DeArrow extension does: the first one, if it is not the original and is locked or not voted down.
         */
        fun parse(body: String): Map<String, String> = runCatching {
            val o = JSONObject(body)
            val out = HashMap<String, String>()
            o.keys().forEach { id ->
                if (!Regex("[A-Za-z0-9_-]{11}").matches(id)) return@forEach
                val first = o.optJSONObject(id)?.optJSONArray("titles")?.optJSONObject(0) ?: return@forEach
                if (first.optBoolean("original")) return@forEach
                if (!first.optBoolean("locked") && first.optInt("votes", 0) < 0) return@forEach
                val t = clean(first.optString("title"))
                if (t.isNotEmpty()) out[id] = t
            }
            out
        }.getOrDefault(emptyMap())

        /** DeArrow marks words that must keep their case with ">"; shown without the marks. */
        fun clean(t: String): String = t.replace(Regex("(^|\\s)>(\\S)"), "$1$2").replace(Regex("\\s+"), " ").trim().take(200)
    }
}
