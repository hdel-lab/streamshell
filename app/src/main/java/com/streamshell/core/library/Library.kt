package com.streamshell.core.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(val url: String, val title: String, val addedAt: Long)
data class HistoryEntry(val url: String, val title: String, val visitedAt: Long)

/** Only real https pages are kept; titles and URLs are length-limited. */
internal fun keepableUrl(url: String?): String? = url?.takeIf { it.startsWith("https://") && it.length <= 2048 }

object LibrarySerializer {
    const val MAX_BOOKMARKS = 500
    const val MAX_HISTORY = 500

    fun encodeBookmarks(list: List<Bookmark>): String = JSONArray().also { a -> list.take(MAX_BOOKMARKS).forEach { a.put(JSONObject().put("u", it.url).put("t", it.title.take(300)).put("a", it.addedAt)) } }.toString()

    fun decodeBookmarks(raw: String?): List<Bookmark> = decode(raw, MAX_BOOKMARKS) { o ->
        keepableUrl(o.optString("u"))?.let { Bookmark(it, o.optString("t"), o.optLong("a")) }
    }.distinctBy { it.url }

    fun encodeHistory(list: List<HistoryEntry>): String = JSONArray().also { a -> list.take(MAX_HISTORY).forEach { a.put(JSONObject().put("u", it.url).put("t", it.title.take(300)).put("v", it.visitedAt)) } }.toString()

    fun decodeHistory(raw: String?): List<HistoryEntry> = decode(raw, MAX_HISTORY) { o ->
        keepableUrl(o.optString("u"))?.let { HistoryEntry(it, o.optString("t"), o.optLong("v")) }
    }

    private fun <T> decode(raw: String?, max: Int, f: (JSONObject) -> T?): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until minOf(a.length(), max)).mapNotNull { i -> a.optJSONObject(i)?.let(f) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** Pure history rules: newest first, a revisit moves the entry to the top, capped. */
object HistoryRules {
    fun record(list: List<HistoryEntry>, url: String, title: String, now: Long): List<HistoryEntry> {
        val u = keepableUrl(url) ?: return list
        val keepTitle = title.ifBlank { list.firstOrNull { it.url == u }?.title.orEmpty() }
        return (listOf(HistoryEntry(u, keepTitle.take(300), now)) + list.filterNot { it.url == u }).take(LibrarySerializer.MAX_HISTORY)
    }

    fun retitle(list: List<HistoryEntry>, url: String, title: String): List<HistoryEntry> = if (title.isBlank()) list else list.map { if (it.url == url) it.copy(title = title.take(300)) else it }

    fun search(list: List<HistoryEntry>, query: String): List<HistoryEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return list
        return list.filter { it.url.lowercase().contains(q) || it.title.lowercase().contains(q) }
    }
}

interface LibraryRepository {
    val bookmarks: Flow<List<Bookmark>>
    val history: Flow<List<HistoryEntry>>
    suspend fun toggleBookmark(url: String, title: String, now: Long = System.currentTimeMillis())
    suspend fun removeBookmark(url: String)
    suspend fun recordVisit(url: String, title: String, now: Long = System.currentTimeMillis())
    suspend fun updateTitle(url: String, title: String)
    suspend fun removeHistory(url: String)
    suspend fun clearHistory()

    /** Restore from a backup: replaces both lists. */
    suspend fun replaceAll(bookmarks: List<Bookmark>, history: List<HistoryEntry>)
}

class DataStoreLibraryRepository(private val store: DataStore<Preferences>) : LibraryRepository {
    private object Keys {
        val bookmarks = stringPreferencesKey("bookmarks_json")
        val history = stringPreferencesKey("history_json")
    }

    private val prefs = store.data.catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }

    override val bookmarks: Flow<List<Bookmark>> = prefs.map { LibrarySerializer.decodeBookmarks(it[Keys.bookmarks]) }.distinctUntilChanged()
    override val history: Flow<List<HistoryEntry>> = prefs.map { LibrarySerializer.decodeHistory(it[Keys.history]) }.distinctUntilChanged()

    override suspend fun toggleBookmark(url: String, title: String, now: Long) {
        val u = keepableUrl(url) ?: return
        store.edit { p ->
            val list = LibrarySerializer.decodeBookmarks(p[Keys.bookmarks])
            val next = if (list.any { it.url == u }) list.filterNot { it.url == u } else listOf(Bookmark(u, title.ifBlank { u }.take(300), now)) + list
            p[Keys.bookmarks] = LibrarySerializer.encodeBookmarks(next)
        }
    }

    override suspend fun removeBookmark(url: String) {
        store.edit { p -> p[Keys.bookmarks] = LibrarySerializer.encodeBookmarks(LibrarySerializer.decodeBookmarks(p[Keys.bookmarks]).filterNot { it.url == url }) }
    }

    override suspend fun recordVisit(url: String, title: String, now: Long) {
        store.edit { p -> p[Keys.history] = LibrarySerializer.encodeHistory(HistoryRules.record(LibrarySerializer.decodeHistory(p[Keys.history]), url, title, now)) }
    }

    override suspend fun updateTitle(url: String, title: String) {
        store.edit { p -> p[Keys.history] = LibrarySerializer.encodeHistory(HistoryRules.retitle(LibrarySerializer.decodeHistory(p[Keys.history]), url, title)) }
    }

    override suspend fun removeHistory(url: String) {
        store.edit { p -> p[Keys.history] = LibrarySerializer.encodeHistory(LibrarySerializer.decodeHistory(p[Keys.history]).filterNot { it.url == url }) }
    }

    override suspend fun clearHistory() {
        store.edit { it.remove(Keys.history) }
    }

    override suspend fun replaceAll(bookmarks: List<Bookmark>, history: List<HistoryEntry>) {
        store.edit { p ->
            p[Keys.bookmarks] = LibrarySerializer.encodeBookmarks(bookmarks)
            p[Keys.history] = LibrarySerializer.encodeHistory(history)
        }
    }
}
