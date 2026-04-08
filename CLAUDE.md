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
    FittiDatabase.kt        # Version 5, Migrationen 1->2->3->4->5
    ExerciseEntity.kt       # 8 Nautilus-Maschinen (Seed-Daten, inkl. F6 Ab Crunch)
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
- Room SQLite, Version 5, offline-first
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
| F6 | Ab Crunch | ABS |

Unterstuetzte Gruppen: CHEST, BACK, LEGS, SHOULDERS, ARMS (Arme), ABS (Bauch)

## Umgesetzte Features
- Reps pro Satz loggen (8-12 Auswahl statt nur geschafft/nicht)
- Gewichtssteigerung pro Maschine individuell (ExerciseEntity.progressionStepKg)
- Reihenfolge im Zirkel anpassbar (ExerciseEntity.sortOrder, Hoch/Runter in Settings)
- Double Progression: Steigerung nur wenn ALLE Saetze die Max-Reps erreichen (z.B. 12/12)
- Graustufen-Accessibility: Status-Icons, einheitliche Buttons, Bold/Normal statt Farbe
- Default Pause: 60s
- Freitext-Ziel im Profil (mehrzeiliges Eingabefeld, auf Home Screen angezeigt)
- Claude API-Schluessel Speicherung in Einstellungen (fuer KI-Feedback)
- Shared Debug Keystore fuer konsistente APK-Signierung (CI + lokal)
- Geraete hinzufuegen/entfernen in Einstellungen (Add-Dialog mit Muskelgruppe, Delete mit Bestaetigung)
- Muskelgruppe ARMS im Add-Dialog waehlbar (alle 5 Gruppen: CHEST, BACK, LEGS, SHOULDERS, ARMS)
- Export/Import fuer Geraetewechsel (JSON via Android Share Sheet / Datei-Picker)
- Koerpergewichts-Verlaufsgraph auf Home Screen (Canvas-basierter Liniengraph)
- GitHub Actions CI/CD: `.github/workflows/build.yml` baut APK bei Push auf main, Artifact downloadbar
- Sitz- und Polsterposition pro Geraet (seatPosition/padPosition, Anzeige im Training als S/P)
- Nautilus Gewichtsstufen-Stack pro Geraet (weightSteps, automatische naechste Stufe bei Progression)
- Angenehmer Dreiklang-Sound bei Timer-Ende (ToneGenerator statt System-Notification)

## Bekannte TODOs
- KI-Feedback nach dem Training (kurz, via Claude API)
- Woechentliche KI-Trainingsanalyse (laenger, via Claude API)
