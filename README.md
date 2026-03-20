# Fitti – Private Android-Logbuch-App f\u00fcrs Fitnesscenter

## Ziel der App
**Fitti** ist eine bewusst einfache, private Android-App f\u00fcr dein eigenes Training im Fitnessstudio.
Der Fokus liegt auf einem zuverl\u00e4ssigen Trainings-Logbuch statt auf Social- oder Cloud-Funktionen.

Die App dient langfristig als Datengrundlage f\u00fcr einen LLM-basierten Personal Trainer.

## Status: MVP implementiert

- \u2705 4 Screens: Home, Active Workout, History Detail, Settings
- \u2705 7 Nautilus-Maschinen mit Muskelgruppen (Brust, R\u00fccken, Beine, Schultern)
- \u2705 Kartenspiel-Metapher: eine \u00dcbung nach der anderen
- \u2705 One-Tap Set-Logging mit Rest-Timer (Sound + Vibration)
- \u2705 Manuelle Progressionsentscheidung nach allen S\u00e4tzen
- \u2705 \u00dcberspringen-Funktion f\u00fcr besetzte Ger\u00e4te
- \u2705 Muskelgruppen-Frische-Indikator auf Home Screen
- \u2705 Periodische K\u00f6rpergewichts-Abfrage
- \u2705 Einstellungen: Rep-Range, S\u00e4tze, Pause, Progression, Ziel, Gr\u00f6\u00dfe
- \u2705 Dark Theme, Portrait-Lock, Screen-Always-On
- \u2705 Room-Datenbank mit Migrationen (v1 \u2192 v2 \u2192 v3)

## Kernfunktionen

### Training starten
App \u00f6ffnen \u2192 "Training starten" \u2192 erste \u00dcbung erscheint mit Gewicht, Rep-Range (8-12) und S\u00e4tzen (2).

### Satz loggen
"Satz geschafft" antippen \u2192 Timer startet automatisch (60s) \u2192 Sound + Vibration wenn abgelaufen.

### Progression
Nach allen S\u00e4tzen: "Mehr Gewicht n\u00e4chstes Mal?" \u2192 Ja/Nein. Bei Ja wird das Gewicht f\u00fcr n\u00e4chstes Mal erh\u00f6ht.

### \u00dcberspringen
Ger\u00e4t besetzt? "\u00dcberspringen" \u2192 kommt sp\u00e4ter im Zirkel wieder dran.

### Historie
Vergangene Trainings einsehen: Datum, Dauer, Soll vs. Ist pro \u00dcbung.

## Tech-Stack
- **Kotlin** + **Jetpack Compose** (Material3 Dark Theme)
- **Room** (SQLite, offline-first, lokal)
- **MVVM** + Use Cases
- **Navigation Compose** (4 Routes)
- **Coroutines + Flow**
- Target: Android 8+ (API 26), optimiert f\u00fcr Pixel 9a

## Build

```bash
# JAVA_HOME auf Android Studio JBR setzen
export JAVA_HOME="/pfad/zu/Android Studio/jbr"

# APK bauen
./gradlew assembleDebug

# Tests laufen lassen
./gradlew test

# APK: app/build/outputs/apk/debug/app-debug.apk
```

Oder: Projekt in Android Studio \u00f6ffnen \u2192 Run auf verbundenem Ger\u00e4t.

## Nicht-Ziele im MVP
- Kein Login/Account-System
- Keine Cloud-Synchronisierung
- Keine Social Features
- Keine Smartwatch-Integration

## Geplante Verbesserungen
- Wiederholungen pro Satz loggen (8-12 Auswahl)
- Gewichtssteigerung pro Maschine individuell
- Reihenfolge im Zirkel anpassbar
- Export-Funktion (JSON) f\u00fcr LLM-Trainer
- K\u00f6rpergewichts-Verlaufsgraph
- Custom \u00dcbungen hinzuf\u00fcgen
