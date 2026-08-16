package com.fitti.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Eine geloggte Mahlzeit mit geschaetztem/eingegebenem Proteingehalt.
 * Die Tagessumme wird gegen das Protein-Ziel gehalten und leitet den
 * bisherigen [NutritionLogEntity.proteinHit]-Boolean automatisch ab.
 */
@Entity(
    tableName = "meal_logs",
    indices = [Index(value = ["date"])]
)
data class MealLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timestamp: Long,
    val description: String,
    val proteinGrams: Double,
    val source: String
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_AI_TEXT = "ai_text"
        const val SOURCE_AI_PHOTO = "ai_photo"
    }
}

@Dao
interface MealLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meal: MealLogEntity): Long

    @Delete
    suspend fun delete(meal: MealLogEntity)

    @Query("SELECT * FROM meal_logs WHERE date = :date ORDER BY timestamp ASC")
    suspend fun getForDate(date: String): List<MealLogEntity>

    @Query("SELECT * FROM meal_logs WHERE date >= :sinceDate ORDER BY timestamp ASC")
    suspend fun getSince(sinceDate: String): List<MealLogEntity>

    // Letzte unterschiedliche Mahlzeiten fuer die Schnell-Wiederholung
    // (haeufige Mahlzeiten ohne erneuten API-Call wieder loggen).
    @Query(
        "SELECT * FROM meal_logs GROUP BY description ORDER BY MAX(timestamp) DESC LIMIT :limit"
    )
    suspend fun getRecentDistinct(limit: Int): List<MealLogEntity>

    @Query("SELECT * FROM meal_logs ORDER BY timestamp ASC")
    suspend fun getAll(): List<MealLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(meals: List<MealLogEntity>)

    @Query("DELETE FROM meal_logs")
    suspend fun deleteAll()
}
