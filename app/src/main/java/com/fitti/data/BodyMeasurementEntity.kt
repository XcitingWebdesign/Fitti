package com.fitti.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "body_measurements")
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measuredAt: String,
    val chestCm: Double,
    val waistCm: Double,
    val bicepsCm: Double
)

@Dao
interface BodyMeasurementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BodyMeasurementEntity): Long

    @Query("SELECT * FROM body_measurements ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): BodyMeasurementEntity?

    @Query("SELECT * FROM body_measurements ORDER BY id DESC")
    suspend fun getAll(): List<BodyMeasurementEntity>

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<BodyMeasurementEntity>)

    @Query("DELETE FROM body_measurements")
    suspend fun deleteAll()
}
