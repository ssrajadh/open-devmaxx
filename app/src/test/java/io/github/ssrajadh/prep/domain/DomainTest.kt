package io.github.ssrajadh.prep.domain

import io.github.ssrajadh.prep.data.content.Card
import io.github.ssrajadh.prep.data.content.Content
import io.github.ssrajadh.prep.data.content.KeyPoint
import io.github.ssrajadh.prep.data.content.Source
import io.github.ssrajadh.prep.data.content.Topic
import io.github.ssrajadh.prep.data.content.Track
import io.github.ssrajadh.prep.data.content.Verified
import io.github.ssrajadh.prep.data.db.CardState
import io.github.ssrajadh.prep.data.db.ReviewState
import io.github.ssrajadh.prep.domain.Fsrs.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsTest {
    @Test fun intervalEqualsStabilityAtNinetyPercent() {
        assertEquals(10L, Fsrs.intervalDays(10.0))
        assertEquals(0.9, Fsrs.retrievability(10.0, 10.0), 1e-9)
    }

    @Test fun firstReviewUsesInitialStability() {
        assertEquals(1L, Fsrs.first(Rating.Again).intervalDays)
        assertEquals(3L, Fsrs.first(Rating.Good).intervalDays) // w2 = 3.173
        assertEquals(16L, Fsrs.first(Rating.Easy).intervalDays) // w3 = 15.69
    }

    @Test fun betterRatingsGiveLongerIntervals() {
        val m = Fsrs.first(Rating.Good).memory
        val hard = Fsrs.next(m, 3, Rating.Hard).intervalDays
        val good = Fsrs.next(m, 3, Rating.Good).intervalDays
        val easy = Fsrs.next(m, 3, Rating.Easy).intervalDays
        assertTrue("$hard < $good < $easy", hard < good && good < easy)
        assertTrue(good > 3)
    }

    @Test fun lapseShrinksStabilityAndRaisesDifficulty() {
        val m = Fsrs.next(Fsrs.first(Rating.Good).memory, 3, Rating.Good).memory
        val r = Fsrs.next(m, 10, Rating.Again)
        assertTrue(r.memory.stability < m.stability)
        assertTrue(r.memory.difficulty > m.difficulty)
        assertEquals(1L, r.intervalDays)
        assertTrue(r.lapsed)
    }

    @Test fun difficultyStaysInRange() {
        var m = Fsrs.first(Rating.Again).memory
        repeat(30) { m = Fsrs.next(m, 1, Rating.Again).memory }
        assertTrue(m.difficulty <= 10.0)
        repeat(30) { m = Fsrs.next(m, 5, Rating.Easy).memory }
        assertTrue(m.difficulty >= 1.0)
    }
}

class GradingTest {
    private fun g(hit: Int, total: Int) = Grading.suggest(hit, total, 0.4f, 0.7f)

    @Test fun thresholds() {
        assertEquals(Rating.Again, g(0, 5))
        assertEquals(Rating.Again, g(1, 5)) // 20%
        assertEquals(Rating.Hard, g(2, 5)) // 40%
        assertEquals(Rating.Hard, g(3, 5)) // 60%
        assertEquals(Rating.Good, g(4, 5)) // 80%
        assertEquals(Rating.Easy, g(5, 5))
    }
}

class StudyQueueTest {
    private fun card(id: String, track: String, topic: String, prereqs: List<String> = emptyList(), difficulty: Int = 1) = Card(
        id = id, trackId = track, topicId = topic, kind = "concept", difficulty = difficulty, prompt = id,
        keyPoints = listOf(KeyPoint("kp1", "x")), explanation = "", sources = listOf(Source("s", "https://x", "s")),
        prereqs = prereqs, version = 1, verified = Verified("verified", "2026-11-15"),
    )

    private val content = Content(
        tracks = listOf(Track("a", "A", ""), Track("b", "B", "")),
        topics = listOf(Topic("a1", "a", "A1", 1), Topic("a2", "a", "A2", 2), Topic("b1", "b", "B1", 1)),
        cards = listOf(
            card("a-2", "a", "a2"),
            card("a-1", "a", "a1"),
            card("a-1b", "a", "a1", prereqs = listOf("a-1")),
            card("b-1", "b", "b1"),
            card("b-2", "b", "b1"),
        ),
    )

    private fun state(id: String, due: Long) = ReviewState(id, 5.0, 5.0, due, 0, 1, 0, CardState.REVIEW, "")

    private fun build(states: Map<String, ReviewState> = emptyMap(), seen: Set<String> = emptySet(), reviewCap: Int = 10, newPerDay: Int = 10, reviewsDone: Int = 0, newDone: Int = 0, active: (String) -> Boolean = { true }) =
        StudyQueue.build(content, states, seen, today = 100, isTrackActive = active, reviewsDoneToday = reviewsDone, newDoneToday = newDone, reviewCap = reviewCap, newPerDay = newPerDay)

    @Test fun newCardsRespectPrereqsAndRotateTracks() {
        val plan = build(newPerDay = 4)
        // a-1b is locked until a-1 is seen; topic order puts a-1 before a-2; tracks alternate.
        assertEquals(listOf("a-1", "b-1", "a-2", "b-2"), plan.cards.map { it.id })
    }

    @Test fun prereqUnlocksOnceSeen() {
        val plan = build(states = mapOf("a-1" to state("a-1", 200)), seen = setOf("a-1"), newPerDay = 10)
        assertTrue("a-1b" in plan.newIds)
    }

    @Test fun reviewCapSpreadsBacklog() {
        val states = content.cards.associate { it.id to state(it.id, 90L + it.id.length) }
        val plan = build(states = states, reviewCap = 2)
        assertEquals(2, plan.cards.size)
        assertEquals(3, plan.reviewsLeftOverCap)
        val later = build(states = states, reviewCap = 2, reviewsDone = 2)
        assertEquals(0, later.cards.size)
    }

    @Test fun newCapCountsWhatWasDoneToday() {
        assertEquals(1, build(newPerDay = 3, newDone = 2).cards.size)
    }

    @Test fun inactiveTracksAreSkipped() {
        val plan = build(active = { it == "b" })
        assertTrue(plan.cards.all { it.trackId == "b" })
    }

    @Test fun newCardsAreSpreadThroughReviews() {
        val states = mapOf("b-1" to state("b-1", 99), "b-2" to state("b-2", 98), "a-2" to state("a-2", 97), "a-1b" to state("a-1b", 96))
        val plan = build(states = states, seen = states.keys, newPerDay = 1)
        assertEquals(5, plan.cards.size)
        val newIndex = plan.cards.indexOfFirst { it.id in plan.newIds }
        assertTrue("new card at $newIndex", newIndex in 1..3)
    }
}
