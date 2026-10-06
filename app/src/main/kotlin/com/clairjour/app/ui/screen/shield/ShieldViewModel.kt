package com.clairjour.app.ui.screen.shield

import android.os.SystemClock
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

/** What the user asked to switch off. */
sealed interface DisableTarget {
    data object AllProtections : DisableTarget
    data class Rule(val rule: ShieldRule) : DisableTarget
}

/**
 * A protection waiting for the delayed confirmation. The deadline lives here, not in the
 * dialog, so that rotating the screen does not restart the countdown.
 */
data class PendingDisable(val target: DisableTarget, val confirmableAtMillis: Long)

data class ShieldUiState(
    val settings: ShieldSettings = ShieldSettings(),
    val serviceEnabled: Boolean = false,
    val pendingDisable: PendingDisable? = null
)

class ShieldViewModel(
    private val settingsRepository: SettingsRepository,
    private val clock: () -> Long = SystemClock::elapsedRealtime
) : ViewModel() {

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
        else requestDisable(DisableTarget.AllProtections)
    }

    fun onRuleChange(rule: ShieldRule, enabled: Boolean) {
        if (enabled) viewModelScope.launch { settingsRepository.setShieldRule(rule, true) }
        else requestDisable(DisableTarget.Rule(rule))
    }

    private fun requestDisable(target: DisableTarget) {
        val delayMillis = ShieldSettings.DISABLE_DELAY_SECONDS * 1_000L
        pendingDisable.value = PendingDisable(target, clock() + delayMillis)
    }

    /** Seconds left before the switch-off can be confirmed (0 when allowed). */
    fun secondsBeforeConfirm(pending: PendingDisable): Int {
        val remainingMillis = (pending.confirmableAtMillis - clock()).coerceAtLeast(0)
        return ((remainingMillis + 999) / 1_000).toInt()
    }

    fun confirmDisable() {
        val pending = pendingDisable.value ?: return
        // The button is disabled during the countdown; this guard covers any early call.
        if (secondsBeforeConfirm(pending) > 0) return
        pendingDisable.value = null
        viewModelScope.launch {
            when (val target = pending.target) {
                DisableTarget.AllProtections -> settingsRepository.setShieldEnabled(false)
                is DisableTarget.Rule -> settingsRepository.setShieldRule(target.rule, false)
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
