package com.fitti.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "weight_logs")
data class WeightLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weightKg: Double,
    val loggedAt: String
)

@Dao
interface WeightLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: WeightLogEntity): Long

    @Query("SELECT * FROM weight_logs ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): WeightLogEntity?

    @Query("SELECT * FROM weight_logs ORDER BY id DESC")
    suspend fun getAll(): List<WeightLogEntity>
}
