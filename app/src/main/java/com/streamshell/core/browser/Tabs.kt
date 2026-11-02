package com.streamshell.core.browser

import org.json.JSONArray
import org.json.JSONObject

/** One browser tab. [url] null = the StreamShell start page and nothing loaded yet. */
data class BrowserTab(
    val id: Long,
    val url: String? = null,
    val title: String = "",
    /** The start page is shown on top of (or instead of) the tab's page. */
    val showingStart: Boolean = true,
    /** The tab's page was opened from the start page, so Back without page history returns there. */
    val openedFromStart: Boolean = true,
    /** This tab is the persistent "instance" of a site app (SiteApp name, e.g. YOUTUBE): it is reused, never re-loaded by itself. */
    val instance: String? = null,
    /** This tab is the video player of the instance tab with this id (videos open here, so the instance keeps its page). */
    val playerFor: Long? = null,
    /** Private tab: nothing is remembered (history, positions, watch time, searches); never restored after a restart. */
    val private: Boolean = false
)

data class TabsState(val tabs: List<BrowserTab>, val activeId: Long) {
    val active: BrowserTab get() = tabs.firstOrNull { it.id == activeId } ?: tabs.first()

    companion object {
        fun initial() = TabsState(listOf(BrowserTab(1)), 1)

        /** The one tab allowed to play: the mini player's video (if that tab still exists), else the current tab. */
        fun mediaTab(t: TabsState, mini: Long?): Long = mini?.takeIf { id -> t.tabs.any { it.id == id } } ?: t.activeId
    }
}

/** Pure tab bookkeeping. Platform web views are created/destroyed by the engine to match. */
object TabsReducer {
    const val MAX_TABS = 10

    fun newTab(s: TabsState, url: String? = null): TabsState {
        if (s.tabs.size >= MAX_TABS) return s
        val id = (s.tabs.maxOfOrNull { it.id } ?: 0) + 1
        val tab = BrowserTab(id, url = url, showingStart = url == null, openedFromStart = url == null)
        return TabsState(s.tabs + tab, id)
    }

    /** A new private tab (start page) — see [BrowserTab.private]. */
    fun newPrivateTab(s: TabsState): TabsState {
        val n = newTab(s)
        return if (n === s) s else update(n, n.activeId) { it.copy(private = true) }
    }

    fun select(s: TabsState, id: Long) = if (s.tabs.any { it.id == id }) s.copy(activeId = id) else s

    /** Closing the last tab leaves one fresh start-page tab, so there is always something to show. */
    fun close(s: TabsState, id: Long): TabsState {
        val idx = s.tabs.indexOfFirst { it.id == id }
        if (idx < 0) return s
        val rest = s.tabs.filterNot { it.id == id }
        if (rest.isEmpty()) {
            val fresh = BrowserTab((s.tabs.maxOf { it.id }) + 1)
            return TabsState(listOf(fresh), fresh.id)
        }
        val active = if (s.activeId == id) rest[minOf(idx, rest.size - 1)].id else s.activeId
        return TabsState(rest, active)
    }

    fun update(s: TabsState, id: Long, f: (BrowserTab) -> BrowserTab) = s.copy(tabs = s.tabs.map { if (it.id == id) f(it) else it })

    /** Opens a new tab without selecting it (e.g. a video player tab prepared in the background). */
    fun newBackgroundTab(s: TabsState, f: (BrowserTab) -> BrowserTab): Pair<TabsState, Long?> {
        if (s.tabs.size >= MAX_TABS) return s to null
        val id = (s.tabs.maxOfOrNull { it.id } ?: 0) + 1
        return s.copy(tabs = s.tabs + f(BrowserTab(id))) to id
    }

    /** Tabs as the user counts them: an instance's video player belongs to its instance. */
    fun visibleCount(s: TabsState): Int = s.tabs.count { t -> t.playerFor == null || s.tabs.none { it.id == t.playerFor } }

    fun instanceTab(s: TabsState, app: String): BrowserTab? = s.tabs.firstOrNull { it.instance == app }
    fun playerTabOf(s: TabsState, instanceId: Long): BrowserTab? = s.tabs.firstOrNull { it.playerFor == instanceId }

    /**
     * A new app start (after leaving the app): never the last video again. Video player tabs are dropped; the default
     * site instance (if any) goes back to its home page; otherwise a start page is in front (an empty tab is reused).
     */
    fun freshStart(s: TabsState, defaultInstance: String?, instanceHome: String?): TabsState {
        val kept = s.tabs.filter { t -> t.playerFor == null }.ifEmpty { listOf(BrowserTab((s.tabs.maxOfOrNull { it.id } ?: 0) + 1)) }
        var next = TabsState(kept, kept.firstOrNull { it.id == s.activeId }?.id ?: kept.first().id)
        val inst = defaultInstance?.let { instanceTab(next, it) }
        if (inst != null && instanceHome != null) {
            return select(update(next, inst.id) { it.copy(url = instanceHome, showingStart = false) }, inst.id)
        }
        if (defaultInstance != null) return next
        val blank = next.tabs.firstOrNull { it.url == null && it.instance == null }
        if (blank != null) return select(update(next, blank.id) { it.copy(showingStart = true) }, blank.id)
        next = newTab(next)
        if (next.tabs.size == kept.size) next = update(next, next.activeId) { it.copy(showingStart = true) } // full: start page over the current tab
        return next
    }

    fun closeAll(s: TabsState): TabsState {
        val fresh = BrowserTab((s.tabs.maxOfOrNull { it.id } ?: 0) + 1)
        return TabsState(listOf(fresh), fresh.id)
    }
}

/** Open tabs survive an app restart (URL and title only; pages reload when a tab is shown). */
object TabsSerializer {
    fun encode(s: TabsState): String {
        val arr = JSONArray()
        // Private tabs are never written down (a restart starts without them).
        s.tabs.filterNot { it.private }.forEach { t ->
            arr.put(
                JSONObject().put("id", t.id).put("u", t.url ?: JSONObject.NULL).put("t", t.title.take(300)).put("s", t.showingStart).put("f", t.openedFromStart)
                    .put("i", t.instance ?: JSONObject.NULL).put("p", t.playerFor ?: JSONObject.NULL)
            )
        }
        val active = s.activeId.takeIf { id -> s.tabs.any { it.id == id && !it.private } } ?: s.tabs.firstOrNull { !it.private }?.id ?: 0L
        return JSONObject().put("tabs", arr).put("active", active).toString()
    }

    fun decode(raw: String?): TabsState {
        if (raw.isNullOrBlank()) return TabsState.initial()
        return try {
            val o = JSONObject(raw)
            val arr = o.getJSONArray("tabs")
            val tabs = (0 until minOf(arr.length(), TabsReducer.MAX_TABS)).mapNotNull { i ->
                val t = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = if (t.isNull("u")) null else t.optString("u").takeIf { it.startsWith("https://") && it.length <= 4096 }
                BrowserTab(
                    t.getLong("id"),
                    url,
                    t.optString("t"),
                    t.optBoolean("s", url == null) || url == null,
                    t.optBoolean("f", true),
                    instance = if (t.isNull("i")) null else t.optString("i").takeIf { n -> SiteApp.entries.any { it.name == n } },
                    playerFor = if (t.isNull("p") || !t.has("p")) null else t.optLong("p")
                )
            }.distinctBy { it.id }
            if (tabs.isEmpty()) {
                TabsState.initial()
            } else {
                TabsState(tabs, o.optLong("active").takeIf { a -> tabs.any { it.id == a } } ?: tabs.first().id)
            }
        } catch (_: Exception) {
            TabsState.initial()
        }
    }
}
