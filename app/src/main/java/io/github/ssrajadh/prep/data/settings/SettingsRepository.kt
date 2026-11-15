package io.github.ssrajadh.prep.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore is the modern replacement for SharedPreferences: an async, Flow-based
// key-value file. The `preferencesDataStore` delegate gives one instance per process.
private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    /** Empty means "all tracks" (so new tracks are on by default). */
    val activeTracks: Set<String> = emptySet(),
    val newPerDay: Int = 5,
    val reviewCap: Int = 30,
    val notifyEnabled: Boolean = false,
    val notifyHour: Int = 19,
    val notifyMinute: Int = 0,
    /** Fraction of key points hit below which the suggested rating is Again. */
    val againBelow: Float = 0.4f,
    /** Below this (and at least againBelow) the suggestion is Hard; otherwise Good, all = Easy. */
    val hardBelow: Float = 0.7f,
) {
    fun isTrackActive(trackId: String) = activeTracks.isEmpty() || trackId in activeTracks
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val activeTracks = stringSetPreferencesKey("active_tracks")
        val newPerDay = intPreferencesKey("new_per_day")
        val reviewCap = intPreferencesKey("review_cap")
        val notifyEnabled = booleanPreferencesKey("notify_enabled")
        val notifyHour = intPreferencesKey("notify_hour")
        val notifyMinute = intPreferencesKey("notify_minute")
        val againBelow = floatPreferencesKey("again_below")
        val hardBelow = floatPreferencesKey("hard_below")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            activeTracks = this[Keys.activeTracks] ?: d.activeTracks,
            newPerDay = this[Keys.newPerDay] ?: d.newPerDay,
            reviewCap = this[Keys.reviewCap] ?: d.reviewCap,
            notifyEnabled = this[Keys.notifyEnabled] ?: d.notifyEnabled,
            notifyHour = this[Keys.notifyHour] ?: d.notifyHour,
            notifyMinute = this[Keys.notifyMinute] ?: d.notifyMinute,
            againBelow = this[Keys.againBelow] ?: d.againBelow,
            hardBelow = this[Keys.hardBelow] ?: d.hardBelow,
        )
    }

    suspend fun setActiveTracks(ids: Set<String>) = context.dataStore.edit { it[Keys.activeTracks] = ids }
    suspend fun setNewPerDay(n: Int) = context.dataStore.edit { it[Keys.newPerDay] = n }
    suspend fun setReviewCap(n: Int) = context.dataStore.edit { it[Keys.reviewCap] = n }
    suspend fun setNotify(enabled: Boolean, hour: Int, minute: Int) = context.dataStore.edit {
        it[Keys.notifyEnabled] = enabled
        it[Keys.notifyHour] = hour
        it[Keys.notifyMinute] = minute
    }
    suspend fun setThresholds(againBelow: Float, hardBelow: Float) = context.dataStore.edit {
        it[Keys.againBelow] = againBelow
        it[Keys.hardBelow] = maxOf(hardBelow, againBelow)
    }
}
