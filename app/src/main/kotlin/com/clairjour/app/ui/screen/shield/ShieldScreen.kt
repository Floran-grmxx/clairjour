package com.clairjour.app.ui.screen.shield

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clairjour.app.BuildConfig
import com.clairjour.app.R
import com.clairjour.app.data.AppContainer
import com.clairjour.app.shield.ShieldRule
import com.clairjour.app.shield.ShieldServiceStatus
import com.clairjour.app.shield.ShieldSettings
import com.clairjour.app.ui.components.viewModelFactoryOf
import kotlinx.coroutines.delay

private val ShieldRule.titleRes: Int
    get() = when (this) {
        ShieldRule.REELS_TAB -> R.string.shield_rule_reels_tab
        ShieldRule.DIRECT_SINGLE_REEL -> R.string.shield_rule_direct_single_reel
        ShieldRule.OTHER_REELS -> R.string.shield_rule_other_reels
        ShieldRule.PROFILE_REELS -> R.string.shield_rule_profile_reels
        ShieldRule.EXPLORE_GRID -> R.string.shield_rule_explore_grid
        ShieldRule.SEARCH_ACCOUNTS_ONLY -> R.string.shield_rule_search_accounts_only
        ShieldRule.HOME_FEED -> R.string.shield_rule_home_feed
        ShieldRule.STORIES -> R.string.shield_rule_stories
    }

private val ShieldRule.bodyRes: Int
    get() = when (this) {
        ShieldRule.REELS_TAB -> R.string.shield_rule_reels_tab_body
        ShieldRule.DIRECT_SINGLE_REEL -> R.string.shield_rule_direct_single_reel_body
        ShieldRule.OTHER_REELS -> R.string.shield_rule_other_reels_body
        ShieldRule.PROFILE_REELS -> R.string.shield_rule_profile_reels_body
        ShieldRule.EXPLORE_GRID -> R.string.shield_rule_explore_grid_body
        ShieldRule.SEARCH_ACCOUNTS_ONLY -> R.string.shield_rule_search_accounts_only_body
        ShieldRule.HOME_FEED -> R.string.shield_rule_home_feed_body
        ShieldRule.STORIES -> R.string.shield_rule_stories_body
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: ShieldViewModel = viewModel(
        factory = viewModelFactoryOf { ShieldViewModel(container.settingsRepository) }
    )
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var showDisclosure by remember { mutableStateOf(false) }

    // The user enables the service in Android settings: re-check whenever the screen comes back.
    LifecycleResumeEffect(Unit) {
        vm.refreshServiceStatus(ShieldServiceStatus.isEnabled(context))
        onPauseOrDispose { }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.shield_disclosure_title)) },
            text = { Text(stringResource(R.string.shield_disclosure_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    ShieldServiceStatus.openAccessibilitySettings(context)
                }) { Text(stringResource(R.string.shield_disclosure_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) {
                    Text(stringResource(R.string.shield_disclosure_decline))
                }
            }
        )
    }

    state.pendingDisable?.let {
        DelayedDisableDialog(onConfirm = vm::confirmDisable, onDismiss = vm::cancelDisable)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.shield_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            StatusCard(serviceEnabled = state.serviceEnabled, onActivate = { showDisclosure = true })
            Spacer(Modifier.height(16.dp))

            ShieldToggleRow(
                title = stringResource(R.string.shield_master),
                body = null,
                checked = state.settings.enabled,
                onCheckedChange = vm::onMasterChange
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.shield_rules_section).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(6.dp))
            ShieldRule.entries.forEach { rule ->
                ShieldToggleRow(
                    title = stringResource(rule.titleRes),
                    body = stringResource(rule.bodyRes),
                    checked = rule in state.settings.rules,
                    enabled = state.settings.enabled,
                    onCheckedChange = { vm.onRuleChange(rule, it) }
                )
            }

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ShieldToggleRow(
                    title = stringResource(R.string.shield_diagnostic),
                    body = stringResource(R.string.shield_diagnostic_body),
                    checked = state.settings.diagnostic,
                    onCheckedChange = vm::setDiagnostic
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StatusCard(serviceEnabled: Boolean, onActivate: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (serviceEnabled) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (serviceEnabled) R.string.shield_status_on else R.string.shield_status_off),
                    style = MaterialTheme.typography.titleMedium
                )
                if (!serviceEnabled) {
                    Text(
                        stringResource(R.string.shield_status_off_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!serviceEnabled) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onActivate) { Text(stringResource(R.string.shield_activate)) }
            }
        }
    }
}

@Composable
private fun ShieldToggleRow(
    title: String,
    body: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Switching a protection off requires waiting, so that the urge has time to pass. */
@Composable
private fun DelayedDisableDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var secondsLeft by remember { mutableIntStateOf(ShieldSettings.DISABLE_DELAY_SECONDS) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shield_disable_title)) },
        text = { Text(stringResource(R.string.shield_disable_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = secondsLeft == 0) {
                Text(
                    if (secondsLeft > 0) stringResource(R.string.shield_disable_wait, secondsLeft)
                    else stringResource(R.string.shield_disable_confirm)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
