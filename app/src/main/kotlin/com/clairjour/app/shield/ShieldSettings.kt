package com.clairjour.app.shield

/** User configuration of the Instagram shield. */
data class ShieldSettings(
    val enabled: Boolean = true,
    val rules: Set<ShieldRule> = ShieldRule.defaults,
    /** Debug builds only: logs the Instagram screen structure to calibrate selectors. */
    val diagnostic: Boolean = false
) {
    val activeRules: Set<ShieldRule> get() = if (enabled) rules else emptySet()

    companion object {
        /** Waiting time before a protection can be switched off. */
        const val DISABLE_DELAY_SECONDS = 10
    }
}
