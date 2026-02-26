package com.fitti.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class FittiDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workout_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `startedAt` TEXT NOT NULL,
                        `completedAt` TEXT,
                        `status` TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `session_exercises` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `exerciseId` INTEGER NOT NULL,
                        `exerciseCode` TEXT NOT NULL,
                        `targetWeight` REAL NOT NULL,
                        `targetReps` INTEGER NOT NULL,
                        `targetSets` INTEGER NOT NULL,
                        `plannedRestSeconds` INTEGER NOT NULL,
                        `progressionStepKg` REAL NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercises_sessionId` ON `session_exercises` (`sessionId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `set_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionExerciseId` INTEGER NOT NULL,
                        `setNumber` INTEGER NOT NULL,
                        `actualWeightKg` REAL NOT NULL,
                        `actualReps` INTEGER NOT NULL,
                        `completedFlag` INTEGER NOT NULL,
                        FOREIGN KEY(`sessionExerciseId`) REFERENCES `session_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_logs_sessionExerciseId` ON `set_logs` (`sessionExerciseId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_set_logs_sessionExerciseId_setNumber` ON `set_logs` (`sessionExerciseId`, `setNumber`)")
            }
        }

        fun create(context: Context): FittiDatabase =
            Room.databaseBuilder(context, FittiDatabase::class.java, "fitti.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
