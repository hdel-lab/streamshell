package com.streamshell.core.privacy

import com.streamshell.core.browser.UrlHosts
import com.streamshell.core.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class FilterPolicy(
    val enabled: Boolean,
    val userAllowed: Set<String> = emptySet(),
    val userBlocked: Set<String> = emptySet(),
    val exemptSites: Set<String> = emptySet(),
    val detailedLogging: Boolean = true
)

/** What the dashboard may truthfully say about the engine. */
sealed interface PrivacyStatus {
    data object Disabled : PrivacyStatus
    data object Initializing : PrivacyStatus
    data class Active(val ruleCount: Int) : PrivacyStatus
    data class Failed(val reason: String) : PrivacyStatus
}

object PrivacyStatusResolver {
    fun resolve(policy: FilterPolicy, engine: EngineState): PrivacyStatus = when {
        !policy.enabled -> PrivacyStatus.Disabled
        engine is EngineState.Failed -> PrivacyStatus.Failed(engine.reason)
        engine is EngineState.Initializing -> PrivacyStatus.Initializing
        engine is EngineState.Ready -> PrivacyStatus.Active(engine.ruleCount)
        else -> PrivacyStatus.Initializing
    }
}

/**
 * The single place where the browser asks "may this load?". Reads only @Volatile snapshots, so it is
 * safe from WebView's background threads (shouldInterceptRequest).
 *
 *  - [decideRequest]: sub-resources of a page (scripts, images, XHR, media ...). Blocking happens here.
 *  - [decideNavigation]: a top-level page. A list or user rule match is shown to the user, who can open it anyway.
 *  - A request counts as blocked only when the caller really refused it ([recordBlocked]).
 */
class PrivacyCoordinator(
    scope: CoroutineScope,
    settings: Flow<AppSettings>,
    private val engineState: StateFlow<EngineState>,
    private val repo: BlockedRequestRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {
    @Volatile private var policy = FilterPolicy(enabled = true)

    @Volatile private var engine: RequestFilter = DomainFilterEngine.EMPTY

    @Volatile private var layered: RequestFilter = build()

    val policyFlow: Flow<FilterPolicy> =
        settings.map { FilterPolicy(it.filteringEnabled, it.customAllowedDomains, it.customBlockedDomains, it.filterExemptSites, it.detailedLogging) }
            .distinctUntilChanged()

    init {
        scope.launch {
            policyFlow.collect {
                policy = it
                layered = build()
            }
        }
        scope.launch {
            engineState.collect {
                engine = (it as? EngineState.Ready)?.engine ?: DomainFilterEngine.EMPTY
                layered = build()
            }
        }
    }

    private fun build(): RequestFilter = LayeredFilter(policy.userAllowed, policy.userBlocked, engine, FunctionalHosts::isFunctional)

    fun statusFlow(): Flow<PrivacyStatus> = kotlinx.coroutines.flow.combine(policyFlow, engineState) { p, e -> PrivacyStatusResolver.resolve(p, e) }.distinctUntilChanged()

    fun isExempt(pageHost: String?): Boolean {
        val h = pageHost ?: return false
        return DomainFilterEngine.suffixes(h).any { it in policy.exemptSites } || h in policy.exemptSites
    }

    /** Pure decision for one host (diagnostics "test a domain"). */
    fun evaluateHost(host: String, pageHost: String? = null): FilterDecision {
        if (!policy.enabled) return FilterDecision.Allow(AllowReason.DISABLED)
        if (isExempt(pageHost)) return FilterDecision.Allow(AllowReason.SITE_EXEMPT)
        return layered.evaluate(host, pageHost)
    }

    fun decideRequest(url: String, pageUrl: String?, isMainFrame: Boolean): FilterDecision {
        if (!policy.enabled) return FilterDecision.Allow(AllowReason.DISABLED)
        if (isMainFrame) return FilterDecision.Allow(AllowReason.MAIN_FRAME)
        val scheme = url.substringBefore(':').lowercase()
        if (scheme != "https" && scheme != "http") return FilterDecision.Allow(AllowReason.NOT_HTTP)
        val host = UrlHosts.hostOf(url) ?: return FilterDecision.Allow(AllowReason.NO_MATCH)
        return evaluateHost(host, UrlHosts.hostOf(pageUrl))
    }

    /** Top-level navigation: lists and user rules apply to the target site itself (third-party rules do not). */
    fun decideNavigation(url: String): FilterDecision {
        val host = UrlHosts.hostOf(url) ?: return FilterDecision.Allow(AllowReason.NO_MATCH)
        if (!policy.enabled) return FilterDecision.Allow(AllowReason.DISABLED)
        if (isExempt(host)) return FilterDecision.Allow(AllowReason.SITE_EXEMPT)
        return layered.evaluate(host, host)
    }

    /** Call exactly when a request or page was really refused. Stores the host only, never the URL. */
    fun recordBlocked(host: String, d: FilterDecision.Block) {
        repo.recordBlocked(BlockedRequest(if (policy.detailedLogging) host else "(logging off)", d.rule, d.source, clock()))
    }

    /** A request that was let through (counted for the dashboard; host only). */
    fun recordAllowed(url: String) {
        if (!policy.enabled) return
        repo.recordObserved(if (policy.detailedLogging) UrlHosts.hostOf(url) else null)
    }
}
