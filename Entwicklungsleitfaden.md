# Entwicklungsleitfaden f\u00fcr Fitti (Android-App)

**Version:** 2.0
**Stand:** 20.03.2026
**Projekt:** Fitti \u2013 private Android-Logbuch-App f\u00fcrs Fitnesscenter

---

## 1. Ziel & Rolle
Du bist Senior-Entwickler-Assistent f\u00fcr eine **native Android-App**.

Dein Auftrag:
- eine einfache, robuste und wartbare Trainings-Logbuch-App zu bauen,
- mit minimaler Komplexit\u00e4t,
- klarer Nutzerf\u00fchrung,
- und sauberer lokaler Datenhaltung.

**Leitidee:** Private Nutzung, schneller Alltagseinsatz im Fitnessstudio, null unn\u00f6tiger Ballast.
Die App dient langfristig als Datenbasis f\u00fcr einen LLM-basierten Personal Trainer.

---

## 2. Grundprinzipien

### KISS
Baue die einfachste L\u00f6sung, die das Problem zuverl\u00e4ssig l\u00f6st.

### YAGNI
Keine Features, Libraries oder Architekturteile ohne direkten Nutzen f\u00fcr den aktuellen Scope.

### DRY
Gemeinsame Logik zentral halten (siehe `ui/common/Formatting.kt`).

### SRP
Jede Klasse/Datei hat genau einen klaren Zweck.

### SoC (Separation of Concerns)
- **UI:** Compose Screens/Components (`ui/screens/`)
- **Domain:** Use-Cases + Gesch\u00e4ftslogik (`domain/`)
- **Data:** Room, Repositories, Mapper (`data/`)

### Offline-first
Alle Kernfunktionen m\u00fcssen ohne Internet funktionieren.

---

## 3. Technologiestack
- **Sprache:** Kotlin
- **UI:** Jetpack Compose (Material3, Dark Theme)
- **Architektur:** MVVM + Use-Case-orientierte Domain
- **Persistenz:** Room (SQLite, Version 4)
- **Navigation:** Navigation Compose (4 Routes)
- **Asynchronit\u00e4t:** Coroutines + Flow
- **Build:** Gradle Kotlin DSL
- **Tests:** JUnit + Robolectric

---

## 4. Aktueller Scope (MVP)

### Implementiert
1. **Home Screen** \u2013 Muskelgruppen-Frische-Indikator (Status-Icons: \u2713 ~ ! \u2013), Training starten/fortsetzen, letzte Trainings
2. **Active Workout** \u2013 Rep-Picker (8-12 Buttons pro Satz), Set-Logging mit tats\u00e4chlichen Reps, Rest-Timer mit Sound/Vibration, \u00dcberspringen
3. **History Detail** \u2013 Vergangene Sessions mit Soll vs. Ist
4. **Settings** \u2013 Profil + Training-Defaults + \u00dcbungen-Reihenfolge + individuelle Gewichtssteigerung pro Maschine

### Trainingslogik
- Rep-Picker: Buttons f\u00fcr jede Rep-Zahl im Zielbereich (z.B. 8, 9, 10, 11, 12) + "Nicht geschafft" (0)
- **Double Progression:** Gewichtssteigerung wird nur vorgeschlagen wenn ALLE S\u00e4tze die **Maximal-Reps** erreichen (z.B. 12/12/12)
- Gewichtssteigerung pro Maschine individuell konfigurierbar (ExerciseEntity.progressionStepKg)
- Reihenfolge im Zirkel anpassbar (ExerciseEntity.sortOrder, Hoch/Runter in Settings)
- Gewichtsrundung auf 0,5 kg/lb
- Snapshot-Pattern: Planwerte werden beim Session-Start eingefroren
- K\u00f6rpergewicht wird periodisch abgefragt (>7 Tage seit letzter Messung)

### Accessibility (Graustufen-Modus)
- Muskelgruppen-Status: Icons statt Farbpunkte (\u2713=frisch, ~=f\u00e4llig, !=\u00fcberf\u00e4llig, \u2013=nie)
- Rep-Picker: Einheitliche Buttons, keine Farbunterscheidung
- Set-Logs: Bold/Normal-Schriftgewicht statt Gr\u00fcn/Rot

### Muskelgruppen
| Code | Ger\u00e4t | Gruppe |
|------|-------|--------|
| B2 | Chest Press | Brust |
| B6 | Leg Press | Beine |
| C2 | Seated Row | R\u00fccken |
| C6 | Butterfly | Brust |
| D3 | Shoulder Press | Schultern |
| D4 | Leg Extension | Beine |
| F3 | Lat Pulldown | R\u00fccken |

### Geplant
- Ger\u00e4te hinzuf\u00fcgen/entfernen (z.B. Bizeps-Curl, Trizeps, Adduktoren)
- Fehlende Muskelgruppen: ARMS (Bizeps/Trizeps)
- Export/Import f\u00fcr Ger\u00e4tewechsel

---

## 5. UI/UX-Leitplanken
- **Dark Theme**, hoher Kontrast, klare Typografie
- Wenige Prim\u00e4raktionen pro Screen
- Gro\u00dfe Tap-Fl\u00e4chen (min. 56dp, gym-tauglich)
- Eindeutige Zust\u00e4nde: gestartet, abgeschlossen
- Pflichtfelder klar markieren
- Screen bleibt an w\u00e4hrend Training
- Portrait-Lock

---

## 6. Daten- und Regelprinzipien
- Planwerte und Ist-Werte getrennt speichern (Snapshot-Pattern)
- \u00c4nderungen am Plan \u00fcberschreiben keine Historie
- Progression ist nachvollziehbar (User-Entscheidung geloggt)
- K\u00f6rpergewicht als Zeitreihe in `weight_logs` Tabelle

---

## 7. Qualit\u00e4tsstandards

### Codequalit\u00e4t
- Lesbare Namen, kleine Funktionen, wenige verschachtelte Ebenen
- Keine toten Pfade, kein Copy-Paste-Overhead
- Shared Code in `ui/common/`

### Performance
- Fl\u00fcsse reaktiv, unn\u00f6tige Recomposition vermeiden
- Datenbankzugriffe nicht im Main Thread
- Button-Debounce gegen Doppel-Tap

### Datenschutz
- Private, lokale Datenspeicherung als Standard
- Keine Drittanbieter-SDKs, kein Tracking, keine Werbung

---

## 8. Testing-Strategie
- Unit-Tests f\u00fcr Progressionslogik (6 Tests)
- Unit-Tests f\u00fcr DAO-Operationen (3 Tests)
- Pragmatik: erst kritische Logik testen, dann Breite erh\u00f6hen

---

## 9. Selbstcheck vor Abgabe
- Ist die L\u00f6sung die einfachste sinnvolle Variante?
- Ist jede neue Datei klar begr\u00fcndet?
- Ist die Trennung UI / Domain / Data sauber?
- Ist die L\u00f6sung offline nutzbar?
- W\u00fcrde ein neuer Entwickler die Struktur schnell verstehen?
