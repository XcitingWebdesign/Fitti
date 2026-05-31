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

    @Query("SELECT * FROM exercises ORDER BY sortOrder ASC")
    suspend fun getAll(): List<ExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ExerciseEntity): Long

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

    @Query("UPDATE exercises SET seatPosition = :seat, padPosition = :pad WHERE id = :exerciseId")
    suspend fun updatePositions(exerciseId: Long, seat: String, pad: String)

    @Query("UPDATE exercises SET weightSteps = :steps WHERE id = :exerciseId")
    suspend fun updateWeightSteps(exerciseId: Long, steps: String)

    @Query("UPDATE exercises SET muscleGroup = :group WHERE id = :exerciseId")
    suspend fun updateMuscleGroup(exerciseId: Long, group: String)

    @Query("DELETE FROM exercises WHERE id = :exerciseId")
    suspend fun deleteById(exerciseId: Long)

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
}
