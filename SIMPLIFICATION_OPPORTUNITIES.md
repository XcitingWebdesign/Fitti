# Simplification & Robustness Opportunities

Codebase-Audit, April 2026.

Items 1-5 und 9 wurden umgesetzt. Items 6-8, 10-11 sind noch offen.

---

## Umgesetzt

### 1. SimpleDateFormat zentralisiert
Alle 6+ Stellen nutzen jetzt `formatDateTime()`, `formatDate()`, `parseDateTime()` und `daysSince()` aus `Formatting.kt`.

### 2. Nautilus Weight Stack als Konstante
`ExerciseEntity.NAUTILUS_WEIGHT_STACK_KG` statt duplizierter Strings in `FittiDatabase.kt` und `ExerciseRepository.kt`.

### 3. AI-Feedback als Shared Composable
`AiFeedbackSection` in `ui/common/AiFeedbackSection.kt` ersetzt ~120 Zeilen duplizierten Code in `ActiveWorkoutScreen` und `HistoryDetailScreen`.

### 4-5. Dokumentation aktualisiert
- Entwicklungsleitfaden.md: DB Version 5, F6 Ab Crunch ergaenzt, implementierte Features nachgetragen
- README.md: 8 Maschinen, v5-Migrationen, implementierte Features nachgetragen

### 9. Freshness-Schwellwerte als Konstanten
`FRESHNESS_FRESH_DAYS`, `FRESHNESS_STALE_DAYS`, `WEIGHT_LOG_INTERVAL_DAYS` in `Formatting.kt`.

### Unused UseCases entfernt
`SaveSetLogUseCase`, `CompleteWorkoutSessionUseCase`, `GetWorkoutHistoryUseCase` waren nirgends referenziert.

---

## Offen (bewusst nicht angefasst)

### 6. Seed-Daten enthalten nutzerspezifische Gewichte
`ExerciseRepository.kt` hardcoded persoenliche Trainingsgewichte (41 kg Chest Press, 160 kg Leg Press etc.) als Defaults. Bei einer Neuinstallation erbt man diese Werte. Besser: Minimalgewicht oder 0.

### 7. Inkonsistente weightSteps bei Nautilus-Maschinen
B6 (Leg Press) und D4 (Leg Extension) haben keinen `weightSteps`-Eintrag, alle anderen Nautilus-Maschinen schon. Falls beabsichtigt → dokumentieren. Falls nicht → Stack ergaenzen.

### 8. D4 nutzt "lb", alle anderen "kg"
`ExerciseRepository.kt:58` — D4 ist das einzige Geraet mit `weightUnit = "lb"`. Undokumentiert.

### 10. Repositories als Composable-Parameter
Repos werden von `MainActivity` durch Screen-Composables bis in innere Lambdas durchgereicht. Sauberer waere es, sie ausschliesslich in ViewModels zu halten (MVVM-konform). Groesseres Refactoring.

### 11. StartWorkoutSessionUseCase behalten
`StartWorkoutSessionUseCase` kombiniert Repo + Settings und ist sinnvoll. Die drei ungenutzten Use Cases wurden entfernt.
