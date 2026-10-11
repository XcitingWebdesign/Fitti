package com.fitti.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Protokoll jedes Claude-Calls (System-Prompt, User-Nachricht, Antwort),
 * damit Prompts spaeter anhand echter Konversationen verbessert werden koennen.
 */
@Entity(tableName = "ai_call_logs")
data class AiCallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: String,
    val kind: String,
    val model: String,
    val systemPrompt: String,
    val userMessage: String,
    val responseText: String?,
    val errorText: String?,
    val inputTokens: Int,
    val outputTokens: Int,
    val durationMs: Long
) {
    companion object {
        const val KIND_WORKOUT_FEEDBACK = "WORKOUT_FEEDBACK"
        const val KIND_WEEKLY_ANALYSIS = "WEEKLY_ANALYSIS"
        const val KIND_WEEKLY_COACHING = "WEEKLY_COACHING"
        const val KIND_STRENGTH_TARGETS = "STRENGTH_TARGETS"
        const val KIND_PROTEIN_TEXT = "PROTEIN_TEXT"
        const val KIND_PROTEIN_IMAGE = "PROTEIN_IMAGE"
    }
}

@Dao
interface AiCallLogDao {
    @Insert
    suspend fun insert(log: AiCallLogEntity): Long

    @Query("SELECT * FROM ai_call_logs ORDER BY id ASC")
    suspend fun getAll(): List<AiCallLogEntity>

    @Insert
    suspend fun insertAll(logs: List<AiCallLogEntity>)

    @Query("DELETE FROM ai_call_logs")
    suspend fun deleteAll()
}
