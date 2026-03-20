package com.fitti.domain

data class Exercise(
    val id: Long,
    val code: String,
    val brand: String,
    val displayName: String,
    val muscleGroup: String,
    val currentWeight: Double,
    val weightUnit: String,
    val recordedOn: String,
    val progressionStepKg: Double = 2.5,
    val sortOrder: Int = 0
)
