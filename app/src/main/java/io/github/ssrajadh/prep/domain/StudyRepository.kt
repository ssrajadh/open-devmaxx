package io.github.ssrajadh.prep.domain

import io.github.ssrajadh.prep.data.content.Card
import io.github.ssrajadh.prep.data.content.Content
import io.github.ssrajadh.prep.data.content.ContentRepository
import io.github.ssrajadh.prep.data.db.AppDatabase
import io.github.ssrajadh.prep.data.db.CardFlag
import io.github.ssrajadh.prep.data.db.CardState
import io.github.ssrajadh.prep.data.db.ReviewLog
import io.github.ssrajadh.prep.data.db.ReviewState
import io.github.ssrajadh.prep.data.settings.SettingsRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Ties content, progress, and settings together. Screens talk to this, not to Room directly. */
class StudyRepository(
    private val contentRepo: ContentRepository,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val dao = db.reviewDao()
    private val zone: ZoneId get() = clock.zone

    fun today(): Long = LocalDate.now(clock).toEpochDay()
    private fun startOfTodayMillis(): Long = LocalDate.now(clock).atStartOfDay(zone).toInstant().toEpochMilli()

    suspend fun content(): Content = contentRepo.content()

    /**
     * Run on startup. If a card's key points changed since it was last reviewed, its
     * scheduling resets (it comes back as a new card). History in review_log is kept.
     */
    suspend fun syncContent() {
        val content = content()
        val stale = dao.allStates().filter { s ->
            val card = content.cardsById[s.cardId]
            card != null && card.keyPointsHash != s.contentHash
        }
        if (stale.isNotEmpty()) dao.deleteStates(stale.map { it.cardId })
    }

    data class TodayInfo(val plan: StudyQueue.Plan, val doneToday: Int)

    suspend fun todayInfo(): TodayInfo {
        val content = content()
        val s = settings.current()
        val states = dao.allStates().associateBy { it.cardId }
        val logs = dao.logsSince(startOfTodayMillis())
        val newDone = logs.filter { it.wasNew }.map { it.cardId }.distinct().size
        val reviewsDone = logs.filter { !it.wasNew }.map { it.cardId }.distinct().size
        val plan = StudyQueue.build(
            content = content,
            states = states,
            seen = dao.seenCardIds().toSet(),
            today = today(),
            isTrackActive = s::isTrackActive,
            reviewsDoneToday = reviewsDone,
            newDoneToday = newDone,
            reviewCap = s.reviewCap,
            newPerDay = s.newPerDay,
        )
        return TodayInfo(plan, logs.map { it.cardId }.distinct().size)
    }

    suspend fun recordReview(card: Card, keyPointsHit: List<String>, rating: Fsrs.Rating) {
        val now = Instant.now(clock).toEpochMilli()
        val today = today()
        db.transaction {
            val prev = dao.state(card.id)
            val result = if (prev == null) {
                Fsrs.first(rating)
            } else {
                val lastDay = Instant.ofEpochMilli(prev.lastReview).atZone(zone).toLocalDate().toEpochDay()
                Fsrs.next(Fsrs.Memory(prev.stability, prev.difficulty), today - lastDay, rating)
            }
            dao.upsertState(
                ReviewState(
                    cardId = card.id,
                    stability = result.memory.stability,
                    difficulty = result.memory.difficulty,
                    due = today + result.intervalDays,
                    lastReview = now,
                    reps = (prev?.reps ?: 0) + 1,
                    lapses = (prev?.lapses ?: 0) + if (result.lapsed) 1 else 0,
                    state = if (rating == Fsrs.Rating.Again) CardState.RELEARNING else CardState.REVIEW,
                    contentHash = card.keyPointsHash,
                )
            )
            dao.insertLog(ReviewLog(cardId = card.id, at = now, rating = rating.value, keyPointsHit = keyPointsHit, cardVersion = card.version, wasNew = prev == null))
        }
    }

    suspend fun flag(card: Card, reason: String, note: String) {
        db.flagDao().insert(CardFlag(cardId = card.id, cardVersion = card.version, reason = reason, note = note, at = Instant.now(clock).toEpochMilli()))
    }

    fun observeFlagCount() = db.flagDao().observeCount()

    @Serializable
    private data class FlagExport(val cardId: String, val cardVersion: Int, val prompt: String?, val reason: String, val note: String, val at: String)

    @Serializable
    private data class FlagExportFile(val exportedAt: String, val flags: List<FlagExport>)

    /** JSON for the content pipeline: re-run the checker on these card IDs. */
    suspend fun exportFlagsJson(): String {
        val content = content()
        val flags = db.flagDao().all().map {
            FlagExport(it.cardId, it.cardVersion, content.cardsById[it.cardId]?.prompt, it.reason, it.note, Instant.ofEpochMilli(it.at).toString())
        }
        return Json { prettyPrint = true }.encodeToString(FlagExportFile(Instant.now(clock).toString(), flags))
    }

    suspend fun clearFlags() = db.flagDao().clear()

    fun observeStates() = dao.observeStates()

    /** For the reminder: how many cards would today's session contain? */
    suspend fun cardsReadyToday(): Int = todayInfo().plan.cards.size
}
