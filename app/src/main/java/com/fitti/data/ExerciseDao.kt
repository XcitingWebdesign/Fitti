package com.fitti.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExerciseEntity>)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("UPDATE exercises SET currentWeight = :newWeight, recordedOn = :date WHERE id = :exerciseId")
    suspend fun updateWeight(exerciseId: Long, newWeight: Double, date: String)

    @Query("SELECT * FROM exercises WHERE id = :exerciseId")
    suspend fun getById(exerciseId: Long): ExerciseEntity?

    @Query("UPDATE exercises SET progressionStepKg = :step WHERE id = :exerciseId")
    suspend fun updateProgressionStep(exerciseId: Long, step: Double)

    @Query("UPDATE exercises SET sortOrder = :sortOrder WHERE id = :exerciseId")
    suspend fun updateSortOrder(exerciseId: Long, sortOrder: Int)
}
