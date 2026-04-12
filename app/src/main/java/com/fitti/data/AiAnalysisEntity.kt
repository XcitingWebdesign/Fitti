package com.fitti.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "ai_analyses")
data class AiAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analysisText: String,
    val createdAt: String
)

@Dao
interface AiAnalysisDao {
    @Insert
    suspend fun insert(analysis: AiAnalysisEntity): Long

    @Query("SELECT * FROM ai_analyses ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): AiAnalysisEntity?

    @Query("SELECT * FROM ai_analyses ORDER BY id DESC")
    suspend fun getAll(): List<AiAnalysisEntity>

    @Insert
    suspend fun insertAll(analyses: List<AiAnalysisEntity>)

    @Query("DELETE FROM ai_analyses")
    suspend fun deleteAll()
}
