package io.github.ssrajadh.prep.domain

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * FSRS-5 (Free Spaced Repetition Scheduler) with the default published weights.
 *
 * The model tracks two numbers per card:
 * - stability S: days until recall probability drops to 90%
 * - difficulty D: 1 (easy) to 10 (hard), how much a card resists getting more stable
 *
 * Retrievability R(t) = (1 + FACTOR * t / S) ^ DECAY is the predicted chance you still
 * remember the card after t days. With the 0.9 desired retention used here, the next
 * interval in days equals S (rounded, at least 1 day).
 *
 * This app schedules in whole days only: there are no minute-level learning steps,
 * which keeps it calm and fits a ~10 min/day routine.
 */
object Fsrs {
    enum class Rating(val value: Int) { Again(1), Hard(2), Good(3), Easy(4);
        companion object { fun of(v: Int) = entries.first { it.value == v } }
    }

    data class Memory(val stability: Double, val difficulty: Double)

    data class Result(val memory: Memory, val intervalDays: Long, val lapsed: Boolean)

    private val W = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
        1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621,
    )
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    const val DESIRED_RETENTION = 0.9
    const val MAX_INTERVAL_DAYS = 3650L

    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1 + FACTOR * elapsedDays / stability).pow(DECAY)

    fun intervalDays(stability: Double, retention: Double = DESIRED_RETENTION): Long {
        val raw = stability / FACTOR * (retention.pow(1 / DECAY) - 1)
        return raw.roundToLong().coerceIn(1, MAX_INTERVAL_DAYS)
    }

    private fun initStability(r: Rating) = max(W[r.value - 1], 0.1)

    private fun initDifficulty(r: Rating) = (W[4] - exp(W[5] * (r.value - 1)) + 1).coerceIn(1.0, 10.0)

    private fun nextDifficulty(d: Double, r: Rating): Double {
        val delta = -W[6] * (r.value - 3)
        val damped = d + delta * (10 - d) / 9 // linear damping: changes shrink near 10
        val reverted = W[7] * initDifficulty(Rating.Easy) + (1 - W[7]) * damped // mean reversion
        return reverted.coerceIn(1.0, 10.0)
    }

    private fun recallStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.Hard) W[15] else 1.0
        val easyBonus = if (rating == Rating.Easy) W[16] else 1.0
        return s * (1 + exp(W[8]) * (11 - d) * s.pow(-W[9]) * (exp((1 - r) * W[10]) - 1) * hardPenalty * easyBonus)
    }

    private fun forgetStability(d: Double, s: Double, r: Double): Double {
        val sf = W[11] * d.pow(-W[12]) * ((s + 1).pow(W[13]) - 1) * exp((1 - r) * W[14])
        return min(sf, s) // forgetting never makes a card more stable than before
    }

    /** Same-day re-review: stability moves a little based on the rating. */
    private fun shortTermStability(s: Double, rating: Rating): Double =
        s * exp(W[17] * (rating.value - 3 + W[18]))

    /** First review of a new (or reset) card. */
    fun first(rating: Rating): Result {
        val m = Memory(initStability(rating), initDifficulty(rating))
        return Result(m, if (rating == Rating.Again) 1 else intervalDays(m.stability), lapsed = false)
    }

    /** A later review, [elapsedDays] after the previous one. */
    fun next(prev: Memory, elapsedDays: Long, rating: Rating): Result {
        val d = nextDifficulty(prev.difficulty, rating)
        val s = when {
            elapsedDays <= 0 -> shortTermStability(prev.stability, rating)
            rating == Rating.Again -> forgetStability(prev.difficulty, prev.stability, retrievability(elapsedDays.toDouble(), prev.stability))
            else -> recallStability(prev.difficulty, prev.stability, retrievability(elapsedDays.toDouble(), prev.stability), rating)
        }.coerceIn(0.1, 36500.0)
        val interval = if (rating == Rating.Again) 1 else intervalDays(s)
        return Result(Memory(s, d), interval, lapsed = rating == Rating.Again)
    }
}
