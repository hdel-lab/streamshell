package com.streamshell.core.browser

/** What to do with a top-level navigation (a link the user tapped, a redirect, the address bar). */
sealed interface NavigationDecision {
    /** Load in the tab. [url] may differ from the request (http is upgraded to https). */
    data class InApp(val url: String) : NavigationDecision

    /** Hand to another app (mailto:, tel:, intent:, market: ...). Only ever on a user gesture. */
    data class External(val uri: String) : NavigationDecision
    data object Refuse : NavigationDecision
}

object NavigationPolicy {
    private val externalSchemes = setOf("mailto", "tel", "sms", "smsto", "geo", "market", "intent")

    fun decide(url: String, hasGesture: Boolean): NavigationDecision {
        val scheme = url.substringBefore(':', "").lowercase()
        return when {
            url == "about:blank" -> NavigationDecision.InApp(url)

            scheme == "https" -> if (UrlHosts.hostOf(url) != null) NavigationDecision.InApp(url) else NavigationDecision.Refuse

            // Cleartext is disabled app-wide; most sites answer on https too.
            scheme == "http" -> {
                val upgraded = "https://" + url.substringAfter("://")
                if (UrlHosts.hostOf(upgraded) != null) NavigationDecision.InApp(upgraded) else NavigationDecision.Refuse
            }

            scheme in externalSchemes -> if (hasGesture) NavigationDecision.External(url) else NavigationDecision.Refuse

            // javascript:, file:, content:, data:, chrome: and anything unknown never navigate the top frame.
            else -> NavigationDecision.Refuse
        }
    }

    /** The https fallback an intent: link may carry (S.browser_fallback_url=...), if any. */
    fun intentFallback(intentUri: String): String? {
        val m = Regex("S\\.browser_fallback_url=([^;]+)").find(intentUri) ?: return null
        val decoded = try {
            java.net.URLDecoder.decode(m.groupValues[1], "UTF-8")
        } catch (_: Exception) {
            return null
        }
        return decoded.takeIf { it.startsWith("https://") && UrlHosts.hostOf(it) != null }
    }
}
