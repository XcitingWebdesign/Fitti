package com.fitti.data

import com.fitti.domain.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExerciseRepository(
    private val dao: ExerciseDao
) {
    fun observeExercises(): Flow<List<Exercise>> =
        dao.observeAll().map { list ->
            list.map { it.toDomain() }
        }

    suspend fun getById(exerciseId: Long): Exercise? =
        dao.getById(exerciseId)?.toDomain()

    suspend fun updateWeight(exerciseId: Long, newWeight: Double, date: String) {
        dao.updateWeight(exerciseId, newWeight, date)
    }

    suspend fun updateProgressionStep(exerciseId: Long, step: Double) {
        dao.updateProgressionStep(exerciseId, step)
    }

    suspend fun updateSortOrder(exerciseId: Long, sortOrder: Int) {
        dao.updateSortOrder(exerciseId, sortOrder)
    }

    suspend fun updatePositions(exerciseId: Long, seat: String, pad: String) {
        dao.updatePositions(exerciseId, seat, pad)
    }

    suspend fun addExercise(entity: ExerciseEntity): Long =
        dao.insert(entity)

    suspend fun deleteExercise(exerciseId: Long) =
        dao.deleteById(exerciseId)

    suspend fun getAllEntities(): List<ExerciseEntity> =
        dao.getAll()

    suspend fun replaceAll(entities: List<ExerciseEntity>) {
        dao.deleteAll()
        dao.insertAll(entities)
    }

    suspend fun ensureSeeded() {
        if (dao.count() > 0) return
        val nautilusStack = ExerciseEntity.NAUTILUS_WEIGHT_STACK_KG
        dao.insertAll(
            listOf(
                ExerciseEntity(code = "B2", brand = "Nautilus", displayName = "Chest Press", muscleGroup = "CHEST", currentWeight = 41.0, weightUnit = "kg", recordedOn = "22.02.2026", progressionStepKg = 5.0, sortOrder = 0, weightSteps = nautilusStack),
                ExerciseEntity(code = "B6", brand = "Nautilus", displayName = "Leg Press", muscleGroup = "LEGS", currentWeight = 160.0, weightUnit = "kg", recordedOn = "22.02.2026", progressionStepKg = 5.0, sortOrder = 1),
                ExerciseEntity(code = "C2", brand = "Nautilus", displayName = "Seated Row", muscleGroup = "BACK", currentWeight = 41.0, weightUnit = "kg", recordedOn = "22.02.2026", progressionStepKg = 5.0, sortOrder = 2, weightSteps = nautilusStack),
                ExerciseEntity(code = "C6", brand = "Nautilus", displayName = "Butterfly", muscleGroup = "CHEST", currentWeight = 41.0, weightUnit = "kg", recordedOn = "22.02.2026", progressionStepKg = 5.0, sortOrder = 3, weightSteps = nautilusStack),
                ExerciseEntity(code = "D3", brand = "Nautilus", displayName = "Shoulder Press", muscleGroup = "SHOULDERS", currentWeight = 36.0, weightUnit = "kg", recordedOn = "22.02.2026", progressionStepKg = 5.0, sortOrder = 4, weightSteps = nautilusStack),
                ExerciseEntity(code = "D4", brand = "Nautilus", displayName = "Leg Extension", muscleGroup = "LEGS", currentWeight = 60.0, weightUnit = "lb", recordedOn = "22.02.2026", progressionStepKg = 5.0, sortOrder = 5, weightSteps = ExerciseEntity.NAUTILUS_WEIGHT_STACK_LB),
                ExerciseEntity(code = "F3", brand = "Nautilus", displayName = "Lat Pulldown", muscleGroup = "BACK", currentWeight = 32.0, weightUnit = "kg", recordedOn = "22.02.2026", progressionStepKg = 5.0, sortOrder = 6, weightSteps = nautilusStack),
                ExerciseEntity(code = "F6", brand = "Nautilus", displayName = "Ab Crunch", muscleGroup = "ABS", currentWeight = 35.0, weightUnit = "kg", recordedOn = "22.02.2026", progressionStepKg = 5.0, sortOrder = 7, weightSteps = nautilusStack)
            )
        )
    }

    private fun ExerciseEntity.toDomain() = Exercise(
        id = id,
        code = code,
        brand = brand,
        displayName = displayName,
        muscleGroup = muscleGroup,
        currentWeight = currentWeight,
        weightUnit = weightUnit,
        recordedOn = recordedOn,
        progressionStepKg = progressionStepKg,
        sortOrder = sortOrder,
        seatPosition = seatPosition,
        padPosition = padPosition,
        weightSteps = weightSteps
    )
}
