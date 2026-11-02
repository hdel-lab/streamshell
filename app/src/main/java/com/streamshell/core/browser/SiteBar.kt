package com.streamshell.core.browser

/**
 * The site app's bottom bar, arranged by the user: which items it shows and in which order ("search" = the search
 * capsule, the rest are the site's sections). At most [MAX]; "More" is always added last by the UI and holds the
 * sections that are not on the bar. Kept per site app. Pure rules, unit-tested.
 */
object SiteBar {
    const val SEARCH = "search"

    /** StreamShell's own Library (playlists, Watch later, subscriptions, watch time) — YouTube only. */
    const val LIBRARY = "ss-library"
    const val MAX = 5
    private val DEFAULT_IN_MORE = setOf("shorts", "live")

    fun all(app: SiteApp): List<String> = listOf(SEARCH) + app.destinations.map { it.id } + if (app == SiteApp.YOUTUBE) listOf(LIBRARY) else emptyList()

    fun default(app: SiteApp): List<String> = (listOf(SEARCH) + app.destinations.map { it.id }.filterNot { it in DEFAULT_IN_MORE } + if (app == SiteApp.YOUTUBE) listOf(LIBRARY) else emptyList()).take(MAX)

    /** What the bar shows, in order. Unknown or empty configuration → the default. */
    fun items(app: SiteApp, config: List<String>?): List<String> {
        val ok = config.orEmpty().filter { it in all(app) }.distinct().take(MAX)
        return ok.ifEmpty { default(app) }
    }

    /** Sections not on the bar (shown under More). */
    fun hidden(app: SiteApp, config: List<String>?): List<SiteDestination> {
        val shown = items(app, config)
        return app.destinations.filter { it.id !in shown }
    }

    /** Show/hide [key]; at least one item stays, at most [MAX]. New items go to the end. */
    fun toggle(app: SiteApp, config: List<String>?, key: String): List<String> {
        val cur = items(app, config)
        return when {
            key !in all(app) -> cur
            key in cur -> if (cur.size <= 1) cur else cur - key
            cur.size >= MAX -> cur
            else -> cur + key
        }
    }

    /** Moves [key] by [delta] places (−1 = left/up, +1 = right/down), within the bar. */
    fun move(app: SiteApp, config: List<String>?, key: String, delta: Int): List<String> {
        val cur = items(app, config).toMutableList()
        val i = cur.indexOf(key)
        val j = i + delta
        if (i < 0 || j !in cur.indices) return cur
        cur.add(j, cur.removeAt(i))
        return cur
    }

    fun sanitize(map: Map<String, List<String>>): Map<String, List<String>> = map.mapNotNull { (k, v) ->
        val app = SiteApp.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
        val ok = v.filter { it in all(app) }.distinct().take(MAX)
        if (ok.isEmpty()) null else k to ok
    }.toMap()

    /** "YOUTUBE=search,home;YOUTUBE_MUSIC=home,search" */
    fun encode(map: Map<String, List<String>>): String = sanitize(map).entries.joinToString(";") { (k, v) -> k + "=" + v.joinToString(",") }

    fun decode(raw: String?): Map<String, List<String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return sanitize(
            raw.split(';').mapNotNull { part ->
                val k = part.substringBefore('=', "").trim()
                if (k.isEmpty()) null else k to part.substringAfter('=').split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }.toMap()
        )
    }
}
