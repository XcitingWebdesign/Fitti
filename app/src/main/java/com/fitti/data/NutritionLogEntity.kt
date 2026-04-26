package com.fitti.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "nutrition_logs",
    indices = [Index(value = ["date"], unique = true)]
)
data class NutritionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val proteinHit: Boolean,
    val weightKg: Double?
)

@Dao
interface NutritionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: NutritionLogEntity): Long

    @Query("SELECT * FROM nutrition_logs WHERE date = :date LIMIT 1")
    suspend fun getForDate(date: String): NutritionLogEntity?

    @Query("SELECT * FROM nutrition_logs ORDER BY date DESC")
    suspend fun getAll(): List<NutritionLogEntity>

    @Query("SELECT * FROM nutrition_logs WHERE date >= :sinceDate ORDER BY date ASC")
    suspend fun getSince(sinceDate: String): List<NutritionLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<NutritionLogEntity>)

    @Query("DELETE FROM nutrition_logs")
    suspend fun deleteAll()
}
