# MVP Foundation – End-to-End-Kernfluss

## Ziel & Scope
Dieses Dokument definiert den **verbindlichen MVP-Kernfluss** für Fitti:

**Profil anlegen → Übung im Plan anlegen (mit Sollwerten + Pause) → Trainingseinheit starten → Ist-Sätze erfassen → Einheit abschließen → Historie ansehen**.

Es konkretisiert die Leitplanken aus `Entwicklungsleitfaden.md` (KISS, YAGNI, SRP, SoC, Offline-first, getrennte Soll-/Ist-Werte, unveränderliche Historie) in eine direkt umsetzbare Spezifikation.

---

## 1) Domain-Objekte & Verantwortlichkeiten (SRP / SoC)

### 1.1 Profil (`Profile`)
**Verantwortung (SRP):** Verwaltung der Stammdaten für genau einen lokalen Nutzer.

- Felder: `firstName`, `lastName`, `heightCm`, `bodyWeightKg`, `updatedAt`.
- Fachregeln:
  - Genau ein aktives Profil im MVP.
  - `firstName` Pflicht.
  - `heightCm > 0`, `bodyWeightKg > 0`.
- Nicht verantwortlich für Trainingslogik.

### 1.2 Übung (`Exercise`)
**Verantwortung:** Repräsentiert ein trainierbares Gerät/eine Übung als Stammobjekt.

- Felder: `name`, `isActive`, `createdAt`, `updatedAt`.
- Fachregeln:
  - `name` Pflicht, pro aktivem Datensatz eindeutig (case-insensitive auf App-Ebene).
- Nicht verantwortlich für Ziele, Sessions oder Logs.

### 1.3 Plan-Ziel (`PlanTarget`)
**Verantwortung:** Definiert Sollwerte pro Übung für kommende Einheiten.

- Referenz auf `Exercise`.
- Felder: `targetWeightKg`, `targetReps`, `targetSets`, `restSeconds`, `progressionStepKg`, `isActive`, `createdAt`, `updatedAt`.
- Fachregeln:
  - Alle Sollfelder Pflicht und > 0.
  - Genau **ein aktives** Plan-Ziel pro Übung im MVP.
  - Änderungen erzeugen neue Version oder Update des aktiven Ziels, ohne alte Session-Daten zu verändern.

### 1.4 Progressionsregel (`ProgressionRule`)
**Verantwortung:** Liefert deterministisch das nächste Zielgewicht nach Abschluss einer Einheit.

- Input: Plan-Snapshot + Satz-Logs der Einheit.
- Output: `nextTargetWeightKg`.
- MVP: nur Default-Regel (siehe Abschnitt 3), keine benutzerdefinierte Rule-Engine.

### 1.5 Trainingseinheit (`WorkoutSession`)
**Verantwortung:** Kapselt einen konkreten Trainingsdurchlauf und seinen Zustand.

- Felder: `status` (`PLANNED`, `STARTED`, `COMPLETED`), `startedAt`, `completedAt`, `createdAt`.
- Fachregeln:
  - Starten nur aus `PLANNED`.
  - Abschließen nur aus `STARTED`.
  - Nach `COMPLETED` keine fachliche Mutation mehr (nur lesend).

### 1.6 Session-Übung (`SessionExercise`)
**Verantwortung:** Bindet eine Übung in eine konkrete Einheit ein und speichert den **Soll-Snapshot**.

- Referenzen: `WorkoutSession`, `Exercise`, `PlanTarget` (Quelle).
- Snapshot-Felder: `plannedWeightKg`, `plannedReps`, `plannedSets`, `plannedRestSeconds`, `plannedProgressionStepKg`.
- Fachregeln:
  - Snapshot wird beim Start/Erstellen der Session fixiert.
  - Spätere Planänderungen wirken nicht rückwirkend auf diesen Datensatz.

### 1.7 Satz-Log (`SetLog`)
**Verantwortung:** Speichert Ist-Werte je tatsächlich ausgeführtem Satz.

- Felder: `setNumber`, `actualWeightKg`, `actualReps`, `isCompleted`, `loggedAt`, optional `note`.
- Referenz: `SessionExercise`.
- Fachregeln:
  - `setNumber` beginnt bei 1, lückenfrei bis max. geplante Satzanzahl (MVP).
  - `actualWeightKg > 0`, `actualReps >= 0`.
  - Unveränderlichkeit nach Session-Abschluss.

