package com.fitti.domain

import com.fitti.data.ExerciseEntity
import com.fitti.data.ExerciseTrainedOn
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ExerciseOrderPlannerTest {

    private val today = LocalDate.of(2026, 11, 1)

    private fun ex(id: Long, code: String, group: String, sort: Int) = ExerciseEntity(
        id = id, code = code, brand = "Nautilus", muscleGroup = group,
        currentWeight = 40.0, weightUnit = "kg", recordedOn = "01.01.2026", sortOrder = sort
    )

    private val legPress = ex(1, "B6", "LEGS", 0)
    private val chest = ex(2, "B2", "CHEST", 1)
    private val row = ex(3, "C2", "BACK", 2)
    private val biceps = ex(4, "BI", "ARMS", 3)
    private val abs = ex(5, "F6", "ABS", 4)
    private val all = listOf(legPress, chest, row, biceps, abs)

    private fun allTrainedOn(date: String) = all.map { ExerciseTrainedOn(it.id, date) }

    private fun codes(list: List<ExerciseEntity>) = list.map { it.code }

    @Test
    fun equalPause_noTargets_keepsLayoutOrder() {
        val result = ExerciseOrderPlanner.order(all, allTrainedOn("29.10.2026 18:00"), emptySet(), today)
        assertEquals(listOf("B6", "B2", "C2", "BI", "F6"), codes(result))
    }

    @Test
    fun skippedArmsExercise_comesFirstNextTime() {
        val trained = allTrainedOn("25.10.2026 18:00") +
            all.filter { it != biceps }.map { ExerciseTrainedOn(it.id, "29.10.2026 18:00") }
        val result = ExerciseOrderPlanner.order(all, trained, emptySet(), today)
        assertEquals("BI", result.first().code)
    }

    @Test
    fun neverTrained_comesFirst() {
        val trained = all.filter { it != row }.map { ExerciseTrainedOn(it.id, "29.10.2026 18:00") }
        val result = ExerciseOrderPlanner.order(all, trained, emptySet(), today)
        assertEquals("C2", result.first().code)
    }

    @Test
    fun abs_staysLast_evenWhenLongestPause() {
        val trained = all.filter { it != abs }.map { ExerciseTrainedOn(it.id, "29.10.2026 18:00") }
        val result = ExerciseOrderPlanner.order(all, trained, emptySet(), today)
        assertEquals("F6", result.last().code)
    }

    @Test
    fun equalPause_focusGroupsFirst_thenLayout() {
        val result = ExerciseOrderPlanner.order(all, allTrainedOn("29.10.2026 18:00"), setOf("back", "ARMS"), today)
        assertEquals(listOf("C2", "BI", "B6", "B2", "F6"), codes(result))
    }

    @Test
    fun longerPause_beatsFocusGroup() {
        val trained = allTrainedOn("20.10.2026 18:00") +
            all.filter { it != chest }.map { ExerciseTrainedOn(it.id, "29.10.2026 18:00") }
        val result = ExerciseOrderPlanner.order(all, trained, setOf("BACK"), today)
        assertEquals(listOf("B2", "C2"), codes(result).take(2))
    }

    @Test
    fun monthBoundary_isComparedChronologically() {
        // 31.10. ist aelter als 01.11. — als String waere es umgekehrt.
        val trained = all.map {
            ExerciseTrainedOn(it.id, if (it == row) "31.10.2026 18:00" else "01.11.2026 08:00")
        }
        val result = ExerciseOrderPlanner.order(all, trained, emptySet(), today)
        assertEquals("C2", result.first().code)
    }

    @Test
    fun parseDate_acceptsGermanAndIso() {
        assertEquals(LocalDate.of(2026, 2, 26), ExerciseOrderPlanner.parseDate("26.02.2026 10:00"))
        assertEquals(LocalDate.of(2026, 2, 26), ExerciseOrderPlanner.parseDate("2026-02-26T10:00:00"))
        assertEquals(null, ExerciseOrderPlanner.parseDate("kaputt"))
    }
}
