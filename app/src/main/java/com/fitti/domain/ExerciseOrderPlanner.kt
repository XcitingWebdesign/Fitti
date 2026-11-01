package com.fitti.domain

import com.fitti.data.ExerciseEntity
import com.fitti.data.ExerciseTrainedOn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Legt die Reihenfolge der Geraete fuer eine neue Session fest.
 *
 * Sortierschluessel (trainingswissenschaftlich begruendet, Prioritaetsprinzip):
 * 1. Bauch (ABS) immer zuletzt: vorermuedeter Rumpf destabilisiert die grossen Uebungen.
 * 2. Laengste Pause zuerst (Tage seit dem letzten vollstaendigen Training; nie = maximal).
 *    Uebersprungene/unvollstaendige Uebungen holen so ihren Reiz nach, solange man frisch ist.
 * 3. Ziel-Fokusgruppen (Radar-Fortschritt unter Schwelle) zuerst.
 * 4. Geraete-Aufstellung (sortOrder) als Tie-Break.
 */
object ExerciseOrderPlanner {

    val LAST_GROUPS = setOf("ABS")
    const val FOCUS_PROGRESS_THRESHOLD = 0.7f

    private val germanFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun order(
        exercises: List<ExerciseEntity>,
        trainedOn: List<ExerciseTrainedOn>,
        focusGroups: Set<String>,
        today: LocalDate
    ): List<ExerciseEntity> {
        val lastTrained = mutableMapOf<Long, LocalDate>()
        for (row in trainedOn) {
            val date = parseDate(row.startedAt) ?: continue
            val prev = lastTrained[row.exerciseId]
            if (prev == null || date.isAfter(prev)) lastTrained[row.exerciseId] = date
        }
        val focus = focusGroups.map { it.trim().uppercase() }.toSet()

        return exercises.sortedWith(
            compareBy<ExerciseEntity> { it.muscleGroup.trim().uppercase() in LAST_GROUPS }
                .thenByDescending { ex ->
                    lastTrained[ex.id]?.let { ChronoUnit.DAYS.between(it, today) } ?: Long.MAX_VALUE
                }
                .thenBy { it.muscleGroup.trim().uppercase() !in focus }
                .thenBy { it.sortOrder }
        )
    }

    /** Akzeptiert "dd.MM.yyyy HH:mm" (App-Format) und ISO "yyyy-MM-dd...". */
    internal fun parseDate(raw: String): LocalDate? = try {
        val s = raw.trim()
        when {
            s.length >= 10 && s[2] == '.' -> LocalDate.parse(s.substring(0, 10), germanFormat)
            s.length >= 10 && s[4] == '-' -> LocalDate.parse(s.substring(0, 10))
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
