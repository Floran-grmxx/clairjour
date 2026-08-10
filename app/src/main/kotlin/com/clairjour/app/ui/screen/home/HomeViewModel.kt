package com.clairjour.app.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clairjour.app.data.db.AddictionEntity
import com.clairjour.app.data.db.MilestoneDao
import com.clairjour.app.data.db.MilestoneReachedEntity
import com.clairjour.app.data.motivations.Motivation
import com.clairjour.app.data.motivations.MotivationsRepository
import com.clairjour.app.data.repository.AddictionRepository
import com.clairjour.app.data.repository.JournalRepository
import com.clairjour.app.data.repository.PledgeRepository
import com.clairjour.app.data.repository.RelapseRepository
import com.clairjour.app.data.repository.RelapseSnapshot
import com.clairjour.app.domain.Milestone
import com.clairjour.app.domain.Milestones
import com.clairjour.app.domain.Streak
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class HomeUiState(
    val loading: Boolean = true,
    val addictions: List<AddictionEntity> = emptyList(),
    val current: AddictionEntity? = null,
    val streakDays: Int = 0,
    val nextMilestone: Milestone? = null,
    val progressToNext: Float = 0f,
    val pledgeDone: Boolean = false,
    val motivation: Motivation? = null,
    val journalWrittenToday: Boolean = false,
    val unseenMilestone: MilestoneReachedEntity? = null
)

class HomeViewModel(
    private val addictionRepo: AddictionRepository,
    private val pledgeRepo: PledgeRepository,
    private val journalRepo: JournalRepository,
    private val motivationsRepo: MotivationsRepository,
    private val relapseRepo: RelapseRepository,
    private val milestoneDao: MilestoneDao
) : ViewModel() {

    private val selectedIdFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = combine(
        addictionRepo.observeActive(),
        selectedIdFlow
    ) { list, selected ->
        list to (list.firstOrNull { it.id == selected } ?: list.firstOrNull())
    }.flatMapLatest { (list, current) ->
        if (current == null) {
            flowOf(HomeUiState(loading = false, addictions = list))
        } else {
            val today = todayLocal()
            combine(
                pledgeRepo.observeFor(current.id, today),
                journalRepo.observeByDate(today),
                milestoneDao.observeFor(current.id)
            ) { pledge, journalToday, milestones ->
                val days = Streak.daysSince(current.startDate)
                HomeUiState(
                    loading = false,
                    addictions = list,
                    current = current,
                    streakDays = days,
                    nextMilestone = Milestones.next(days),
                    progressToNext = Milestones.progressToNext(days),
                    pledgeDone = pledge != null,
                    motivation = motivationsRepo.ofDay(),
                    journalWrittenToday = journalToday != null,
                    unseenMilestone = milestones.firstOrNull { !it.seenByUser }
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    init {
        // Milestone auto-insertion runs in its OWN pipeline, driven by (addiction.id,
        // addiction.startDate). Mixing this write into the combine above caused a race:
        // when reportRelapse fired, `observeFor(milestones)` could emit an empty list
        // *before* `observeActive` emitted the new startDate — resulting in
        // `days = 40, milestones = []` → re-inserting all past milestones as unseen.
        viewModelScope.launch {
            addictionRepo.observeActive()
                .map { list -> list.firstOrNull { it.id == selectedIdFlow.value } ?: list.firstOrNull() }
                .distinctUntilChanged { a, b -> a?.id == b?.id && a?.startDate == b?.startDate }
                .collect { current ->
                    if (current != null) {
                        val days = Streak.daysSince(current.startDate)
                        if (days > 0) checkAndInsertMilestones(current.id, days)
                    }
                }
        }
    }

    fun select(id: String) {
        selectedIdFlow.value = id
    }

    fun pledge() {
        val current = uiState.value.current ?: return
        if (uiState.value.pledgeDone) return
        viewModelScope.launch {
            try {
                pledgeRepo.pledge(current.id, todayLocal())
            } catch (_: Exception) {
                // pledge failure is silent; UI state remains unchanged
            }
        }
    }

    // Undo pattern: relapse write is committed immediately so counter/streak update visibly.
    // If the user taps "Undo" within 5s, we roll back via the snapshot. Otherwise, we
    // just clear the pending snapshot after the delay.
    private var pendingRelapseSnapshot: RelapseSnapshot? = null
    private var relapseUndoJob: Job? = null

    /**
     * Reports a relapse and returns true if the write succeeded. When true, callers
     * can offer an "Undo" action for up to 5s via [undoLastRelapse].
     */
    fun reportRelapse(
        note: String?,
        onUndoWindowOpen: () -> Unit = {},
        onBlocked: () -> Unit = {}
    ) {
        val current = uiState.value.current ?: return
        viewModelScope.launch {
            try {
                val snapshot = relapseRepo.reportRelapse(current.id, note, emptyList())
                if (snapshot != null) {
                    // Cancel any pending expiration before rebinding — avoids a stale
                    // job from a previous relapse nulling the fresh snapshot.
                    relapseUndoJob?.cancel()
                    pendingRelapseSnapshot = snapshot
                    onUndoWindowOpen()
                    relapseUndoJob = viewModelScope.launch {
                        delay(5_000)
                        // Only clear if the snapshot is still ours (defensive: another
                        // report may have replaced it in the meantime).
                        if (pendingRelapseSnapshot === snapshot) pendingRelapseSnapshot = null
                    }
                } else {
                    // null = relapse already recorded today or addiction not found
                    onBlocked()
                }
            } catch (_: Exception) {
                onBlocked()
            }
        }
    }

    fun undoLastRelapse() {
        val snapshot = pendingRelapseSnapshot ?: return
        relapseUndoJob?.cancel()
        pendingRelapseSnapshot = null
        viewModelScope.launch {
            try {
                relapseRepo.undoRelapse(snapshot)
            } catch (_: Exception) {
                // undo failure is silent
            }
        }
    }

    fun dismissMilestone(id: String) {
        viewModelScope.launch { milestoneDao.markSeen(id) }
    }

    private fun checkAndInsertMilestones(addictionId: String, days: Int) {
        viewModelScope.launch {
            Milestones.reached(days).forEach { milestone ->
                milestoneDao.insert(
                    MilestoneReachedEntity(
                        id = "${addictionId}_${milestone.days}",
                        addictionId = addictionId,
                        milestoneDays = milestone.days,
                        reachedAt = Clock.System.now(),
                        seenByUser = false
                    )
                )
            }
        }
    }

    private fun todayLocal(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
