package com.streamshell.core.privacy

import org.json.JSONArray
import org.json.JSONObject

/** A remote list definition shown to the user with attribution. Rules inside are data only, never executed. */
data class FilterSource(
    val id: String,
    val name: String,
    val url: String,
    val license: String,
    val homepage: String
)

object FilterSources {
    const val BUILTIN_ID = "builtin"

    /** Recommended lists. EasyList/EasyPrivacy are browser-oriented; only their domain rules are used (the rest is counted as skipped). */
    val recommended = listOf(
        FilterSource("easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt", "GPLv3 / CC BY-SA 3.0", "https://easylist.to/"),
        FilterSource("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt", "GPLv3 / CC BY-SA 3.0", "https://easylist.to/"),
        FilterSource(
            "urlhaus",
            "URLhaus malware domains (abuse.ch)",
            "https://urlhaus.abuse.ch/downloads/hostfile/",
            "CC0 1.0",
            "https://urlhaus.abuse.ch/"
        )
    )

    /** Enabled out of the box; the others are one tap away. */
    val defaultEnabled = setOf("easyprivacy", "urlhaus")

    /** Hosts a list may be downloaded from (and redirected to) unless the user trusts more. */
    val defaultTrustedHosts = setOf("easylist.to", "urlhaus.abuse.ch")
}

data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val license: String?,
    val homepage: String?,
    val enabled: Boolean,
    /** One of [FilterSources.recommended]: can be disabled and restored, user-added lists can be removed. */
    val recommended: Boolean
)

data class ListStatus(
    val lastSuccessMs: Long? = null,
    val lastAttemptMs: Long? = null,
    val ruleCount: Int? = null,
    val skippedCount: Int? = null,
    /** Visible reason of the last failed update. The previous (last-known-good) copy stays active. */
    val error: String? = null,
    val etag: String? = null,
    val lastModified: String? = null
)

data class ListEntry(val subscription: Subscription, val status: ListStatus, val cached: Boolean)

/** All persisted list metadata (not the rules themselves). */
data class FilterConfig(
    val subscriptions: List<Subscription>,
    val statuses: Map<String, ListStatus>,
    val trustedHosts: Set<String>
) {
    companion object {
        fun defaults() = FilterConfig(
            subscriptions = FilterSources.recommended.map {
                Subscription(it.id, it.name, it.url, it.license, it.homepage, it.id in FilterSources.defaultEnabled, recommended = true)
            },
            statuses = emptyMap(),
            trustedHosts = FilterSources.defaultTrustedHosts
        )
    }
}

object FilterConfigSerializer {
    fun encode(c: FilterConfig): String {
        val subs = JSONArray()
        c.subscriptions.forEach {
            subs.put(
                JSONObject().put("id", it.id).put("n", it.name).put("u", it.url).put("l", it.license ?: JSONObject.NULL)
                    .put("h", it.homepage ?: JSONObject.NULL).put("e", it.enabled).put("r", it.recommended)
            )
        }
        val st = JSONObject()
        c.statuses.forEach { (id, s) ->
            st.put(
                id,
                JSONObject().put("ok", s.lastSuccessMs ?: JSONObject.NULL).put("at", s.lastAttemptMs ?: JSONObject.NULL)
                    .put("rc", s.ruleCount ?: JSONObject.NULL).put("sk", s.skippedCount ?: JSONObject.NULL)
                    .put("er", s.error ?: JSONObject.NULL).put("et", s.etag ?: JSONObject.NULL).put("lm", s.lastModified ?: JSONObject.NULL)
            )
        }
        return JSONObject().put("subs", subs).put("status", st).put("trusted", JSONArray(c.trustedHosts.toList())).toString()
    }

    /** Tolerant: anything malformed falls back to the defaults, entries with non-https URLs are dropped. */
    fun decode(raw: String?): FilterConfig {
        if (raw.isNullOrBlank()) return FilterConfig.defaults()
        return try {
            val o = JSONObject(raw)
            val subsArr = o.getJSONArray("subs")
            val subs = (0 until subsArr.length()).mapNotNull { i ->
                val s = subsArr.optJSONObject(i) ?: return@mapNotNull null
                val url = s.optString("u")
                val id = s.optString("id")
                if (!url.startsWith("https://") || id.isBlank() || id.length > 80) {
                    null
                } else {
                    Subscription(
                        id,
                        s.optString("n").ifBlank { id }.take(60),
                        url,
                        s.optString("l").takeIf { it.isNotBlank() && !s.isNull("l") },
                        s.optString("h").takeIf { it.isNotBlank() && !s.isNull("h") },
                        s.optBoolean("e", true),
                        s.optBoolean("r", false)
                    )
                }
            }.distinctBy { it.id }
            val stObj = o.optJSONObject("status") ?: JSONObject()
            val statuses = subs.mapNotNull { sub ->
                val s = stObj.optJSONObject(sub.id) ?: return@mapNotNull null
                fun long(k: String) = if (s.isNull(k)) null else s.optLong(k)
                fun int(k: String) = if (s.isNull(k)) null else s.optInt(k)
                fun str(k: String) = if (s.isNull(k)) null else s.optString(k).takeIf { it.isNotEmpty() }
                sub.id to ListStatus(long("ok"), long("at"), int("rc"), int("sk"), str("er"), str("et"), str("lm"))
            }.toMap()
            val trustedArr = o.optJSONArray("trusted")
            val trusted = if (trustedArr == null) {
                FilterSources.defaultTrustedHosts
            } else {
                (0 until trustedArr.length()).mapNotNull { DomainInput.normalize(trustedArr.optString(it)) }.toSet()
            }
            FilterConfig(subs, statuses, trusted)
        } catch (_: Exception) {
            FilterConfig.defaults()
        }
    }
}
