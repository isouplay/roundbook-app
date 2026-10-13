# Roundbook

Roundbook hält fest, was an einem Abend getrunken wird – im Lokal, zu Hause oder unterwegs, allein
oder mit der Crew. Daraus entstehen Abrechnung, Statistik und eine Promille-Schätzung. Lokale
können ihre Karte per QR-Code bereitstellen.

**Stand: im Aufbau.** Dieses Repo ist der Neuaufbau als App für Android und iOS mit eigenem
Server. Es gibt noch keine Funktionen, nur das Gerüst.

## Aufbau

| Modul | Inhalt |
|---|---|
| `core` | Gemeinsam für App und Server (später Kartenformat, Kennungen, Signatur, Briefkasten-Protokoll). Ziele Android, iOS, JVM. |
| `app/domain` | Lokaler Kern der App (später Ereignisfolge, Rechnungen, Schnittstelle zum Speicher). Keine Oberfläche. Ziele Android, iOS, JVM. |
| `app/shared` | Oberfläche mit Compose Multiplatform, gleich auf Android und iOS. |
| `app/androidApp` | Einstieg der Android-App. |
| `app/iosApp` | Einstieg der iOS-App (Xcode-Projekt), bindet `app/shared` als Framework `Shared` ein. |
| `server` | Server mit Ktor. |

Technik: Kotlin Multiplatform, Compose Multiplatform, Ktor, Gradle (Kotlin DSL), Java 21.

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