### 1.8 Historien-ReadModel (`WorkoutHistoryItem` / Query-Modell)
**Verantwortung:** Leseoptimierte Projektion für Logbuch-Screens.

- Enthält Session-Metadaten + Übungszusammenfassung (Soll vs. Ist, Erfolg/kein Erfolg).
- Kein eigenes Write-Modell; wird über Queries/Mapper aus persistierten Entitäten aufgebaut.

---

## 2) Konkretes Room-Schema

> Ziel: klare Trennung von Stamm-, Plan- und Verlaufsdaten. Historie bleibt unveränderlich, Soll- und Ist-Werte sind getrennt persistiert.

### 2.1 Tabellen / Entities

#### `profiles`
- `id` (PK, `Long`) – im MVP immer `1`.
- `first_name` (`TEXT`, not null)
- `last_name` (`TEXT`, nullable)
- `height_cm` (`REAL`, not null)
- `body_weight_kg` (`REAL`, not null)
- `created_at` (`INTEGER`, epoch millis)
- `updated_at` (`INTEGER`, epoch millis)

#### `exercises`
- `id` (PK)
- `name` (`TEXT`, not null)
- `is_active` (`INTEGER` bool)
- `created_at`, `updated_at`
- Index: `idx_exercises_name_active` für eindeutige aktive Übungsnamen (App-seitige Validierung + optional DB-Unique über normalisierte Spalte später).

#### `plan_targets`
- `id` (PK)
- `exercise_id` (FK → `exercises.id`, `ON DELETE RESTRICT`)
- `target_weight_kg` (`REAL`, not null)
- `target_reps` (`INTEGER`, not null)
- `target_sets` (`INTEGER`, not null)
- `rest_seconds` (`INTEGER`, not null)
- `progression_step_kg` (`REAL`, not null, default `2.5`)
- `is_active` (`INTEGER` bool)
- `created_at`, `updated_at`
- Index: `idx_plan_targets_exercise_active`.
- Regel: pro `exercise_id` maximal ein aktiver Datensatz.

#### `workout_sessions`
- `id` (PK)
- `status` (`TEXT`, Enum: `PLANNED|STARTED|COMPLETED`)
- `started_at` (`INTEGER`, nullable)
- `completed_at` (`INTEGER`, nullable)
- `created_at` (`INTEGER`, not null)

#### `session_exercises`
- `id` (PK)
- `session_id` (FK → `workout_sessions.id`, `ON DELETE CASCADE`)
- `exercise_id` (FK → `exercises.id`, `ON DELETE RESTRICT`)
- `source_plan_target_id` (FK → `plan_targets.id`, nullable bei Legacy)
- **Soll-Snapshot**:
  - `planned_weight_kg` (`REAL`, not null)
  - `planned_reps` (`INTEGER`, not null)
  - `planned_sets` (`INTEGER`, not null)
  - `planned_rest_seconds` (`INTEGER`, not null)
  - `planned_progression_step_kg` (`REAL`, not null)
- `created_at` (`INTEGER`, not null)
- Unique: `(session_id, exercise_id)`.

#### `set_logs`
- `id` (PK)
- `session_exercise_id` (FK → `session_exercises.id`, `ON DELETE CASCADE`)
- `set_number` (`INTEGER`, not null)
- **Ist-Werte**:
  - `actual_weight_kg` (`REAL`, not null)
  - `actual_reps` (`INTEGER`, not null)
  - `is_completed` (`INTEGER` bool, not null)
- `note` (`TEXT`, nullable)
- `logged_at` (`INTEGER`, not null)
- Unique: `(session_exercise_id, set_number)`.

### 2.2 Beziehungen
- `Exercise 1 --- n PlanTarget`
- `WorkoutSession 1 --- n SessionExercise`
- `SessionExercise 1 --- n SetLog`
- `Exercise 1 --- n SessionExercise`
- `PlanTarget 1 --- n SessionExercise` (als Referenz auf Snapshot-Quelle)

### 2.3 Zeitstempel- und Unveränderlichkeitsprinzip
- Alle Write-Entities haben `created_at`; mutable Stamm-/Planobjekte zusätzlich `updated_at`.
- `set_logs` und abgeschlossene `workout_sessions` werden **nicht editiert**.
- Korrekturen im MVP nur via „Einheit löschen und neu erfassen“ (optional später soft-delete/audit).

