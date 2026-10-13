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

## Heute und Buchen (Paket 3)

| Konzept (deutsch) | Code |
|---|---|
| Abende (Ablauf), laufender Abend | `Evenings`, `Evening.isRunning`, `Evenings.running` |
| Abend beginnen, beenden | `Evenings.start` (`StartResult`), `Evenings.end` (`EndResult`) |
| Abend begonnen, Abend beendet (Ereignis) | `EveningStarted` (`evening.started`), `EveningEnded` (`evening.ended`) |
| Datum eines Abends, Tagesgrenze 6 Uhr | `Evenings.eveningDate`, `Evenings.DAY_CUTOFF_HOUR` |
| Buchungen (Ablauf) | `Entries` (`EntryId`) |
| Buchen | `Entries.book` (`BookResult`, `BookProblem`) |
| Was gebucht wird: von der Karte, als Mischung, frei | `DrinkChoice.FromMenu`, `DrinkChoice.AsMix`, `DrinkChoice.Free` |
| Freie Buchung | `DrinkChoice.Free` |
| Momentaufnahme einer Buchung | `EntrySnapshot` |
| Zeitpunkt des Trinkens, gebucht am | `drunkAt`, `addedAt` |
| cl Spirituose (Mischung) | `spiritCl` |
| Mischgetränk der Buchung | `mixerId`, `mixerName` |
| Allgemeine Mischung („mit Mischgetränk“, ohne Suchwort) | `generalMix` |
| Mischpreis zu Größe und Mischgetränk | `mixOffer` (`MixOffer`) |
| Mischgetränke der Karte | `Menu.mixers` |
| Buchung angelegt, geändert, gelöscht (Ereignis) | `EntryAdded` (`entry.added`), `EntryChanged` (`entry.changed`), `EntryDeleted` (`entry.deleted`) |
| Ändern, Uhrzeit ändern | `Entries.change` (`ChangeResult`, `ChangeProblem`), `Entries.timeFor` (`TimeResult`) |
| Gruppe (gleiches Getränk, Größe, Preis), jüngste Buchung | `EntryGroup`, `EntryGroup.youngest` |
| Kopf: Summe, Zahl der Getränke | `EveningSummary` |
| Zähler „heute schon x-mal“ | `Entries.countsByDrink` |
| Zurück, Aktion zurückgenommen (Ereignis) | `Entries.undo`, `Entries.undoable` (`UndoInfo`, `UndoResult`), `ActionUndone` (`action.undone`) |
| Art der Aktion | `ActionKind` (`BOOKED`, `CHANGED`, `DELETED`) |
| Favoritenreihe, Kachel der Reihe | `Favorites.row`, `FavoriteItem` (`OfDrink`, `OfMix`) |
| Schlüssel eines Favoriten | `FavoriteKey` (`OfDrink`, `OfMix`; gespeichert als `code`: Getränke-Kennung oder `mix:[…]`) |
| Anpinnen, lösen (Ereignis) | `Favorites.setPinned`, `FavoritePinned` (`favorite.pinned`), `FavoriteUnpinned` (`favorite.unpinned`) |
| Neu ordnen der Reihe | `Favorites.reorder` |
| Mischung von Schnaps und Mischgetränk: Kennzeichnung zusammen | `combineLabelling` (`CombinedLabelling`) |
| Deutsche Reihenfolge, Suche ohne Umlaute | `GermanOrder`, `sortedByName`, `foldForSearch` |
| Nach Menge (Litern) | `sortedByLiters` |
| Im Hintergrund (Speicher) | `Roundbook.inBackground` |
| Leiste unten: Heute, Ich | `TodayTab`, `MeTab` (Navigation Compose) |
