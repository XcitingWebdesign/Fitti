package com.fitti.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val brand: String,
    val currentWeight: Double,
    val weightUnit: String,
    val recordedOn: String
)
