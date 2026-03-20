# Fitti - Projektkonventionen

## Build
```bash
export JAVA_HOME="/e/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug    # APK bauen
./gradlew test             # Unit Tests
```

APK Output: `app/build/outputs/apk/debug/app-debug.apk`

## Architektur

```
com.fitti/
  MainActivity.kt          # NavHost mit 4 Routes
  data/                     # Room Entities, DAOs, Repositories
    FittiDatabase.kt        # Version 4, Migrationen 1->2->3->4
    ExerciseEntity.kt       # 7 Nautilus-Maschinen (Seed-Daten)
    SessionExerciseEntity.kt # Soll-Snapshot pro Training
    SetLogEntity.kt         # Ist-Werte pro Satz
    WeightLogEntity.kt      # Koerpergewicht-Zeitreihe
    SettingsRepository.kt   # SharedPreferences fuer Defaults
  domain/
    Exercise.kt             # Domain-Modell
    ProgressionService.kt   # Gewichtsberechnung, Rundung, Progressions-Eligibility
    WorkoutSessionUseCases.kt
  ui/
    HomeViewModel.kt        # Home: Muskelgruppen-Frische, Sessions
    ActiveWorkoutViewModel.kt # Kern: Queue, Timer, Progression
    HistoryDetailViewModel.kt
    common/Formatting.kt    # Shared: muscleGroupLabels, cleanWeight, calculateDuration
    theme/FittiTheme.kt     # Dark Theme
    screens/                # 4 Compose Screens
```

## Datenbank
- Room SQLite, Version 4, offline-first
- Snapshot-Pattern: SessionExercise friert Planwerte beim Start ein
- Set-Logs sind nach Session-Abschluss read-only

## Konventionen
- Sprache: Kotlin, UI komplett in Jetpack Compose
- Deutsche UI-Texte (UTF-8 Umlaute als Unicode-Escapes: `\u00fc` = ue, `\u00e4` = ae, etc.)
- Kein Hilt/DI - manuelle Konstruktion in MainActivity
- KISS, YAGNI, SRP Prinzipien
- Alle Defaults in SettingsRepository (nicht hardcoded in DAOs)

## Muskelgruppen
| Code | Name | Gruppe |
|------|------|--------|
| B2 | Chest Press | CHEST |
| B6 | Leg Press | LEGS |
| C2 | Seated Row | BACK |
| C6 | Butterfly | CHEST |
| D3 | Shoulder Press | SHOULDERS |
| D4 | Leg Extension | LEGS |
| F3 | Lat Pulldown | BACK |

## Umgesetzte Features
- Reps pro Satz loggen (8-12 Auswahl statt nur geschafft/nicht)
- Gewichtssteigerung pro Maschine individuell (ExerciseEntity.progressionStepKg)
- Reihenfolge im Zirkel anpassbar (ExerciseEntity.sortOrder, Hoch/Runter in Settings)
- Double Progression: Steigerung nur wenn ALLE Saetze die Max-Reps erreichen (z.B. 12/12)
- Graustufen-Accessibility: Status-Icons, einheitliche Buttons, Bold/Normal statt Farbe
- Default Pause: 60s

## Bekannte TODOs
- Geraete hinzufuegen/entfernen (UI + DAO)
- Fehlende Muskelgruppen: ARMS (Bizeps/Trizeps)
- Export/Import fuer Geraetewechsel
