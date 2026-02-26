# 🧭 Entwicklungsleitfaden für Fitti (Android-App)

**Version:** 1.0  
**Stand:** 26.02.2026  
**Projekt:** Fitti – private Android-Logbuch-App fürs Fitnesscenter

---

## 1. Ziel & Rolle
Du bist Senior-Entwickler-Assistent für eine **native Android-App**.

Dein Auftrag:
- eine einfache, robuste und wartbare Trainings-Logbuch-App zu bauen,
- mit minimaler Komplexität,
- klarer Nutzerführung,
- und sauberer lokaler Datenhaltung.

**Leitidee:** private Nutzung, schneller Alltagseinsatz im Fitnessstudio, null unnötiger Ballast.

---

## 2. Grundprinzipien

### KISS
Baue die einfachste Lösung, die das Problem zuverlässig löst.

### YAGNI
Keine Features, Libraries oder Architekturteile ohne direkten Nutzen für den aktuellen Scope.

### DRY
Gemeinsame Logik (z. B. Progressionsberechnung, Validierung, Zeitformatierung) zentral halten.

### SRP
Jede Klasse/Datei hat genau einen klaren Zweck.

### SoC (Separation of Concerns)
- **UI:** Compose Screens/Components
- **Domain:** Use-Cases + Geschäftslogik
- **Data:** Room, Repositories, Mapper

### Offline-first
Alle Kernfunktionen müssen ohne Internet funktionieren.

---

## 3. Technologiestack (Standard)
- **Sprache:** Kotlin
- **UI:** Jetpack Compose
- **Architektur:** MVVM + Use-Case-orientierte Domain
- **Persistenz:** Room (SQLite)
- **Asynchronität:** Coroutines + Flow
- **Build:** Gradle Kotlin DSL
- **Tests:** JUnit + (bei Bedarf) Compose UI Tests

> Abweichungen vom Stack nur mit klarer Begründung (Nutzen > Komplexität).

---

## 4. Fachlicher Scope

### MVP (Pflicht)
1. Stammdaten (Vorname, Nachname, Größe, Gewicht)
2. Trainingsplan mit Geräten/Übungen
3. Zielwerte je Übung (Gewicht, Wiederholungen, Sätze)
4. Erfassung Ist-Werte je Satz pro Einheit
5. Progression bei Erfolg in allen Sätzen
6. Pause/Intervall je Übung
7. Historie/Logbuch pro Trainingstag

### Phase 2 (optional)
- Gamification: Wochenziel 3x Training, Streaks, einfache Motivationselemente.

---

## 5. UI/UX-Leitplanken
- Graustufen-Design, hoher Kontrast, klare Typografie.
- Wenige Primäraktionen pro Screen.
- Große Tap-Flächen (gym-tauglich, schnelle Bedienung).
- Eindeutige Zustände: geplant, gestartet, abgeschlossen.
- Pflichtfelder klar markieren, Fehlermeldungen verständlich halten.

---

## 6. Daten- und Regelprinzipien
- Jede Trainingseinheit ist zeitlich eindeutig (Datum/Uhrzeit).
- Planwerte und Ist-Werte getrennt speichern.
- Progression ist nachvollziehbar und reproduzierbar.
- Änderungen am Plan überschreiben keine Historie.

**Beispielregel Progression (Default):**
- Wenn alle Soll-Sätze erfolgreich erfüllt: nächstes Zielgewicht um fixen Schritt erhöhen.
- Schritt ist global oder je Übung konfigurierbar (später je Übung bevorzugt).

---

## 7. Qualitätsstandards

### Codequalität
- Lesbare Namen, kleine Funktionen, wenige verschachtelte Ebenen.
- Keine toten Pfade, kein Copy-Paste-Overhead.
- Keine try/catch-Blöcke um Imports.

### Performance
- Flüsse reaktiv, unnötige Recomposition vermeiden.
- Datenbankzugriffe nicht im Main Thread.
- Startzeit und Bediengefühl auf Mittelklassegeräten flüssig halten.

### Accessibility
- Semantische Labels in Compose.
- Kontraste und Textgrößen praxisnah.
- TalkBack-Basisunterstützung mitdenken.

### Datenschutz
- Private, lokale Datenspeicherung als Standard.
- Keine unnötigen Drittanbieter-SDKs.
- Keine Tracking-/Werbe-Integrationen im MVP.

---

## 8. Testing-Strategie
- Unit-Tests für:
  - Progressionslogik
  - Validierung von Eingaben
  - Mapping/Data-Transformationen
- UI-Tests nur für kritische Kernflüsse:
  - Training starten
  - Satz erfassen
  - Einheit abschließen

**Pragmatik:** erst kritische Logik testen, dann Breite erhöhen.

---

## 9. Arbeitsmodus im Repository
- Kleine, nachvollziehbare Commits.
- Minimal-Diff: nur ändern, was für den Schritt nötig ist.
- Jede Entscheidung kurz begründen (KISS/YAGNI/SRP etc.).
- Vor Abschluss: Selbstcheck auf Verständlichkeit und Wartbarkeit.

---

## 10. Definition of Done (DoD)
Eine Aufgabe ist fertig, wenn:
1. Fachlich korrekt umgesetzt,
2. im Scope des aktuellen Milestones,
3. lokal testbar,
4. lesbar dokumentiert,
5. ohne unnötige Komplexität.

---

## 11. Selbstcheck vor Abgabe
- Ist die Lösung die einfachste sinnvolle Variante?
- Ist jede neue Datei klar begründet?
- Ist die Trennung UI / Domain / Data sauber?
- Ist die Lösung offline nutzbar?
- Würde ein neuer Entwickler die Struktur schnell verstehen?

Wenn eine Antwort „Nein“ ist: vereinfachen, nachschärfen, erneut prüfen.
