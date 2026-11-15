package io.github.ssrajadh.prep.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

// DAOs (data access objects) are interfaces; Room's annotation processor (KSP) generates
// the SQL-running implementations at build time and checks every query against the schema.

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_state")
    fun observeStates(): Flow<List<ReviewState>>

    @Query("SELECT * FROM review_state")
    suspend fun allStates(): List<ReviewState>

    @Query("SELECT * FROM review_state WHERE cardId = :cardId")
    suspend fun state(cardId: String): ReviewState?

    @Upsert
    suspend fun upsertState(state: ReviewState)

    @Query("DELETE FROM review_state WHERE cardId IN (:cardIds)")
    suspend fun deleteStates(cardIds: List<String>)

    @Insert
    suspend fun insertLog(log: ReviewLog)

    @Query("SELECT DISTINCT cardId FROM review_log")
    suspend fun seenCardIds(): List<String>

    @Query("SELECT * FROM review_log WHERE at >= :fromMillis")
    fun observeLogsSince(fromMillis: Long): Flow<List<ReviewLog>>

    @Query("SELECT * FROM review_log WHERE at >= :fromMillis")
    suspend fun logsSince(fromMillis: Long): List<ReviewLog>
}

@Dao
interface FlagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flag: CardFlag)

    @Query("SELECT * FROM card_flag ORDER BY at")
    suspend fun all(): List<CardFlag>

    @Query("SELECT COUNT(*) FROM card_flag")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM card_flag")
    suspend fun clear()
}

class Converters {
    @TypeConverter
    fun fromList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(",")
}

@Database(entities = [ReviewState::class, ReviewLog::class, CardFlag::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reviewDao(): ReviewDao
    abstract fun flagDao(): FlagDao

    suspend fun <T> transaction(block: suspend () -> T): T = withTransaction(block)

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "prep.db").build()
    }
}
