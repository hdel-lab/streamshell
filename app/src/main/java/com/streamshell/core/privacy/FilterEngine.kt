package com.streamshell.core.privacy

sealed interface FilterDecision {
    /** [rule] is the matching rule in list syntax; [source] is the list id ("builtin", a subscription id) or [USER_SOURCE]. */
    data class Block(val rule: String, val source: String?) : FilterDecision
    data class Allow(val reason: AllowReason) : FilterDecision
}

enum class AllowReason {
    NO_MATCH,
    EXCEPTION,
    FIRST_PARTY,
    USER_ALLOW,

    /** Needed for sign-in, video, images or subtitles (see [FunctionalHosts]); lists never block it. */
    FUNCTIONAL,

    /** The page's site is on the user's "no blocking on this site" list. */
    SITE_EXEMPT,
    DISABLED,
    MAIN_FRAME,
    NOT_HTTP
}

const val USER_SOURCE = "custom"

/** Android-independent contract so the engine can be tested and swapped. */
interface RequestFilter {
    fun evaluate(requestHost: String, pageHost: String?): FilterDecision
}

/**
 * Deterministic, immutable suffix-matching engine. Thread-safe by immutability.
 *
 * Limitations: "third-party" uses an approximate registrable-domain heuristic (no Public Suffix
 * List), so unusual multi-label public suffixes can be misclassified.
 */
class DomainFilterEngine(rules: List<DomainRule>) : RequestFilter {
    private class BlockEntry(val thirdPartyOnly: Boolean, val listId: String?)

    private val blocked: Map<String, BlockEntry>
    private val exceptions: Set<String>
    val ruleCount: Int

    init {
        val b = HashMap<String, BlockEntry>()
        val e = HashSet<String>()
        for (r in rules) {
            if (r.exception) {
                e += r.domain
            } else {
                val old = b[r.domain]
                // An unconditional rule wins over a third-party-only one; otherwise the first list keeps the attribution.
                if (old == null || (old.thirdPartyOnly && !r.thirdPartyOnly)) b[r.domain] = BlockEntry(r.thirdPartyOnly, r.listId)
            }
        }
        blocked = b
        exceptions = e
        ruleCount = b.size + e.size
    }

    override fun evaluate(requestHost: String, pageHost: String?): FilterDecision {
        val host = requestHost.lowercase().trimEnd('.')
        val candidates = suffixes(host)
        if (candidates.any { it in exceptions }) return FilterDecision.Allow(AllowReason.EXCEPTION)

        val matched = candidates.firstOrNull { it in blocked } ?: return FilterDecision.Allow(AllowReason.NO_MATCH)
        val entry = blocked.getValue(matched)
        val page = pageHost?.lowercase()?.trimEnd('.')
        val thirdParty = page == null || registrable(page) != registrable(host)
        if (entry.thirdPartyOnly && !thirdParty) return FilterDecision.Allow(AllowReason.FIRST_PARTY)
        return FilterDecision.Block("||$matched^", entry.listId)
    }

    companion object {
        /** "a.b.c.com" → [a.b.c.com, b.c.com, c.com]; a bare TLD is never a candidate. */
        internal fun suffixes(host: String): List<String> {
            val labels = host.split('.')
            if (labels.size < 2) return listOf(host)
            return (0..labels.size - 2).map { labels.drop(it).joinToString(".") }
        }

        private val secondLevel = setOf("co", "com", "org", "net", "gov", "ac", "edu")

        /** Approximate registrable domain (eTLD+1) without a Public Suffix List. */
        internal fun registrable(host: String): String {
            val labels = host.split('.')
            if (labels.size <= 2) return host
            val last = labels.last()
            val second = labels[labels.size - 2]
            val take = if (last.length == 2 && second in secondLevel && labels.size >= 3) 3 else 2
            return labels.takeLast(take).joinToString(".")
        }

        val EMPTY = DomainFilterEngine(emptyList())
    }
}

/**
 * The user's own domain rules layered over the list engine. DOCUMENTED PRECEDENCE (first match wins):
 *   1. user allow rule      -> allowed   (an allowed domain also covers its subdomains)
 *   2. list exception (@@)  -> allowed
 *   3. user block rule      -> blocked   (applies to the domain and its subdomains, first- and third-party)
 *   4. functional host      -> allowed   (sign-in, video, images, subtitles: lists must not break them)
 *   5. list block rule      -> blocked
 *   6. otherwise            -> allowed
 */
class LayeredFilter(
    private val userAllowed: Set<String>,
    private val userBlocked: Set<String>,
    private val lists: RequestFilter,
    private val functional: (String) -> Boolean = { false }
) : RequestFilter {
    override fun evaluate(requestHost: String, pageHost: String?): FilterDecision {
        val host = requestHost.lowercase().trimEnd('.')
        val suffixes = DomainFilterEngine.suffixes(host)
        if (suffixes.any { it in userAllowed }) return FilterDecision.Allow(AllowReason.USER_ALLOW)
        val fromLists = lists.evaluate(host, pageHost)
        if (fromLists is FilterDecision.Allow && fromLists.reason == AllowReason.EXCEPTION) return fromLists
        suffixes.firstOrNull { it in userBlocked }?.let { return FilterDecision.Block("||$it^", USER_SOURCE) }
        if (fromLists is FilterDecision.Block && functional(host)) return FilterDecision.Allow(AllowReason.FUNCTIONAL)
        return fromLists
    }
}

/**
 * Hosts that sign-in, video playback, thumbnails and subtitles depend on. A list rule never blocks them
 * (only your own block rules can). Ads served from these same hosts (e.g. YouTube video ads, which come
 * from the same servers as the video) are therefore NOT blocked; domain filtering cannot separate them.
 */
object FunctionalHosts {
    private val exact = setOf(
        "accounts.google.com", "accounts.youtube.com", "myaccount.google.com", "www.google.com", "apis.google.com",
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtubei.googleapis.com", "youtu.be",
        "player.vimeo.com", "vimeo.com", "www.dailymotion.com", "www.twitch.tv", "gql.twitch.tv"
    )
    private val suffixes = listOf(
        "googlevideo.com", "ytimg.com", "ggpht.com", "gstatic.com", "googleusercontent.com",
        "vimeocdn.com", "dmcdn.net", "jtvnw.net", "ttvnw.net"
    )

    fun isFunctional(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return h in exact || suffixes.any { h == it || h.endsWith(".$it") }
    }
}

/** Validation for domains typed by the user (no schemes, paths, wildcards or IPs-with-ports). */
object DomainInput {
    private val re = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    /** Accepts "Example.com", "https://example.com/path", "||example.com^"; returns the bare lowercase domain or null. */
    fun normalize(raw: String): String? {
        var s = raw.trim().lowercase()
        s = s.removePrefix("||").removeSuffix("^").removePrefix("https://").removePrefix("http://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#').trimEnd('.')
        return s.takeIf { it.length <= 253 && re.matches(it) && !it.all { c -> c.isDigit() || c == '.' } }
    }
}
