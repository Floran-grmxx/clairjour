package com.clairjour.app.data.repository

import androidx.room.withTransaction
import com.clairjour.app.data.db.AddictionDao
import com.clairjour.app.data.db.ClairjourDatabase
import com.clairjour.app.data.db.MilestoneDao
import com.clairjour.app.data.db.MilestoneReachedEntity
import com.clairjour.app.data.db.PledgeDao
import com.clairjour.app.data.db.PledgeEntity
import com.clairjour.app.data.db.RelapseDao
import com.clairjour.app.data.db.RelapseEventEntity
import com.clairjour.app.domain.Streak
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

class RelapseRepository(
    private val db: ClairjourDatabase,
    private val relapseDao: RelapseDao,
    private val milestoneDao: MilestoneDao,
    private val addictionDao: AddictionDao,
    private val pledgeDao: PledgeDao
) {
    fun observeFor(addictionId: String): Flow<List<RelapseEventEntity>> =
        relapseDao.observeFor(addictionId)

    /**
     * Reports a relapse atomically:
     *  1. inserts the RelapseEvent (with prior streak baked in)
     *  2. marks existing milestones as seen (kept as history so re-reached milestones
     *     do not pop the celebration overlay again — `insert IGNORE` will skip them)
     *  3. wipes today's pledge (a broken pledge should not linger as "kept ✓")
     *  4. resets the addiction start date to now
     *
     * All four writes happen inside a single Room transaction so downstream flows
     * emit ONCE with a fully consistent snapshot — otherwise a transient state like
     * `(startDate=old, milestones=[])` would re-insert milestones and flash celebrations.
     */
    suspend fun reportRelapse(
        addictionId: String,
        notes: String?,
        triggers: List<String>
    ): RelapseSnapshot? {
        val addiction = addictionDao.getById(addictionId) ?: return null
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today: LocalDate = now.toLocalDateTime(zone).date

        val previousStreak = Streak.daysSince(addiction.startDate, now)
        val previousStart = addiction.startDate
        val previousMilestones = milestoneDao.getFor(addictionId)
        val previousPledge = pledgeDao.getFor(addictionId, today)
        val relapseId = UUID.randomUUID().toString()

        db.withTransaction {
            relapseDao.insert(
                RelapseEventEntity(
                    id = relapseId,
                    addictionId = addictionId,
                    date = today,
                    notes = notes,
                    triggers = triggers,
                    previousStreakDays = previousStreak
                )
            )
            milestoneDao.markAllSeenFor(addictionId)
            pledgeDao.deleteFor(addictionId, today)
            addictionDao.resetStart(addictionId, now)
        }

        return RelapseSnapshot(
            relapseId = relapseId,
            addictionId = addictionId,
            previousStart = previousStart,
            previousMilestones = previousMilestones,
            previousPledge = previousPledge
        )
    }

    /**
     * Undoes a previous relapse — restores start date, milestones, and today's pledge,
     * then deletes the RelapseEvent. Also atomic to avoid intermediate emissions.
     */
    suspend fun undoRelapse(snapshot: RelapseSnapshot) {
        db.withTransaction {
            addictionDao.resetStart(snapshot.addictionId, snapshot.previousStart)
            // REPLACE (not IGNORE) so seenByUser is restored to its pre-relapse value —
            // reportRelapse now flips existing rows to seen=true, so a plain insert
            // would be ignored and leave them incorrectly marked as seen.
            milestoneDao.insertAll(snapshot.previousMilestones)
            snapshot.previousPledge?.let { pledgeDao.insert(it) }
            relapseDao.deleteById(snapshot.relapseId)
        }
    }
}

data class RelapseSnapshot(
    val relapseId: String,
    val addictionId: String,
    val previousStart: kotlinx.datetime.Instant,
    val previousMilestones: List<MilestoneReachedEntity> = emptyList(),
    val previousPledge: PledgeEntity? = null
)
