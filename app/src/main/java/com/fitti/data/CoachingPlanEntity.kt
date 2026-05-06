package com.fitti.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Entity(tableName = "coaching_plans")
data class CoachingPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: String,
    val validUntil: String,
    val rawJson: String,
    val bottleneckType: String,
    val bottleneckTarget: Double,
    val bottleneckCurrent: Double,
    val weeklyProteinG: Int,
    val weeklyKcal: Int,
    val weeklySessions: Int,
    val weeklyBodyweightKg: Double
)

@Entity(
    tableName = "coaching_plan_exercise_targets",
    foreignKeys = [
        ForeignKey(
            entity = CoachingPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["planId"])]
)
data class CoachingPlanExerciseTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val exerciseCode: String,
    val action: String,
    val targetWeightKg: Double,
    val reasonText: String,
    /** null = noch nicht entschieden, "accepted" = uebernommen, "rejected" = verworfen */
    val decision: String? = null
)

data class CoachingPlanWithTargets(
    val plan: CoachingPlanEntity,
    val targets: List<CoachingPlanExerciseTargetEntity>
) {
    fun targetFor(exerciseCode: String): CoachingPlanExerciseTargetEntity? =
        targets.firstOrNull { it.exerciseCode == exerciseCode }
}

@Dao
interface CoachingPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: CoachingPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargets(targets: List<CoachingPlanExerciseTargetEntity>)

    @Query("SELECT * FROM coaching_plans ORDER BY id DESC LIMIT 1")
    suspend fun getLatestPlan(): CoachingPlanEntity?

    @Query("SELECT * FROM coaching_plan_exercise_targets WHERE planId = :planId")
    suspend fun getTargetsForPlan(planId: Long): List<CoachingPlanExerciseTargetEntity>

    @Query("UPDATE coaching_plan_exercise_targets SET decision = :decision WHERE id = :targetId")
    suspend fun updateTargetDecision(targetId: Long, decision: String)

    @Transaction
    suspend fun savePlan(plan: CoachingPlanEntity, targets: List<CoachingPlanExerciseTargetEntity>): Long {
        val id = insertPlan(plan)
        insertTargets(targets.map { it.copy(planId = id) })
        return id
    }

    @Transaction
    suspend fun getLatest(): CoachingPlanWithTargets? {
        val plan = getLatestPlan() ?: return null
        return CoachingPlanWithTargets(plan, getTargetsForPlan(plan.id))
    }

    @Query("SELECT * FROM coaching_plans ORDER BY id DESC")
    suspend fun getAllPlans(): List<CoachingPlanEntity>

    @Query("SELECT * FROM coaching_plan_exercise_targets")
    suspend fun getAllTargets(): List<CoachingPlanExerciseTargetEntity>

    @Insert
    suspend fun insertAllPlans(plans: List<CoachingPlanEntity>)

    @Insert
    suspend fun insertAllTargets(targets: List<CoachingPlanExerciseTargetEntity>)

    @Query("DELETE FROM coaching_plan_exercise_targets")
    suspend fun deleteAllTargets()

    @Query("DELETE FROM coaching_plans")
    suspend fun deleteAllPlans()
}
