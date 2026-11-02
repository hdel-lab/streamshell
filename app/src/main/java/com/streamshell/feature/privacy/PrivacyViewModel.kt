package com.streamshell.feature.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamshell.core.model.AppSettings
import com.streamshell.core.privacy.AddResult
import com.streamshell.core.privacy.AllowReason
import com.streamshell.core.privacy.BlockedRequest
import com.streamshell.core.privacy.BlockedRequestRepository
import com.streamshell.core.privacy.DomainInput
import com.streamshell.core.privacy.EngineState
import com.streamshell.core.privacy.FilterDecision
import com.streamshell.core.privacy.FilterListRepository
import com.streamshell.core.privacy.ListEntry
import com.streamshell.core.privacy.PrivacyCoordinator
import com.streamshell.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrivacyUiState(
    val settings: AppSettings = AppSettings(),
    val engine: EngineState = EngineState.Initializing,
    val entries: List<ListEntry> = emptyList(),
    val updating: Boolean = false,
    val blocked: Long = 0,
    val observed: Long = 0,
    val observedHosts: Map<String, Int> = emptyMap(),
    val recent: List<BlockedRequest> = emptyList(),
    /** A host the user must decide to trust before a list from it can be added. */
    val pendingTrust: PendingList? = null,
    val message: String? = null
) {
    val enabledLists get() = entries.count { it.subscription.enabled }

    /** Newest successful download of any list. */
    val lastUpdateMs: Long? get() = entries.mapNotNull { it.status.lastSuccessMs }.maxOrNull()
}

data class PendingList(val name: String, val url: String, val host: String)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val lists: FilterListRepository,
    private val repo: BlockedRequestRepository,
    private val coordinator: PrivacyCoordinator
) : ViewModel() {
    private val transient = MutableStateFlow<Pair<PendingList?, String?>>(null to null)

    val state: StateFlow<PrivacyUiState> = combine(
        combine(settings.settings, lists.engineState, lists.entries, lists.updating) { s, e, en, u -> PrivacyUiState(settings = s, engine = e, entries = en, updating = u) },
        combine(repo.blockedCount, repo.observedCount, repo.observedHosts, repo.recent) { b, o, h, r -> arrayOf<Any>(b, o, h, r) },
        transient
    ) { base, stats, (pending, msg) ->
        @Suppress("UNCHECKED_CAST")
        base.copy(
            blocked = stats[0] as Long,
            observed = stats[1] as Long,
            observedHosts = stats[2] as Map<String, Int>,
            recent = stats[3] as List<BlockedRequest>,
            pendingTrust = pending,
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUiState())

    private fun say(text: String) = transient.update { it.first to text }
    fun messageShown() = transient.update { it.first to null }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    fun updateNow() {
        viewModelScope.launch {
            val s = lists.updateAll()
            say(
                when {
                    s.updated + s.notModified + s.failed == 0 -> "No list is enabled."
                    s.failed == 0 -> "Lists are up to date (${s.updated} updated, ${s.notModified} unchanged)."
                    else -> "${s.failed} list(s) failed; previous copies stay active. Details are shown per list."
                }
            )
        }
    }

    fun setListEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { lists.setEnabled(id, enabled) }
    }

    fun removeList(id: String) {
        viewModelScope.launch { lists.remove(id) }
    }

    fun addList(name: String, url: String, trustHost: Boolean = false) {
        viewModelScope.launch {
            when (val r = lists.add(name, url, trustHost)) {
                is AddResult.Added -> {
                    transient.update { null to it.second }
                    lists.updateAll(setOf(r.id))
                    say("List added.")
                }

                is AddResult.NeedsTrust -> transient.update { PendingList(name, url, r.host) to it.second }

                is AddResult.Invalid -> say(r.reason)
            }
        }
    }

    fun cancelTrust() = transient.update { null to it.second }

    fun addBlocked(raw: String) = addDomain(raw, blocked = true)
    fun addAllowed(raw: String) = addDomain(raw, blocked = false)

    private fun addDomain(raw: String, blocked: Boolean) {
        val d = DomainInput.normalize(raw)
        if (d == null) {
            say("“${raw.trim()}” is not a valid domain name.")
            return
        }
        update { s ->
            if (blocked) {
                s.copy(customBlockedDomains = s.customBlockedDomains + d, customAllowedDomains = s.customAllowedDomains - d)
            } else {
                s.copy(customAllowedDomains = s.customAllowedDomains + d, customBlockedDomains = s.customBlockedDomains - d)
            }
        }
    }

    fun addExempt(raw: String) {
        val d = DomainInput.normalize(raw)
        if (d == null) {
            say("“${raw.trim()}” is not a valid domain name.")
            return
        }
        update { it.copy(filterExemptSites = it.filterExemptSites + d) }
    }

    fun removeExempt(d: String) = update { it.copy(filterExemptSites = it.filterExemptSites - d) }

    fun removeBlocked(d: String) = update { it.copy(customBlockedDomains = it.customBlockedDomains - d) }
    fun removeAllowed(d: String) = update { it.copy(customAllowedDomains = it.customAllowedDomains - d) }

    fun clearStats() = repo.clear()

    fun resetDefaults() {
        viewModelScope.launch {
            lists.resetToDefaults()
            settings.update {
                it.copy(
                    filteringEnabled = true,
                    detailedLogging = true,
                    updateIntervalHours = 24,
                    customBlockedDomains = emptySet(),
                    customAllowedDomains = emptySet(),
                    filterExemptSites = emptySet()
                )
            }
            say("Reset to the recommended defaults.")
        }
    }

    /** Human-readable verdict for a domain loaded by another site, using exactly the rules the browser applies. */
    fun explain(raw: String): String {
        val d = DomainInput.normalize(raw) ?: return "Enter a domain name such as example.com."
        return when (val r = coordinator.evaluateHost(d)) {
            is FilterDecision.Block -> "Blocked by ${r.rule} (${sourceName(r.source)})."

            is FilterDecision.Allow -> when (r.reason) {
                AllowReason.USER_ALLOW -> "Allowed: you allowed this domain."
                AllowReason.EXCEPTION -> "Allowed: a list exception applies."
                AllowReason.FUNCTIONAL -> "Allowed: a list matches, but this host is needed for sign-in or video, so lists never block it."
                AllowReason.DISABLED -> "Not checked: protection is off."
                else -> "Not blocked: no rule matches."
            }
        }
    }

    private fun sourceName(id: String?): String = when (id) {
        null -> "unknown list"
        "custom" -> "your rule"
        "builtin" -> "built-in list"
        else -> state.value.entries.firstOrNull { it.subscription.id == id }?.subscription?.name ?: id
    }
}
