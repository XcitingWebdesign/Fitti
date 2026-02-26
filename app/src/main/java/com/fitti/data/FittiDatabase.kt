package com.fitti.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ExerciseEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FittiDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        fun create(context: Context): FittiDatabase =
            Room.databaseBuilder(context, FittiDatabase::class.java, "fitti.db")
                .build()
    }
}
