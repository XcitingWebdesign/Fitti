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
        /** Nautilus Inspiration standard weight stack (kg) — 20 lbs Top-Plate + 10 lbs Platten */
        const val NAUTILUS_WEIGHT_STACK_KG = "9,14,18,23,27,32,36,41,46,50,55,59,64,68,73,77,82,86,91,96,100,105,109"
        /** Nautilus Inspiration standard weight stack (lb) */
        const val NAUTILUS_WEIGHT_STACK_LB = "20,30,40,50,60,70,80,90,100,110,120,130,140,150,160,170,180,190,200,210,220,230,240"
        /** Nautilus Inspiration heavy-machine stack (kg) — 40 lbs Top-Plate + 10 lbs Platten */
        const val NAUTILUS_HEAVY_STACK_KG =
            "18,23,27,32,36,41,46,50,55,59,64,68,73,77,82,86,91,96,100,105,109," +
            "114,118,123,127,132,136,141,145,150,154,159,163,168,172,177,181"
    }
}
