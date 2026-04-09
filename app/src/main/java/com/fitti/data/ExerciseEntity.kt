package com.fitti.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val brand: String,
    val displayName: String = "",
    val muscleGroup: String = "",
    val currentWeight: Double,
    val weightUnit: String,
    val recordedOn: String,
    val progressionStepKg: Double = 2.5,
    val sortOrder: Int = 0,
    val seatPosition: String = "",
    val padPosition: String = "",
    val weightSteps: String = ""
) {
    companion object {
        /** Nautilus Inspiration standard weight stack (kg) */
        const val NAUTILUS_WEIGHT_STACK_KG = "9,14,18,23,27,32,36,41,46,50,55,59,64,68,73,77,82,86,91"
    }
}
