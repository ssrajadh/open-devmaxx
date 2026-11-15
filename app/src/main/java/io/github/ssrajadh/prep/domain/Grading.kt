package io.github.ssrajadh.prep.domain

import io.github.ssrajadh.prep.domain.Fsrs.Rating

/**
 * Maps "how many key points did I cover" to a suggested FSRS rating.
 * The learner can always override the suggestion.
 */
object Grading {
    fun suggest(hit: Int, total: Int, againBelow: Float, hardBelow: Float): Rating {
        if (total <= 0) return Rating.Good
        val f = hit.toFloat() / total
        return when {
            hit == total -> Rating.Easy
            f < againBelow -> Rating.Again
            f < hardBelow -> Rating.Hard
            else -> Rating.Good
        }
    }
}
