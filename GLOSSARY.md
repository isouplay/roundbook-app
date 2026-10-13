# Glossary

Deutscher Begriff aus den Konzepten → Name im Code. Alle Sitzungen nehmen diese Namen.
Wer einen neuen Begriff einführt, trägt ihn hier nach.

| Konzept (deutsch) | Code |
|---|---|
| Abend | `Evening` |
| Buchung, Eintrag | `Entry` |
| Getränk | `Drink` |
| Karte | `Menu` |
| Kategorie | `Category` |
| Mischgetränk | `Mixer` |
| Mischung | `Mix` |
| Aufs Haus | `OnTheHouse` |
| Abrechnung | `Bill` |
| Trinkgeld | `Tip` |
| Promille | `BloodAlcohol` |
| Abzeichen | `Badge` |
| Favoriten | `Favorites` |
| Allergene | `Allergen` |
| Zusatzstoffe | `Additive` |
| Ort | `Place` (Art: `PlaceKind.LOCAL`, `PlaceKind.PRIVATE`, `PlaceKind.PUBLIC`) |
| Lokal (öffentlicher Ort) | `Venue` |
| Wirt | `VenueOwner` |
| Gast | `Guest` |
| Crew (private Gruppe) | `Crew` |
| Gruppe | `Group` |
| Briefkasten | `Mailbox` |
| Kennung | `Id` (z. B. `DrinkId`) |
| Server-Kennung | `ServerId` |
| Signatur | `Signature` |
| Ereignisfolge | `EventLog` |
| Datenauskunft | `DataExport` |
| Papierkorb | `Trash` |
| Dual-Modus | `DualMode` |

## Technische Begriffe (Paket 1)

| Konzept (deutsch) | Code |
|---|---|
| Fassung (Version von App und Server) | `BuildInfo.VERSION` |
| Gesundheits-Endpunkt | `GET /health` |
| Lokaler Kern der App | Modul `:app:domain`, Paket `io.github.isouplay.roundbook.domain` |
| Gemeinsamer Teil App/Server | Modul `:core`, Paket `io.github.isouplay.roundbook.core` |

## Fundament (Paket 2)

| Konzept (deutsch) | Code |
|---|---|
| Ereignis | `Event` (`EventId`) |
| Art eines Ereignisses | `EventType` (`place.created`, `place.renamed`, `menu.imported`) |
| Inhalt eines Ereignisses, Schema-Fassung des Inhalts | `payload`, `payloadVersion` (`PAYLOAD_VERSION`) |
| Ort angelegt, Ort umbenannt, Karte eingelesen | `PlaceCreated`, `PlaceRenamed`, `MenuImported` |
| Gerät, Gerätekennung | `DeviceId` |
| Laufende Nummer (je Gerät) | `sequence` |
| Zeitpunkt der Aufzeichnung | `recordedAt` (Millisekunden seit 1970, UTC) |
| Stelle in der Ereignisfolge, Konfliktregel | `EventPosition` (Zeitpunkt, dann Gerätekennung, dann laufende Nummer) |
| Zwischengespeicherte Sichten | `Projection` (`apply` = fortschreiben, `rebuild` = neu aufbauen) |
| Ereignisse von anderswo übernehmen | `EventLog.receive` |
| Speicher | `RoundbookDatabase` (SQLDelight), Wurzel `Roundbook` |
| Schema-Fassung des Speichers | `Roundbook.SCHEMA_VERSION` |
| Orte (Ablauf) | `Places` |
| Kartenfassung, Preisverlauf | `MenuVersion`, `MenuVersionInfo` (`MenuVersionId`), `MenuVersions.versions` |
| Gültig ab | `validFrom` |
| Name der Fassung (aus der Datei) | `label` |
| Karte einlesen | `MenuVersions.importMenu`, `MenuFileReader` |
| Kartendatei | `MenuFile` (Format `roundbook-karte`, `format_version` 1) |
| Fehler in der Kartendatei | `MenuProblem` (`MenuProblemKind`, Stelle `MenuPlace`, Feld `MenuField`) |
| Wertegrenzen der Karte | `MenuLimits` |
| Größe (eines Getränks) | `Size` (`label`, `liters`, `priceCents`) |
| Preisstufe (`mix_tiers`) | `MixTier` (`MixTierId`); am Getränk `mixTierId` |
| Größe einer Mischung | `MixSize` (`spiritCl` = Schnaps-Anteil in cl) |
| Suchwort einer Mischung | `match` |
| Alkoholgehalt (Vol.-%) | `abv` |
| Hinweis, ausgeblendet, auf ex | `note`, `hidden`, `ex` |
| Zählt als Getränk | `countsAsDrink` |
| Als Mischgetränk (Kategorie bzw. Häkchen am Getränk) | `mixer`, `Drink.isMixer` |
| Kennzeichnung (Allergene und Zusatzstoffe) | `Labelling` |
| Symbol einer Kategorie | `icon` |
| Abschnitt der Kartenansicht | `MenuSection` |
