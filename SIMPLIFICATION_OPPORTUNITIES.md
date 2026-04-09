# Simplification & Robustness Opportunities

Codebase audit, April 2026. No code changes — just observations.

---

## A. DRY Violations

### 1. SimpleDateFormat created in 6+ places

`SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)` is instantiated independently in:

| Location | Line |
|----------|------|
| `HomeViewModel.kt` | 49 |
| `ActiveWorkoutViewModel.kt` | 84 |
| `Formatting.kt` (calculateDuration) | 26 |
| `HomeScreen.kt` | 261 |
| `SettingsScreen.kt` | 615, 995 |

**Fix:** Extract a shared constant in `Formatting.kt`, e.g. `val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)`. Same for the `"dd.MM.yyyy"` variant used in `SettingsScreen.kt` lines 632, 907, 962.

---

### 2. Nautilus weight stack string duplicated

The string `"9,14,18,23,27,32,36,41,46,50,55,59,64,68,73,77,82,86,91"` is defined identically in:

- `FittiDatabase.kt:136` (MIGRATION_4_5)
- `ExerciseRepository.kt:50` (ensureSeeded)

**Fix:** Extract to a companion constant on `ExerciseEntity` or a shared object, e.g. `NautilusDefaults.WEIGHT_STACK_KG`.

---

### 3. AI feedback UI block duplicated across screens

Nearly identical state management + UI rendering (~60 lines each) in:

- `ActiveWorkoutScreen.kt:504-678`
- `HistoryDetailScreen.kt:68-245`

Both declare `aiFeedback`, `isLoadingFeedback`, `feedbackError` as local `remember` state, then repeat the same loading/error/result card layout.

**Fix:** Extract a shared `AiFeedbackSection` composable that takes an `onRequestFeedback: suspend () -> String` lambda.

---

## B. Stale Documentation

### 4. Entwicklungsleitfaden.md is out of date

| Section | Issue |
|---------|-------|
| Line 51: Technologiestack | Says "Room (SQLite, Version 4)" — actual is **Version 5** |
| Lines 81-91: Muskelgruppen-Tabelle | Missing **F6 Ab Crunch** (ABS) |
| Lines 94-96: "Geplant" | Lists "Geraete hinzufuegen/entfernen" and "Export/Import" as planned — both are **already implemented** |
| Overall | Missing many shipped features: Sitz/Polsterposition, Nautilus Gewichtsstufen, Dreiklang-Sound, GitHub Actions CI/CD, Koerpergewichts-Graph, Claude API key storage |

### 5. README.md is out of date

| Section | Issue |
|---------|-------|
| Line 21 | Says "Migrationen (v1 → v2 → v3 → v4)" — actual is **v5** |
| Lines 76-81: "Geplante Verbesserungen" | Lists Koerpergewichts-Verlaufsgraph and Custom Uebungen as planned — both are **already implemented** |
| Line 12 | Says "7 Nautilus-Maschinen" — actual is **8** (includes F6 Ab Crunch) |

---

## C. Robustness

### 6. Seed data contains user-specific weights

`ExerciseRepository.kt:53-60` hardcodes personal training weights as defaults:

```
B2 Chest Press:    41.0 kg
B6 Leg Press:     160.0 kg
D3 Shoulder Press: 36.0 kg
...
```

These are not sensible defaults for new users. A fresh install inherits one person's training state. Consider using a minimal starting weight (e.g. the first step in the weight stack) or 0.

---

### 7. Inconsistent weightSteps on Nautilus machines

Most Nautilus machines get `weightSteps = nautilusStack` in the seed data, but two do not:

| Code | Machine | Has weightSteps? |
|------|---------|-----------------|
| B6 | Leg Press | **No** |
| D4 | Leg Extension | **No** |

If these machines genuinely use a different weight system, it should be documented. Otherwise they should get the stack too.

---

### 8. D4 (Leg Extension) uses "lb" while all others use "kg"

`ExerciseRepository.kt:58` — D4 is the only exercise with `weightUnit = "lb"`. This is undocumented and may confuse progression logic if not intentional.

---

### 9. Hardcoded freshness thresholds

`HomeViewModel.kt` uses magic numbers for muscle group freshness:

- 4 days = FRESH
- 6 days = STALE
- >6 days = OVERDUE

These thresholds are buried in ViewModel logic. Moving them to named constants (in `Formatting.kt` or `SettingsRepository`) would make them discoverable and potentially user-configurable.

---

## D. Architecture Simplification

### 10. Repositories passed through composable parameters

`MainActivity.kt` creates repos and passes them to every screen as parameters. Screens then forward repos to inner composables and lambdas (e.g. `HomeScreen` passes `workoutRepo` into an `onClick` lambda at line 260).

This creates long parameter lists (HomeScreen takes 7 params, ActiveWorkoutScreen takes 7 params). Holding repos exclusively in ViewModels would simplify screen signatures and keep data access out of the UI layer — consistent with the MVVM pattern the Entwicklungsleitfaden prescribes.

---

### 11. WorkoutSessionUseCases are single-line delegations

`domain/WorkoutSessionUseCases.kt` contains 4 use-case classes that each delegate a single call to the repository:

```kotlin
class StartWorkoutSession(...) {
    suspend operator fun invoke(...) = workoutRepo.startSession(...)
}
```

Per KISS/YAGNI, these could be inlined — calling the repo directly from ViewModels — unless there's a plan to add cross-cutting logic here.
