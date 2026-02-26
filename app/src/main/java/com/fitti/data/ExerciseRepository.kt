package com.fitti.data

import com.fitti.domain.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExerciseRepository(
    private val dao: ExerciseDao
) {
    fun observeExercises(): Flow<List<Exercise>> =
        dao.observeAll().map { list ->
            list.map { entity ->
                Exercise(
                    code = entity.code,
                    brand = entity.brand,
                    currentWeight = "${entity.currentWeight} ${entity.weightUnit}",
                    recordedOn = entity.recordedOn
                )
            }
        }

    suspend fun ensureSeeded() {
        if (dao.count() > 0) return
        dao.insertAll(
            listOf(
                ExerciseEntity(code = "B2", brand = "Nautilus", currentWeight = 41.0, weightUnit = "kg", recordedOn = "22.02.2026"),
                ExerciseEntity(code = "B6", brand = "Nautilus", currentWeight = 160.0, weightUnit = "kg", recordedOn = "22.02.2026"),
                ExerciseEntity(code = "C2", brand = "Nautilus", currentWeight = 41.0, weightUnit = "kg", recordedOn = "22.02.2026"),
                ExerciseEntity(code = "C6", brand = "Nautilus", currentWeight = 41.0, weightUnit = "kg", recordedOn = "22.02.2026"),
                ExerciseEntity(code = "D3", brand = "Nautilus", currentWeight = 36.0, weightUnit = "kg", recordedOn = "22.02.2026"),
                ExerciseEntity(code = "D4", brand = "Nautilus", currentWeight = 60.0, weightUnit = "lb", recordedOn = "22.02.2026"),
                ExerciseEntity(code = "F3", brand = "Nautilus", currentWeight = 32.0, weightUnit = "kg", recordedOn = "22.02.2026")
            )
        )
    }
}