### 2.4 Soll-/Ist-Trennung
- **Soll** ausschließlich in `plan_targets` (aktuell) und `session_exercises` (historischer Snapshot).
- **Ist** ausschließlich in `set_logs`.
- Historie zeigt immer Vergleich aus `session_exercises` (Soll zum damaligen Zeitpunkt) vs. `set_logs` (tatsächlich).

---

## 3) Default-Progressionsregel (verbindlich)

### 3.1 Definition
Für jede `SessionExercise` gilt nach Session-Abschluss:

1. Ein Satz ist **erfolgreich**, wenn:
   - `is_completed = true`
   - `actual_reps >= planned_reps`
   - `actual_weight_kg >= planned_weight_kg`
2. Eine Übung ist **insgesamt erfolgreich**, wenn **alle geplanten Sätze** erfolgreich sind.
3. Dann: `nextTargetWeightKg = planned_weight_kg + planned_progression_step_kg`.
4. Sonst: `nextTargetWeightKg = planned_weight_kg` (keine Erhöhung).

### 3.2 Randfälle (explizit)
- **Ein Satz nicht geschafft** (Reps unter Soll oder Gewicht unter Soll oder `is_completed = false`): keine Progression.
- **Weniger geloggte Sätze als geplant**: fehlende Sätze gelten als „nicht geschafft“ → keine Progression.
- **Mehr geloggte Sätze als geplant**: nur die ersten `planned_sets` zählen für die Regel; zusätzliche Sätze sind erlaubt, aber progression-neutral.
- **Null/negative Eingaben** werden vor Persistenz validiert und abgewiesen.
- **Rundung Gewicht:** auf 0,5-kg-Schritte runden (half-up), um gym-typische Scheibenlogik abzubilden.

### 3.3 Schreibwirkung
- Bei Erfolg wird das aktive `plan_target` der Übung auf neues Zielgewicht aktualisiert (oder neue Version erzeugt; Implementierung darf intern wählen).
- Historische Session-Daten bleiben unverändert.

---

## 4) Minimaler Screen-Flow (MVP)

### Screen A: Profil anlegen / bearbeiten
**Primäraktion:** „Profil speichern“.

- Pflichtfelder:
  - Vorname
  - Größe (cm)
  - Körpergewicht (kg)
- Optional: Nachname
- Validierung inline + beim Speichern.
- Ergebniszustand: Profil vorhanden.

---

### Screen B: Übung + Plan-Ziel anlegen
**Primäraktion:** „Übung speichern“.

- Pflichtfelder:
  - Übungsname
  - Soll-Gewicht (kg)
  - Soll-Wiederholungen
  - Soll-Sätze
  - Pause (Sekunden)
- Optional (MVP empfohlen als vorausgefüllt): Progressionsschritt (kg), Default 2,5.
- Ergebniszustand: aktive Übung mit aktivem Plan-Ziel.

---

### Screen C: Session-Plan / Start
Zeigt aktive Plan-Übungen als Liste.

**Primäraktion:** „Training starten“.

- Beim Start wird `WorkoutSession(status=STARTED)` erzeugt.
- Für jede aktive Plan-Übung wird `SessionExercise` mit Soll-Snapshot erstellt.
- Ergebniszustand: Einheit gestartet.

---

### Screen D: Laufende Einheit (Sätze erfassen)
Für jede Übung: Sollwerte sichtbar + Eingabe je Satz.

**Primäraktionen:**
- „Satz speichern“ (pro Satz)
- „Einheit abschließen“ (global)

- Pflicht je Satz:
  - Ist-Gewicht
  - Ist-Wiederholungen
  - Satz abgeschlossen (Checkbox/Toggle)
- Pause:
  - Timer kann manuell gestartet werden (MVP), basiert auf `planned_rest_seconds`.
- Ergebniszustand: Satz-Logs vorhanden, Einheit weiterhin `STARTED`.

---

### Screen E: Einheit abschließen (Bestätigung)
**Primäraktion:** „Final abschließen“.

- Validiert, dass mindestens ein Satz geloggt wurde.
- Setzt `status=COMPLETED`, `completedAt`.
- Triggert Progressionsberechnung je `SessionExercise`.
- Ergebniszustand: Einheit abgeschlossen (read-only).

---

### Screen F: Historie / Logbuch
Liste nach Datum (neueste zuerst), Drilldown pro Einheit.

**Primäraktion:** „Einheit öffnen“.

- Zeigt je Übung:
  - damalige Sollwerte (Snapshot)
  - geloggte Ist-Sätze
  - Erfolgsstatus + angewandte/nicht angewandte Progression
