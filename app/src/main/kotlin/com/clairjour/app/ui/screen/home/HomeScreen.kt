package com.clairjour.app.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clairjour.app.R
import com.clairjour.app.data.AppContainer
import com.clairjour.app.data.db.AddictionEntity
import com.clairjour.app.domain.AddictionType
import com.clairjour.app.domain.Milestones
import com.clairjour.app.ui.components.ClairjourBrandBadge
import com.clairjour.app.ui.components.ClairjourCard
import com.clairjour.app.ui.components.MilestoneProgress
import com.clairjour.app.ui.components.viewModelFactoryOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Composable
fun HomeScreen(
    container: AppContainer,
    contentPadding: PaddingValues,
    onAddAddiction: () -> Unit,
    onOpenJournalEditor: () -> Unit,
    onOpenCrisis: (String?) -> Unit
) {
    val vm: HomeViewModel = viewModel(
        factory = viewModelFactoryOf {
            HomeViewModel(
                container.addictionRepository,
                container.pledgeRepository,
                container.journalRepository,
                container.motivationsRepository,
                container.relapseRepository,
                container.milestoneDao
            )
        }
    )
    val state by vm.uiState.collectAsState()
    var showRelapseDialog by remember { mutableStateOf(false) }
    var showPledgeDialog by remember { mutableStateOf(false) }
    var relapseNote by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val relapseRecordedText = stringResource(R.string.relapse_recorded)
    val relapseUndoText = stringResource(R.string.relapse_undo)
    val relapseAlreadyTodayText = stringResource(R.string.relapse_already_today)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.current != null) {
                ExtendedFloatingActionButton(
                    onClick = { onOpenCrisis(state.current?.id) },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    icon = {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.cd_crisis_fab)
                        )
                    },
                    text = { Text(stringResource(R.string.crisis_breathe)) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
        val current = state.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(contentPadding)
                .padding(horizontal = 20.dp)
        ) {
            // Scrollable main content — adapts to any screen height.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                if (state.addictions.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.addictions, key = { it.id }) { addiction ->
                            val isCurrent = state.current?.id == addiction.id
                            FilterChip(
                                selected = isCurrent,
                                onClick = { vm.select(addiction.id) },
                                label = { Text(addiction.name) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (current == null) {
                    EmptyState(onAdd = onAddAddiction)
                } else {
                    CounterBlock(addiction = current, startDate = current.startDate)
                    Spacer(Modifier.height(12.dp))

                    state.nextMilestone?.let { next ->
                        val remaining = (next.days - state.streakDays).coerceAtLeast(0)
                        MilestoneProgress(
                            label = stringResource(
                                R.string.home_next_milestone,
                                stringResource(next.labelRes),
                                remaining
                            ),
                            progress = state.progressToNext
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    PledgeCard(done = state.pledgeDone, onPledge = { showPledgeDialog = true })
                    Spacer(Modifier.height(8.dp))

                    state.motivation?.let { motivation ->
                        MotivationCard(motivation.textFor(LocalConfiguration.current.locales[0].language))
                        Spacer(Modifier.height(8.dp))
                    }

                    JournalQuickCard(
                        written = state.journalWrittenToday,
                        onOpen = onOpenJournalEditor
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Relapse button fixed above the snackbar zone — 80dp bottom clears the snackbar height.
            if (current != null) {
                TextButton(
                    onClick = { showRelapseDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 80.dp)
                ) {
                    Text(
                        stringResource(R.string.detail_report_relapse),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Milestone celebration overlay
        val unseen = state.unseenMilestone
        AnimatedVisibility(
            visible = unseen != null,
            enter = fadeIn() + scaleIn(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (unseen != null) {
                MilestoneCelebrationOverlay(
                    milestoneDays = unseen.milestoneDays,
                    onDismiss = { vm.dismissMilestone(unseen.id) }
                )
            }
        }
    }
    }

    if (showPledgeDialog) {
        AlertDialog(
            onDismissRequest = { showPledgeDialog = false },
            title = { Text(stringResource(R.string.pledge_dialog_title)) },
            text = { Text(stringResource(R.string.pledge_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.pledge()
                    showPledgeDialog = false
                }) { Text(stringResource(R.string.home_pledge_cta)) }
            },
            dismissButton = {
                TextButton(onClick = { showPledgeDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showRelapseDialog) {
        AlertDialog(
            onDismissRequest = { showRelapseDialog = false },
            title = { Text(stringResource(R.string.detail_relapse_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.detail_relapse_body))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = relapseNote,
                        onValueChange = { relapseNote = it },
                        label = { Text(stringResource(R.string.detail_relapse_note)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val note = relapseNote.ifBlank { null }
                    vm.reportRelapse(
                        note = note,
                        onUndoWindowOpen = {
                            coroutineScope.launch {
                                // Long ≈ 10s: comfortably wider than the VM's 5s undo window,
                                // so the user always sees the action before it expires.
                                val result = snackbarHostState.showSnackbar(
                                    message = relapseRecordedText,
                                    actionLabel = relapseUndoText,
                                    duration = androidx.compose.material3.SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    vm.undoLastRelapse()
                                }
                            }
                        },
                        onBlocked = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(relapseAlreadyTodayText)
                            }
                        }
                    )
                    relapseNote = ""
                    showRelapseDialog = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRelapseDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun MilestoneCelebrationOverlay(milestoneDays: Int, onDismiss: () -> Unit) {
    val milestone = Milestones.all.firstOrNull { it.days == milestoneDays }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WbSunny,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (milestone != null) stringResource(milestone.labelRes) else "$milestoneDays days",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.milestone_celebration_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text(
                        stringResource(R.string.milestone_celebration_dismiss),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.onboarding_addiction_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        ) { Text(stringResource(R.string.action_add)) }
    }
}

@Composable
private fun CounterBlock(
    addiction: AddictionEntity,
    startDate: Instant
) {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(startDate) {
        while (isActive) {
            delay(1_000)
            now = Clock.System.now()
        }
    }

    val totalSeconds = (now - startDate).inWholeSeconds.coerceAtLeast(0)
    val elapsedDays = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60

    val type = AddictionType.fromName(addiction.type)
    val counterDescription = stringResource(
        R.string.cd_counter_days_hours_minutes,
        elapsedDays.toInt(),
        hours.toInt(),
        minutes.toInt()
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = counterDescription
            }
    ) {
        Text(
            text = addiction.name.ifBlank { stringResource(type.labelRes) },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Text(
            text = elapsedDays.toString(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 80.sp,
                fontWeight = FontWeight.Light
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = if (elapsedDays == 1L) stringResource(R.string.home_day_label)
                   else stringResource(R.string.home_days_label),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(12.dp))

        Text(
            text = "%dh %02dm %02ds".format(hours, minutes, seconds),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PledgeCard(done: Boolean, onPledge: () -> Unit) {
    if (done) {
        ClairjourCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.home_pledge_done),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    } else {
        Button(
            onClick = onPledge,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text(
                stringResource(R.string.home_pledge_cta),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MotivationCard(text: String) {
    ClairjourCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                stringResource(R.string.home_quote_of_day),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "\" $text \"",
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun JournalQuickCard(written: Boolean, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (written) stringResource(R.string.home_journal_edit)
                       else stringResource(R.string.home_journal_write),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "→",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
