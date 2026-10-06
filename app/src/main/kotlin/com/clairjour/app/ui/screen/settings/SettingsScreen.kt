package com.clairjour.app.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clairjour.app.BuildConfig
import com.clairjour.app.R
import com.clairjour.app.data.AppContainer
import com.clairjour.app.data.prefs.AppLanguage
import com.clairjour.app.data.prefs.LocaleManager
import com.clairjour.app.data.prefs.ThemeMode
import com.clairjour.app.domain.AddictionType
import com.clairjour.app.notifications.ReminderScheduler
import com.clairjour.app.ui.components.viewModelFactoryOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    contentPadding: PaddingValues,
    onEditAddiction: (String) -> Unit,
    onAddAddiction: () -> Unit,
    onOpenShield: () -> Unit
) {
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactoryOf {
            SettingsViewModel(
                container.settingsRepository,
                container.addictionRepository,
                container.backupRepository
            )
        }
    )
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // File pickers — the actual encryption call happens after the passphrase dialog closes.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { pendingExportUri = it } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingImportUri = it } }

    // Export passphrase dialog
    pendingExportUri?.let { uri ->
        PassphraseDialog(
            titleRes = R.string.backup_passphrase_export_title,
            bodyRes = R.string.backup_passphrase_export_body,
            requireConfirm = true,
            onDismiss = { pendingExportUri = null },
            onConfirmed = { passphrase ->
                vm.exportBackup(uri, passphrase)
                pendingExportUri = null
            }
        )
    }

    // Import passphrase dialog
    pendingImportUri?.let { uri ->
        PassphraseDialog(
            titleRes = R.string.backup_passphrase_import_title,
            bodyRes = R.string.backup_passphrase_import_body,
            requireConfirm = false,
            onDismiss = { pendingImportUri = null },
            onConfirmed = { passphrase ->
                vm.importBackup(uri, passphrase)
                pendingImportUri = null
            }
        )
    }

    // Toast on backup result
    val exportSuccess = stringResource(R.string.backup_export_success)
    val importSuccess = stringResource(R.string.backup_import_success)
    val exportError = stringResource(R.string.backup_export_error)
    val importError = stringResource(R.string.backup_import_error)
    val badPassphrase = stringResource(R.string.backup_passphrase_bad)
    val corruptError = stringResource(R.string.backup_import_corrupt)
    val tooLarge = stringResource(R.string.backup_import_too_large)
    val unsupported = stringResource(R.string.backup_import_error)
    LaunchedEffect(state.backupStatus) {
        val msg = when (state.backupStatus) {
            BackupStatus.SUCCESS_EXPORT -> exportSuccess
            BackupStatus.SUCCESS_IMPORT -> importSuccess
            BackupStatus.ERROR_EXPORT -> exportError
            BackupStatus.ERROR_IMPORT -> importError
            BackupStatus.ERROR_BAD_PASSPHRASE -> badPassphrase
            BackupStatus.ERROR_CORRUPT -> corruptError
            BackupStatus.ERROR_UNSUPPORTED_VERSION -> unsupported
            BackupStatus.ERROR_TOO_LARGE -> tooLarge
            else -> null
        }
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.clearBackupStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        Section(title = stringResource(R.string.settings_language)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = state.language == lang,
                        onClick = {
                            vm.setLanguage(lang)
                            LocaleManager.apply(lang)
                        },
                        label = {
                            Text(
                                when (lang) {
                                    AppLanguage.SYSTEM -> stringResource(R.string.settings_theme_system)
                                    AppLanguage.ENGLISH -> "English"
                                    AppLanguage.FRENCH -> "Français"
                                }
                            )
                        }
                    )
                }
            }
        }

        Section(title = stringResource(R.string.settings_theme)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.themeMode == ThemeMode.SYSTEM,
                    onClick = { vm.setTheme(ThemeMode.SYSTEM) },
                    label = { Text(stringResource(R.string.settings_theme_system)) }
                )
                FilterChip(
                    selected = state.themeMode == ThemeMode.LIGHT,
                    onClick = { vm.setTheme(ThemeMode.LIGHT) },
                    label = { Text(stringResource(R.string.settings_theme_light)) }
                )
                FilterChip(
                    selected = state.themeMode == ThemeMode.DARK,
                    onClick = { vm.setTheme(ThemeMode.DARK) },
                    label = { Text(stringResource(R.string.settings_theme_dark)) }
                )
            }
        }

        Section(title = stringResource(R.string.settings_notifications)) {
            ToggleRow(
                label = stringResource(R.string.settings_notif_pledge),
                checked = state.notifPledgeEnabled,
                onCheckedChange = { enabled ->
                    vm.setPledgeEnabled(enabled)
                    if (enabled) ReminderScheduler.schedulePledge(context, state.pledgeTime.first, state.pledgeTime.second)
                    else ReminderScheduler.cancelPledge(context)
                }
            )
            if (state.notifPledgeEnabled) {
                NotifTimeRow(
                    hour = state.pledgeTime.first,
                    minute = state.pledgeTime.second,
                    onTimeChange = { h, m ->
                        vm.setNotifPledgeTime(h, m)
                        ReminderScheduler.schedulePledge(context, h, m)
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            ToggleRow(
                label = stringResource(R.string.settings_notif_journal),
                checked = state.notifJournalEnabled,
                onCheckedChange = { enabled ->
                    vm.setJournalEnabled(enabled)
                    if (enabled) ReminderScheduler.scheduleJournal(context, state.journalTime.first, state.journalTime.second)
                    else ReminderScheduler.cancelJournal(context)
                }
            )
            if (state.notifJournalEnabled) {
                NotifTimeRow(
                    hour = state.journalTime.first,
                    minute = state.journalTime.second,
                    onTimeChange = { h, m ->
                        vm.setNotifJournalTime(h, m)
                        ReminderScheduler.scheduleJournal(context, h, m)
                    }
                )
            }
        }

        Section(title = stringResource(R.string.shield_title)) {
            Surface(
                onClick = onOpenShield,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.shield_settings_entry_body),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }

        Section(title = stringResource(R.string.settings_backup)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportLauncher.launch("clairjour_backup_$timestamp.cjbk")
                    },
                    modifier = Modifier.weight(1f),
                    enabled = state.backupStatus != BackupStatus.LOADING
                ) {
                    Text(stringResource(R.string.settings_backup_export))
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                    enabled = state.backupStatus != BackupStatus.LOADING
                ) {
                    Text(stringResource(R.string.settings_backup_import))
                }
            }
        }

        Section(title = stringResource(R.string.settings_addictions)) {
            state.addictions.forEach { addiction ->
                Surface(
                    onClick = { onEditAddiction(addiction.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(addiction.name, style = MaterialTheme.typography.titleMedium)
                            val type = AddictionType.fromName(addiction.type)
                            Text(
                                stringResource(type.labelRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (addiction.isPrimary) {
                            Text(
                                "★",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            TextButton(onClick = { vm.makePrimary(addiction.id) }) {
                                Text("★", color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAddAddiction,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_add))
            }
        }

        Section(title = stringResource(R.string.settings_about)) {
            Text(
                "Clairjour · v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(10.dp))
        content()
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PassphraseDialog(
    titleRes: Int,
    bodyRes: Int,
    requireConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirmed: (CharArray) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val mismatch = requireConfirm && confirmation.isNotEmpty() && passphrase != confirmation
    val tooShort = requireConfirm && passphrase.isNotEmpty() && passphrase.length < 8
    val canConfirm = passphrase.isNotEmpty() &&
        (!requireConfirm || (!mismatch && !tooShort && confirmation.isNotEmpty()))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                Text(
                    stringResource(bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.backup_passphrase_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = tooShort,
                    modifier = Modifier.fillMaxWidth()
                )
                if (tooShort) {
                    Text(
                        stringResource(R.string.backup_passphrase_too_short),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (requireConfirm) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text(stringResource(R.string.backup_passphrase_confirm_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = mismatch,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mismatch) {
                        Text(
                            stringResource(R.string.backup_passphrase_mismatch),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val chars = passphrase.toCharArray()
                    passphrase = ""
                    confirmation = ""
                    onConfirmed(chars)
                },
                enabled = canConfirm
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = {
                passphrase = ""
                confirmation = ""
                onDismiss()
            }) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotifTimeRow(hour: Int, minute: Int, onTimeChange: (Int, Int) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "%02d:%02d".format(hour, minute),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { showPicker = true }) {
            Text(stringResource(R.string.action_edit))
        }
    }

    if (showPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(pickerState.hour, pickerState.minute)
                    showPicker = false
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            text = { TimePicker(state = pickerState) }
        )
    }
}
