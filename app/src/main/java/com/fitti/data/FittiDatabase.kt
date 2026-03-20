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
        SetLogEntity::class,
        WeightLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class FittiDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun weightLogDao(): WeightLogDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add displayName and muscleGroup to exercises
                db.execSQL("ALTER TABLE exercises ADD COLUMN displayName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercises ADD COLUMN muscleGroup TEXT NOT NULL DEFAULT ''")

                // Backfill seed data
                db.execSQL("UPDATE exercises SET displayName = 'Chest Press', muscleGroup = 'CHEST' WHERE code = 'B2'")
                db.execSQL("UPDATE exercises SET displayName = 'Leg Press', muscleGroup = 'LEGS' WHERE code = 'B6'")
                db.execSQL("UPDATE exercises SET displayName = 'Seated Row', muscleGroup = 'BACK' WHERE code = 'C2'")
                db.execSQL("UPDATE exercises SET displayName = 'Butterfly', muscleGroup = 'CHEST' WHERE code = 'C6'")
                db.execSQL("UPDATE exercises SET displayName = 'Shoulder Press', muscleGroup = 'SHOULDERS' WHERE code = 'D3'")
                db.execSQL("UPDATE exercises SET displayName = 'Leg Extension', muscleGroup = 'LEGS' WHERE code = 'D4'")
                db.execSQL("UPDATE exercises SET displayName = 'Lat Pulldown', muscleGroup = 'BACK' WHERE code = 'F3'")

                // Add snapshot fields to session_exercises
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN exerciseDisplayName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN exerciseMuscleGroup TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN targetRepsMin INTEGER NOT NULL DEFAULT 8")

                // Create weight_logs table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `weight_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `weightKg` REAL NOT NULL,
                        `loggedAt` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Per-exercise progression step
                db.execSQL("ALTER TABLE exercises ADD COLUMN progressionStepKg REAL NOT NULL DEFAULT 2.5")
                // Custom exercise order
                db.execSQL("ALTER TABLE exercises ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")

                // Backfill sortOrder based on current code order
                db.execSQL("UPDATE exercises SET sortOrder = 0 WHERE code = 'B2'")
                db.execSQL("UPDATE exercises SET sortOrder = 1 WHERE code = 'B6'")
                db.execSQL("UPDATE exercises SET sortOrder = 2 WHERE code = 'C2'")
                db.execSQL("UPDATE exercises SET sortOrder = 3 WHERE code = 'C6'")
                db.execSQL("UPDATE exercises SET sortOrder = 4 WHERE code = 'D3'")
                db.execSQL("UPDATE exercises SET sortOrder = 5 WHERE code = 'D4'")
                db.execSQL("UPDATE exercises SET sortOrder = 6 WHERE code = 'F3'")
            }
        }

        fun create(context: Context): FittiDatabase =
            Room.databaseBuilder(context, FittiDatabase::class.java, "fitti.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
