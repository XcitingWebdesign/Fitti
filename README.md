# Fitti – Private Android-Logbuch-App fürs Fitnesscenter

## Ziel der App
**Fitti** ist eine bewusst einfache, private Android-App für dein eigenes Training im Fitnessstudio.
Der Fokus liegt auf einem zuverlässigen Trainings-Logbuch statt auf Social- oder Cloud-Funktionen.

Die App soll dir helfen, pro Gerät strukturiert zu trainieren, Fortschritt sauber festzuhalten und beim nächsten Training direkt mit der passenden Steigerung weiterzumachen.

## Kernfunktionen (MVP)
1. **Trainingsplan verwalten**
   - Geräte/Übungen anlegen.
   - Pro Gerät Zielwerte hinterlegen: Gewicht, Wiederholungen, Sätze.
2. **Trainingseinheit protokollieren**
   - Tatsächlich absolvierte Werte je Satz erfassen.
   - Dokumentieren, was geplant war vs. was gemacht wurde.
3. **Automatische Progression**
   - Wenn Ziel in allen Sätzen erreicht wurde, wird für die nächste Einheit eine definierte Steigerung vorgeschlagen.
4. **Pausen-/Intervalltimer pro Gerät**
   - Unterschiedliche Pausenlängen je Übung/Gerät einstellbar.
5. **Profil / Stammdaten**
   - Vorname, Nachname, Körpergröße, aktuelles Gewicht.
6. **Logbuch & Historie**
   - Nachvollziehen: wann trainiert, wie oft trainiert, welche Leistung.

## Erweiterung (Phase 2)
- **Gamification für 3x Training pro Woche**
  - Wochenziel-Tracking (z. B. 3/3 Einheiten).
  - Motivation über Streaks, einfache Badges oder Fortschrittsbalken.

## Design-Prinzip
- Sehr schlichtes, graustufiges UI.
- Klare Bedienung mit wenigen Klicks.
- Mobile-first für Pixel 9a (Android), später optional für weitere Geräte.

## Technologievorschlag
### Empfohlen (für Android nativ)
- **Sprache:** Kotlin
- **UI:** Jetpack Compose
- **Architektur:** MVVM + Clean-ish Layering (UI / Domain / Data)
- **Datenbank:** Room (lokal auf dem Gerät)
- **Asynchronität:** Kotlin Coroutines + Flow
- **Dependency Injection:** Hilt (optional für MVP, spätestens bei wachsender App)

### Warum diese Wahl?
- Kotlin + Compose sind aktueller Android-Standard.
- Lokale Datenhaltung mit Room passt perfekt zu „private App ohne Cloud-Zwang“.
- Gute Wartbarkeit bei gleichzeitig einfacher Codebasis.

## Mögliche Alternativen
- **Flutter (Dart):** sinnvoll, wenn später iOS geplant ist.
- **React Native (TypeScript):** sinnvoll, wenn Web-/JS-Erfahrung dominiert.

Für dein aktuelles Ziel (private Android-App, schnell und robust) bleibt **Kotlin + Compose** die beste Option.

## Nicht-Ziele im MVP
- Kein Login/Account-System.
- Keine Cloud-Synchronisierung.
- Keine Social Features.
- Keine Smartwatch-Integration.

## Projektstatus
- ✅ Produktziel und Scope dokumentiert.
- ✅ Entwicklungsleitfaden für die App im Repository angelegt (`Entwicklungsleitfaden.md`).
- ⏳ Nächster Schritt: Fachliche Details finalisieren und Datenmodell/Screen-Flows festzurren.

## Offene Fragen (für die nächste Runde)
1. **Progressionsregel:**
   - Soll bei Erfolg automatisch z. B. `+2,5 kg` erhöht werden, oder pro Gerät individuell (z. B. Beine +5 kg, Arme +1 kg)?
2. **Trainingsplan-Struktur:**
   - Ein fester Ganzkörper-Plan oder mehrere Tage (Push/Pull/Legs etc.)?
3. **Timer-Verhalten:**
   - Auto-Start nach Satzabschluss oder manuell per Start-Button?
4. **Historie-Ansicht:**
   - Reicht eine einfache Liste pro Datum, oder möchtest du Diagramme (z. B. Gewichtsverlauf)?
5. **Gamification in Phase 2:**
   - Eher minimal (Streak + Wochenziel) oder mehr (Punkte, Levels, Belohnungen)?

Wenn du diese Punkte beantwortest, können wir im nächsten Schritt sofort die App-Struktur und die ersten Screens umsetzen.