- Keine Editiermöglichkeit für abgeschlossene Einheiten.

---

## 5) Akzeptanzkriterien je Schritt (implementier- & testbar)

### 5.1 Profil anlegen
- [ ] Speichern ohne Vorname verhindert und mit verständlicher Fehlermeldung quittiert.
- [ ] `heightCm <= 0` oder `bodyWeightKg <= 0` verhindert Speichern.
- [ ] Erfolgreiches Speichern erzeugt/aktualisiert genau ein Profil lokal in Room.
- [ ] Funktioniert ohne Netzwerk.

### 5.2 Übung + Plan-Ziel anlegen
- [ ] Alle Pflichtfelder müssen gesetzt und > 0 sein.
- [ ] Pro Übung existiert genau ein aktives Plan-Ziel.
- [ ] Plan-Ziel enthält `restSeconds` und `progressionStepKg`.
- [ ] Funktioniert vollständig offline.

### 5.3 Trainingseinheit starten
- [ ] Start nur möglich, wenn mindestens eine aktive Plan-Übung existiert.
- [ ] Session wird mit Status `STARTED` angelegt.
- [ ] Pro aktiver Plan-Übung wird genau ein `SessionExercise`-Snapshot erzeugt.
- [ ] Snapshot bleibt stabil, auch wenn Planwerte danach geändert werden.

### 5.4 Ist-Sätze erfassen
- [ ] `setNumber` ist pro `SessionExercise` eindeutig und bei 1 beginnend.
- [ ] Ungültige Werte (`actualWeightKg <= 0`, `actualReps < 0`) werden abgewiesen.
- [ ] Speichern eines Satzes ist ohne Internet möglich und sofort in der laufenden Einheit sichtbar.
- [ ] Pausenwert pro Übung wird aus Snapshot genutzt (kein Zugriff auf mutable Planwerte nötig).

### 5.5 Einheit abschließen
- [ ] Abschluss nur aus Status `STARTED` möglich.
- [ ] Nach Abschluss sind Session und SetLogs read-only.
- [ ] Progressionsregel wird pro Übung deterministisch angewendet.
- [ ] Bei mindestens einem nicht geschafften Satz erfolgt keine Erhöhung.

### 5.6 Historie ansehen
- [ ] Abgeschlossene Einheiten werden chronologisch angezeigt.
- [ ] Detailansicht zeigt Soll-Snapshot und Ist-Logs getrennt.
- [ ] Historienwerte ändern sich nicht durch spätere Plananpassungen.
- [ ] Historie ist vollständig offline abrufbar.

---

## 6) Unit-Test-Ziele (MVP-Pflicht)

### 6.1 Progression
1. **Alle Sätze geschafft** → Gewicht erhöht um `progressionStepKg`.
2. **Ein Satz nicht geschafft** → keine Erhöhung.
3. **Fehlender Satz-Log** bei geplanter Satzanzahl → keine Erhöhung.
4. **Mehr Sätze als geplant** → zusätzliche Sätze beeinflussen Ergebnis nicht.
5. **Rundung** auf 0,5 kg korrekt.

### 6.2 Validierung
1. Profil-Validierung (Pflichtfelder, >0-Grenzen).
2. Plan-Validierung (Gewicht/Reps/Sätze/Pause >0).
3. SetLog-Validierung (Gewicht >0, Reps >=0, setNumber konsistent).

### 6.3 Mapping / Data-Transformation
1. Entity ↔ Domain-Mapping verliert keine Pflichtfelder.
2. Historien-Mapper trennt Soll-Snapshot und Ist-Logs korrekt.
3. Session-Start-Mapper übernimmt Planwerte unverändert in Snapshot.

---

## 7) Implementierungsreihenfolge (empfohlen, scope-sicher)
1. Room-Entities + DAOs + Migrationsgrundlage.
2. Domain-Modelle + Mapper.
3. Use Cases:
   - `SaveProfileUseCase`
   - `CreateExerciseWithPlanUseCase`
   - `StartWorkoutSessionUseCase`
   - `LogSetUseCase`
   - `CompleteWorkoutSessionUseCase`
   - `GetWorkoutHistoryUseCase`
4. Progressionsservice + Unit-Tests.
5. Minimal-Screens gemäß Flow A–F.

Damit ist der MVP-Kernfluss fachlich und technisch eindeutig spezifiziert und ohne Scope-Drift implementierbar.
