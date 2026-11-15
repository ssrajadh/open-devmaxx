package io.github.ssrajadh.prep.domain

import io.github.ssrajadh.prep.data.content.Card
import io.github.ssrajadh.prep.data.content.Content
import io.github.ssrajadh.prep.data.db.ReviewState

/**
 * Builds today's study list. Pure function, so it's unit-tested without Android.
 *
 * - Reviews: cards due today or earlier, most overdue first, limited by what's left of
 *   the daily review cap. Anything over the cap simply stays due and is picked up on
 *   following days, so a missed week never turns into a wall of cards.
 * - New cards: limited by what's left of the daily new-card cap. A card is eligible only
 *   once all its prereqs have been seen. Picks rotate across active tracks so each day
 *   is a mix, and within a track follow topic order, then difficulty.
 * - New cards are spread evenly through the reviews.
 */
object StudyQueue {
    data class Plan(val cards: List<Card>, val newIds: Set<String>, val reviewsLeftOverCap: Int)

    fun build(
        content: Content,
        states: Map<String, ReviewState>,
        seen: Set<String>,
        today: Long,
        isTrackActive: (String) -> Boolean,
        reviewsDoneToday: Int,
        newDoneToday: Int,
        reviewCap: Int,
        newPerDay: Int,
    ): Plan {
        val active = content.cards.filter { isTrackActive(it.trackId) }

        val due = active.mapNotNull { c -> states[c.id]?.takeIf { it.due <= today }?.let { c to it } }
            .sortedWith(compareBy({ it.second.due }, { it.first.id }))
            .map { it.first }
        val reviewSlots = (reviewCap - reviewsDoneToday).coerceAtLeast(0)
        val reviews = due.take(reviewSlots)

        val newSlots = (newPerDay - newDoneToday).coerceAtLeast(0)
        val fresh = pickNew(content, active, states, seen, newSlots)

        return Plan(interleave(reviews, fresh), fresh.map { it.id }.toSet(), due.size - reviews.size)
    }

    private fun pickNew(content: Content, active: List<Card>, states: Map<String, ReviewState>, seen: Set<String>, n: Int): List<Card> {
        if (n == 0) return emptyList()
        val order = active.withIndex().associate { it.value.id to it.index }
        val byTrack = active
            .filter { it.id !in states && it.prereqs.all { p -> p in seen || p !in content.cardsById } }
            .sortedWith(compareBy({ content.topicsById[it.topicId]?.order ?: Int.MAX_VALUE }, { it.difficulty }, { order[it.id] }))
            .groupBy { it.trackId }
            .values.map { ArrayDeque(it) }
        val out = mutableListOf<Card>()
        while (out.size < n && byTrack.any { it.isNotEmpty() }) {
            for (q in byTrack) {
                if (out.size >= n) break
                q.removeFirstOrNull()?.let(out::add)
            }
        }
        return out
    }

    private fun interleave(reviews: List<Card>, fresh: List<Card>): List<Card> {
        if (fresh.isEmpty()) return reviews
        if (reviews.isEmpty()) return fresh
        val out = mutableListOf<Card>()
        val step = reviews.size.toDouble() / (fresh.size + 1)
        var r = 0
        fresh.forEachIndexed { i, card ->
            val until = ((i + 1) * step).toInt()
            while (r < until) out += reviews[r++]
            out += card
        }
        while (r < reviews.size) out += reviews[r++]
        return out
    }
}
