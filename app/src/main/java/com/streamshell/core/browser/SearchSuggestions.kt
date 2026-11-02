package com.streamshell.core.browser

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray

/**
 * Suggestions for the site search field: your own recent searches (kept on this device) and, while you type,
 * YouTube's public search suggestions (the same list youtube.com shows under its search box). Only the typed text
 * and the language are sent, to suggestqueries.google.com; no cookies, no account. Pure rules are unit-tested.
 */
object SearchSuggestions {
    const val MAX = 8
    const val MAX_RECENT = 20
    private const val MAX_BYTES = 64 * 1024

    fun url(query: String, lang: String): String = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&ie=utf-8&oe=utf-8&hl=" +
        URLEncoder.encode(lang.take(8), "UTF-8") + "&q=" + URLEncoder.encode(query.trim().take(200), "UTF-8")

    /** `["query", ["s1", "s2", …], …]` → the suggestions. Anything else → empty. */
    fun parse(raw: String?): List<String> = runCatching {
        val list = JSONArray(raw ?: return emptyList()).getJSONArray(1)
        (0 until list.length()).mapNotNull { i -> list.optString(i).trim().takeIf { it.isNotEmpty() && it.length <= 200 } }
    }.getOrDefault(emptyList()).distinctBy { it.lowercase() }.take(MAX)

    /** Empty field: recent searches. Typing: matching recent ones first (at most 3), then YouTube's, no duplicates. */
    fun merge(query: String, recent: List<String>, remote: List<String>): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return recent.take(MAX)
        val mine = recent.filter { it.startsWith(q, ignoreCase = true) }.take(3)
        return (mine + remote).distinctBy { it.lowercase() }.take(MAX)
    }

    /** Newest first, no duplicates (case-insensitive), at most [MAX_RECENT]. */
    fun remember(recent: List<String>, query: String): List<String> {
        val q = query.trim().take(200)
        if (q.isEmpty()) return recent
        return (listOf(q) + recent.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_RECENT)
    }

    fun fetch(query: String, lang: String): List<String> {
        if (query.isBlank()) return emptyList()
        val c = (URL(url(query, lang)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 3_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (c.responseCode !in 200..299) return emptyList()
            val bytes = c.inputStream.use { s ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (out.size() < MAX_BYTES) {
                    val n = s.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
            parse(String(bytes, Charsets.UTF_8))
        } finally {
            c.disconnect()
        }
    }
}
