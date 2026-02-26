package com.fitti.data

import androidx.room.Embedded
import androidx.room.Relation

data class SessionExerciseWithSetLogs(
    @Embedded val sessionExercise: SessionExerciseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionExerciseId"
    )
    val setLogs: List<SetLogEntity>
)

data class WorkoutSessionHistory(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
        entity = SessionExerciseEntity::class
    )
    val sessionExercises: List<SessionExerciseWithSetLogs>
)
