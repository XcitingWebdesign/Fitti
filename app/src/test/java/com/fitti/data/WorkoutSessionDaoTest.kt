package com.fitti.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutSessionDaoTest {

    private lateinit var database: FittiDatabase
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var workoutSessionDao: WorkoutSessionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FittiDatabase::class.java
        ).allowMainThreadQueries().build()

        exerciseDao = database.exerciseDao()
        workoutSessionDao = database.workoutSessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun startTestSession(startedAt: String): Long {
        return workoutSessionDao.startSession(
            startedAt = startedAt,
            repsMin = 8,
            repsMax = 12,
            sets = 2,
            restSeconds = 90
        )
    }

    @Test
    fun startSession_createsExactlyOneSnapshotPerActiveExercise() = runBlocking {
        exerciseDao.insertAll(
            listOf(
                ExerciseEntity(code = "A1", brand = "Nautilus", currentWeight = 50.0, weightUnit = "kg", recordedOn = "01.01.2026"),
                ExerciseEntity(code = "A2", brand = "Nautilus", currentWeight = 60.0, weightUnit = "kg", recordedOn = "01.01.2026")
            )
        )

        val sessionId = startTestSession("2026-02-26T10:00:00")

        val snapshots = workoutSessionDao.getSessionExercises(sessionId)
        assertEquals(2, snapshots.size)
        assertEquals(setOf("A1", "A2"), snapshots.map { it.exerciseCode }.toSet())
    }

    @Test
    fun saveSet_requiresSequentialUniqueSetNumberPerSessionExercise() = runBlocking {
        exerciseDao.insertAll(
            listOf(
                ExerciseEntity(code = "B1", brand = "Nautilus", currentWeight = 40.0, weightUnit = "kg", recordedOn = "01.01.2026")
            )
        )
        val sessionId = startTestSession("2026-02-26T11:00:00")
        val sessionExerciseId = workoutSessionDao.getSessionExercises(sessionId).first().id

        workoutSessionDao.saveSet(
            sessionExerciseId = sessionExerciseId,
            setNumber = 1,
            actualWeightKg = 40.0,
            actualReps = 10,
            completedFlag = true,
            now = "26.02.2026 11:01"
        )

        var invalidSetNumberFailed = false
        try {
            workoutSessionDao.saveSet(
                sessionExerciseId = sessionExerciseId,
                setNumber = 1,
                actualWeightKg = 40.0,
                actualReps = 8,
                completedFlag = true,
                now = "26.02.2026 11:02"
            )
        } catch (_: IllegalArgumentException) {
            invalidSetNumberFailed = true
        }

        assertTrue(invalidSetNumberFailed)
        assertEquals(1, workoutSessionDao.getSetLogs(sessionExerciseId).size)
    }

    @Test
    fun completeSession_withoutAnySetLog_isRejected() = runBlocking {
        exerciseDao.insertAll(
            listOf(
                ExerciseEntity(code = "C1", brand = "Nautilus", currentWeight = 30.0, weightUnit = "kg", recordedOn = "01.01.2026")
            )
        )

        val sessionId = startTestSession("2026-02-26T12:00:00")
        val completed = workoutSessionDao.completeSession(sessionId, "2026-02-26T12:30:00")

        assertFalse(completed)
        assertEquals(WorkoutSessionEntity.STATUS_STARTED, workoutSessionDao.getSessionById(sessionId)?.status)
    }

    private suspend fun trainFully(sessionId: Long, code: String, now: String) {
        val se = workoutSessionDao.getSessionExercises(sessionId).first { it.exerciseCode == code }
        for (n in 1..se.targetSets) {
            workoutSessionDao.saveSet(se.id, n, 40.0, 10, true, now)
        }
    }

    @Test
    fun startSession_putsExerciseSkippedLastTimeFirst() = runBlocking {
        exerciseDao.insertAll(
            listOf(
                ExerciseEntity(code = "LP", brand = "Nautilus", muscleGroup = "LEGS", currentWeight = 100.0, weightUnit = "kg", recordedOn = "01.01.2026", sortOrder = 0),
                ExerciseEntity(code = "BI", brand = "Nautilus", muscleGroup = "ARMS", currentWeight = 20.0, weightUnit = "kg", recordedOn = "01.01.2026", sortOrder = 1)
            )
        )
        // Session 1 (31.10.): beide trainiert
        val s1 = startTestSession("31.10.2026 18:00")
        trainFully(s1, "LP", "31.10.2026 18:10")
        trainFully(s1, "BI", "31.10.2026 18:20")
        assertTrue(workoutSessionDao.completeSession(s1, "31.10.2026 18:30"))

        // Session 2 (01.11.): Bizeps uebersprungen
        val s2 = startTestSession("01.11.2026 18:00")
        trainFully(s2, "LP", "01.11.2026 18:10")
        assertTrue(workoutSessionDao.completeSession(s2, "01.11.2026 18:30"))

        val s3 = workoutSessionDao.startSession(
            startedAt = "03.11.2026 18:00", repsMin = 8, repsMax = 12, sets = 2, restSeconds = 90,
            today = java.time.LocalDate.of(2026, 11, 3)
        )
        assertEquals(listOf("BI", "LP"), workoutSessionDao.getSessionExercises(s3).map { it.exerciseCode })
    }

    @Test
    fun observeCompletedSessions_sortsChronologicallyAcrossMonthBoundary() = runBlocking {
        exerciseDao.insertAll(
            listOf(ExerciseEntity(code = "LP", brand = "Nautilus", currentWeight = 100.0, weightUnit = "kg", recordedOn = "01.01.2026"))
        )
        val s1 = startTestSession("31.10.2026 18:00")
        trainFully(s1, "LP", "31.10.2026 18:10")
        workoutSessionDao.completeSession(s1, "31.10.2026 18:30")
        val s2 = startTestSession("01.11.2026 18:00")
        trainFully(s2, "LP", "01.11.2026 18:10")
        workoutSessionDao.completeSession(s2, "01.11.2026 18:30")

        val ordered = workoutSessionDao.observeCompletedSessions().first().map { it.id }
        assertEquals(listOf(s2, s1), ordered)
    }
}
