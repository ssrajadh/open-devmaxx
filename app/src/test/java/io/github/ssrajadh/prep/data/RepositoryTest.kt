package io.github.ssrajadh.prep.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.ssrajadh.prep.PrepApp
import io.github.ssrajadh.prep.data.content.ContentRepository
import io.github.ssrajadh.prep.data.db.AppDatabase
import io.github.ssrajadh.prep.data.settings.SettingsRepository
import io.github.ssrajadh.prep.domain.Fsrs
import io.github.ssrajadh.prep.domain.StudyRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** StudyRepository against a real (in-memory) Room database and the bundled content. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private val app = ApplicationProvider.getApplicationContext<PrepApp>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val study = StudyRepository(ContentRepository(app.assets), db, SettingsRepository(app))

    @Test fun reviewMovesCardOutOfTodayAndRespectsCaps() = runBlocking {
        val before = study.todayInfo()
        assertEquals(5, before.plan.newIds.size) // default new-per-day cap
        val first = before.plan.cards.first()
        study.recordReview(first, first.keyPoints.map { it.id }, Fsrs.Rating.Good)

        val after = study.todayInfo()
        assertTrue(first.id !in after.plan.cards.map { it.id })
        assertEquals(4, after.plan.newIds.size) // one of today's new slots used
        assertEquals(1, after.doneToday)
        val state = db.reviewDao().state(first.id)!!
        assertTrue(state.due > study.today())
    }

    @Test fun changedKeyPointsResetScheduling() = runBlocking {
        val card = study.todayInfo().plan.cards.first()
        study.recordReview(card, emptyList(), Fsrs.Rating.Good)
        db.reviewDao().upsertState(db.reviewDao().state(card.id)!!.copy(contentHash = "old"))
        study.syncContent()
        assertEquals(null, db.reviewDao().state(card.id))
        assertTrue(card.id in db.reviewDao().seenCardIds()) // history kept
    }

    @Test fun flagsExportAsJson() = runBlocking {
        val card = study.todayInfo().plan.cards.first()
        study.flag(card, "Unclear", "kp3 is vague")
        val json = study.exportFlagsJson()
        assertTrue(json.contains(card.id) && json.contains("kp3 is vague"))
    }
}
