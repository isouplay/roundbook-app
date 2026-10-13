# Start hier – Regeln für Bau-Sitzungen

Diese Datei gilt für jede Sitzung, die in diesem Repo baut. Geplant wird lokal in `claude/`
(nicht im Repo); dort stehen Konzepte, Pakete und die Bau-Chronik `claude/gebaut-app.md`.

## Vor dem Bauen

1. Das Konzept des Pakets lesen, dazu `GLOSSARY.md`.
2. Prüfen, ob die Sitzung `gradlew` ausführen kann und die Gradle-Quellen erreicht
   (Maven Central, Google Maven, Gradle Plugin Portal). Wenn nicht: Code schreiben, ohne selbst zu
   bauen, und das im Bericht sagen. Geprüft wird dann bei Florian.

## Regeln

- **Bauen, was vorgegeben ist.** Alternativen notieren, nicht eigenmächtig umsetzen.
- **Module und ihre Grenzen:**
  - `core` – gemeinsam für App und Server. Keine Oberfläche, keine Plattform-Bibliotheken.
  - `app/domain` – lokaler Kern der App. Keine Oberfläche. Hängt von `core` ab.
  - `app/shared` – Oberfläche (Compose). Hängt von `app/domain` ab.
  - `app/androidApp`, `app/iosApp` – nur Einstiege der Plattformen.
  - `server` – Ktor. Hängt von `core` ab.
  - Die Modulnamen bleiben; `app/iosApp` hängt an `:app:shared`.
- **Sprache:** Im Code ist alles englisch (Bezeichner, Kommentare, Test-Namen). Die Namen kommen
  aus `GLOSSARY.md`; neue Begriffe trägt die Sitzung nach, die sie einführt.
- **Texte für Nutzer** sind deutsch und liegen als Ressourcen vor
  (`app/shared/src/commonMain/composeResources/values/strings.xml`), nicht im Code verstreut.
- **Deutsch** sind Commit-Texte, `README.md` und diese Datei.
- **Tests:** Jede Rechnung und jede Regel bekommt Tests in dem Modul, in dem sie liegt.
  Schnelle Tests laufen auf der JVM (`core`, `app/domain`).
- **Keine Geheimnisse** im Repo: Signierschlüssel und Zugangsdaten nur als GitHub-Secrets.
- **Keine echten Daten** im Repo: Prüffälle nur erfunden oder vollständig anonymisiert
  (Namen, Zeiten, Fotos), auch auf Bildschirmfotos.
- **Fremde Dateien** (Schriften, Bilder, Bibliotheken) nur mit Lizenz, die zu MIT passt.

## Prüfen

- `.\gradlew.bat verifyQuick` – Tests von `core`, `app/domain` (JVM) und `server`.
- `.\gradlew.bat verifyAll` – alle Tests, die auf dem Rechner möglich sind, plus Android-Build.
- iOS-Tests und iOS-Build laufen in GitHub Actions unter macOS.

## Ablauf am Ende

1. Bauen und, soweit möglich, selbst prüfen.
2. Zuerst nur den Prüfbefehl für Florian ausgeben und auf seine Antwort warten:
   `cd C:\claude_projects\roundbook-app; .\gradlew.bat verifyAll` (PowerShell).
3. Bei grünem Lauf: Bericht in `claude/gebaut-app.md` schreiben, danach den PowerShell-Befehl
   für den Commit ausgeben – ohne doppelte Anführungszeichen und ohne `$` im Text.
4. Nach dem Push prüft Florian, ob GitHub Actions unter Linux und macOS grün sind. Schlägt ein
   Lauf fehl, geht der Fehler an die Planung zurück.
