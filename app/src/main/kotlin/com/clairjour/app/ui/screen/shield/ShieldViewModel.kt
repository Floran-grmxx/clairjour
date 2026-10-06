package com.clairjour.app.ui.screen.shield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clairjour.app.data.prefs.SettingsRepository
import com.clairjour.app.shield.ShieldRule
import com.clairjour.app.shield.ShieldSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A protection waiting for the delayed confirmation before being switched off. */
sealed interface PendingDisable {
    data object AllProtections : PendingDisable
    data class Rule(val rule: ShieldRule) : PendingDisable
}

data class ShieldUiState(
    val settings: ShieldSettings = ShieldSettings(),
    val serviceEnabled: Boolean = false,
    val pendingDisable: PendingDisable? = null
)

class ShieldViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val serviceEnabled = MutableStateFlow(false)
    private val pendingDisable = MutableStateFlow<PendingDisable?>(null)

    val state: StateFlow<ShieldUiState> = combine(
        settingsRepository.shieldSettingsFlow,
        serviceEnabled,
        pendingDisable
    ) { settings, enabled, pending ->
        ShieldUiState(settings, enabled, pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShieldUiState())

    fun refreshServiceStatus(enabled: Boolean) {
        serviceEnabled.value = enabled
    }

    /** Switching on is immediate; switching off goes through the delayed confirmation. */
    fun onMasterChange(enabled: Boolean) {
        if (enabled) viewModelScope.launch { settingsRepository.setShieldEnabled(true) }
        else pendingDisable.value = PendingDisable.AllProtections
    }

    fun onRuleChange(rule: ShieldRule, enabled: Boolean) {
        if (enabled) viewModelScope.launch { settingsRepository.setShieldRule(rule, true) }
        else pendingDisable.value = PendingDisable.Rule(rule)
    }

    fun confirmDisable() {
        val pending = pendingDisable.value ?: return
        pendingDisable.value = null
        viewModelScope.launch {
            when (pending) {
                PendingDisable.AllProtections -> settingsRepository.setShieldEnabled(false)
                is PendingDisable.Rule -> settingsRepository.setShieldRule(pending.rule, false)
            }
        }
    }

    fun cancelDisable() {
        pendingDisable.value = null
    }

    fun setDiagnostic(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShieldDiagnostic(enabled) }
    }
}
