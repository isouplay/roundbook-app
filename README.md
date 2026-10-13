# Roundbook

Roundbook hält fest, was an einem Abend getrunken wird – im Lokal, zu Hause oder unterwegs, allein
oder mit der Crew. Daraus entstehen Abrechnung, Statistik und eine Promille-Schätzung. Lokale
können ihre Karte per QR-Code bereitstellen.

**Stand: im Aufbau.** Dieses Repo ist der Neuaufbau als App für Android und iOS mit eigenem
Server. Bisher kann die App einen Abend an einem Ort beginnen und beenden, Getränke von der Karte
buchen (auch Mischungen, aufs Haus und frei eingegeben), Favoriten je Ort zeigen, Buchungen ändern
(auch die Uhrzeit), löschen und für fünf Minuten zurücknehmen. Unten gibt es zwei Bereiche:
**Heute** und **Ich**; unter Ich stehen die Orte mit ihren Karten (Kartendatei der alten App,
`roundbook-karte`, Format 1). Alles liegt nur auf dem Gerät.

## Aufbau

| Modul | Inhalt |
|---|---|
| `core` | Gemeinsam für App und Server: Kartenmodell und Lesen der Kartendatei (später Kennungen, Signatur, Briefkasten-Protokoll). Ziele Android, iOS, JVM. |
| `app/domain` | Lokaler Kern der App: Ereignisfolge, Speicher (SQLDelight), Orte und Karten mit Preisverlauf, Abende, Buchungen, Favoriten (später Rechnungen). Keine Oberfläche. Ziele Android, iOS, JVM. |
| `app/shared` | Oberfläche mit Compose Multiplatform, gleich auf Android und iOS. |
| `app/androidApp` | Einstieg der Android-App. |
| `app/iosApp` | Einstieg der iOS-App (Xcode-Projekt), bindet `app/shared` als Framework `Shared` ein. |
| `server` | Server mit Ktor. |

Technik: Kotlin Multiplatform, Compose Multiplatform, Navigation Compose, SQLDelight (mit Coroutines),
kotlinx.serialization, kotlinx-datetime, Ktor, Gradle (Kotlin DSL), Java 21.

## Bauen

- Android-App: `./gradlew :app:androidApp:assembleDebug` oder in Android Studio starten.
- Server: `./gradlew :server:run`, danach antwortet `http://localhost:8080/health` mit `ok` und der Fassung.
- iOS-App: braucht macOS mit Xcode; `app/iosApp` in Xcode öffnen. Ohne Mac baut GitHub Actions die App.

Unter Windows statt `./gradlew` jeweils `.\gradlew.bat`.

## Prüfen

- `verifyQuick` – Tests von `core`, `app/domain` (JVM) und `server`, in Sekunden.
- `verifyAll` – alle Tests, die auf dem Rechner möglich sind, plus Android-Build (Debug).

```
./gradlew verifyAll
```

GitHub Actions (`.github/workflows/verify.yml`) prüft bei jedem Push und Pull-Request auf `main`:
unter Linux `verifyAll`, unter macOS die iOS-Tests im Simulator und den Build der iOS-App.

## Mitmachen

Regeln für Beiträge stehen in [`START-HIER.md`](START-HIER.md), die Namen im Code in
[`GLOSSARY.md`](GLOSSARY.md). Im Code ist alles englisch, Texte für Nutzer sind deutsch und liegen
als Ressourcen vor.

## Lizenz

MIT, siehe [`LICENSE`](LICENSE).
