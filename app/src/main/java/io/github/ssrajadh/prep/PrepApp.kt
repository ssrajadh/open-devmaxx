package io.github.ssrajadh.prep

import android.app.Application
import io.github.ssrajadh.prep.data.content.ContentRepository
import io.github.ssrajadh.prep.data.db.AppDatabase
import io.github.ssrajadh.prep.data.settings.SettingsRepository
import io.github.ssrajadh.prep.domain.StudyRepository
import io.github.ssrajadh.prep.notify.Reminder

/**
 * The Application object lives as long as the app process. It's a simple place to hold
 * singletons (manual dependency injection; no Hilt needed at this size).
 */
class PrepApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var study: StudyRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        study = StudyRepository(ContentRepository(assets), AppDatabase.create(this), settings)
        Reminder.createChannel(this)
    }
}
