package com.streamshell.core.browser

import com.streamshell.core.model.SearchEngine
import java.net.URLEncoder

/** What the address bar turns the user's text into. */
sealed interface AddressTarget {
    data class Url(val url: String) : AddressTarget
    data class Search(val query: String, val url: String) : AddressTarget
    data object Empty : AddressTarget
}

/**
 * Decides "address or search?" like a desktop browser:
 *  - an explicit scheme (https://, http://) is an address; http is upgraded to https (cleartext is disabled);
 *  - a single token that looks like a host name with a dot ("youtube.com", "m.youtube.com/feed") is an address;
 *  - everything else (spaces, no dot, unknown schemes such as javascript:) is a search.
 */
object AddressInput {
    private const val MAX = 2048
    private val hostLike = Regex("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+(:[0-9]{1,5})?([/?#].*)?$")
    private val tld = Regex("\\.[A-Za-z]{2,63}$")

    fun resolve(raw: String, engine: SearchEngine): AddressTarget {
        val text = raw.trim().take(MAX)
        if (text.isEmpty()) return AddressTarget.Empty
        val lower = text.lowercase()
        if (lower.startsWith("https://") && text.length > "https://".length) return AddressTarget.Url(text)
        if (lower.startsWith("http://") && text.length > "http://".length) return AddressTarget.Url("https://" + text.substring(7))
        if (text.none { it.isWhitespace() } && hostLike.matches(text)) {
            val host = text.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
            if (tld.containsMatchIn(host) || host == "localhost") return AddressTarget.Url("https://$text")
        }
        return AddressTarget.Search(text, engine.queryUrl + URLEncoder.encode(text, "UTF-8"))
    }

    /** Short form for the address bar while not editing: host plus path, no scheme or "www.". */
    fun display(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return url.removePrefix("https://").removePrefix("http://").removePrefix("www.").removeSuffix("/")
    }
}
