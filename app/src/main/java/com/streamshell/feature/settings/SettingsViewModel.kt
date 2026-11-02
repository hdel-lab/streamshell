package com.streamshell.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamshell.core.model.AppSettings
import com.streamshell.core.privacy.PrivacyCoordinator
import com.streamshell.core.privacy.PrivacyStatus
import com.streamshell.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val privacySummary: String = "",
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    coordinator: PrivacyCoordinator
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> = combine(repo.settings, coordinator.statusFlow(), message) { s, status, msg ->
        SettingsUiState(
            s,
            when (status) {
                PrivacyStatus.Disabled -> "Protection is off"
                PrivacyStatus.Initializing -> "Starting…"
                is PrivacyStatus.Active -> "Protection on · ${status.ruleCount} rules · lists, custom rules and update schedule"
                is PrivacyStatus.Failed -> "Filter engine failed: ${status.reason}"
            },
            msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.update(transform) }
    }

    fun resetSettings() {
        viewModelScope.launch {
            repo.update { SettingsReset.apply(it) }
            message.value = "Settings reset. AdBlock & Privacy settings were kept."
        }
    }

    fun messageShown() {
        message.value = null
    }
}

/**
 * "Reset settings": every app setting back to its default (appearance, browsing, start page, playback, YouTube,
 * bottom bar). The AdBlock & Privacy settings have their own reset there and are kept. Pure, unit-tested.
 */
object SettingsReset {
    fun apply(cur: AppSettings): AppSettings = AppSettings().copy(
        filteringEnabled = cur.filteringEnabled,
        detailedLogging = cur.detailedLogging,
        customBlockedDomains = cur.customBlockedDomains,
        customAllowedDomains = cur.customAllowedDomains,
        filterExemptSites = cur.filterExemptSites,
        updateIntervalHours = cur.updateIntervalHours
    )
}
