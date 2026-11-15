package io.github.ssrajadh.prep.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Room turns each @Entity into a SQLite table. User progress only references cards by
// their string ID, so content can be fixed or extended without losing history.

/** FSRS memory state for one card. Absent row = the card is new (or was reset). */
@Entity(tableName = "review_state")
data class ReviewState(
    @PrimaryKey val cardId: String,
    val stability: Double,
    val difficulty: Double,
    /** Epoch day (days since 1970-01-01, local time) when the card is next due. */
    val due: Long,
    /** Epoch millis of the last review. */
    val lastReview: Long,
    val reps: Int,
    val lapses: Int,
    /** One of [CardState]. */
    val state: Int,
    /** Card.keyPointsHash at the time of the last review; a mismatch triggers a reset. */
    val contentHash: String,
)

object CardState {
    const val REVIEW = 2
    const val RELEARNING = 3
}

@Entity(tableName = "review_log", indices = [Index("cardId"), Index("at")])
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val at: Long,
    /** 1 = Again, 2 = Hard, 3 = Good, 4 = Easy. */
    val rating: Int,
    val keyPointsHit: List<String>,
    val cardVersion: Int,
    /** True if this was the card's first review since it was new or reset. */
    val wasNew: Boolean,
)

@Entity(tableName = "card_flag", indices = [Index("cardId")])
data class CardFlag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val cardVersion: Int,
    val reason: String,
    val note: String,
    val at: Long,
)
