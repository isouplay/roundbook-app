package io.github.isouplay.roundbook.domain.menu

import io.github.isouplay.roundbook.core.menu.Menu
import io.github.isouplay.roundbook.core.menu.MenuFileReader
import io.github.isouplay.roundbook.core.menu.MenuJson
import io.github.isouplay.roundbook.core.menu.MenuProblem
import io.github.isouplay.roundbook.core.menu.MenuReadResult
import io.github.isouplay.roundbook.domain.MenuVersionId
import io.github.isouplay.roundbook.domain.PlaceId
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase
import io.github.isouplay.roundbook.domain.event.EventLog
import io.github.isouplay.roundbook.domain.event.EventType
import io.github.isouplay.roundbook.domain.event.MenuImported
import io.github.isouplay.roundbook.domain.place.Places
import kotlinx.datetime.LocalDate

/** One version of the menu of a place, without the menu itself (for lists). */
data class MenuVersionInfo(
    val id: MenuVersionId,
    val placeId: PlaceId,
    val validFrom: LocalDate,
    /** Name of the version from the file (e.g. "Herbstkarte"); empty if the file had none. */
    val label: String,
    /** Milliseconds since 1970-01-01 UTC. */
    val recordedAt: Long,
)

data class MenuVersion(
    val info: MenuVersionInfo,
    val menu: Menu,
)

sealed interface MenuImportResult {
    /** A new menu version was recorded. */
    data class Imported(val version: MenuVersionInfo, val drinkCount: Int) : MenuImportResult

    /** The file holds the current menu with the same date: no new version. */
    data class Unchanged(val version: MenuVersionInfo) : MenuImportResult

    /** The file was rejected; nothing was recorded. */
    data class Rejected(val problem: MenuProblem) : MenuImportResult

    data object UnknownPlace : MenuImportResult
}

/**
 * The menus of the places: several versions per place, each valid from a date. The price history
 * stays per place. The current menu is the newest version (latest "valid from"; at the same date the
 * one recorded last).
 */
class MenuVersions internal constructor(
    private val database: RoundbookDatabase,
    private val eventLog: EventLog,
    private val places: Places,
    private val today: () -> LocalDate,
    private val newId: () -> String,
) {
    private val queries get() = database.menuVersionQueries

    /**
     * Reads a menu file (format `roundbook-karte`, format_version 1) and records it as a new version
     * of the place. Valid from the date in the file, otherwise from today.
     */
    fun importMenu(placeId: PlaceId, fileText: String): MenuImportResult {
        if (places.get(placeId) == null) return MenuImportResult.UnknownPlace
        val file = when (val read = MenuFileReader.read(fileText)) {
            is MenuReadResult.Failure -> return MenuImportResult.Rejected(read.problem)
            is MenuReadResult.Success -> read.file
        }
        val validFrom = file.validFrom ?: today()
        val current = current(placeId)
        if (current != null && current.info.validFrom == validFrom && current.menu == file.menu) {
            return MenuImportResult.Unchanged(current.info)
        }
        val id = MenuVersionId(newId())
        eventLog.append(
            EventType.MENU_IMPORTED,
            MenuImported.serializer(),
            MenuImported(
                placeId = placeId,
                menuVersionId = id,
                validFrom = validFrom.toString(),
                label = file.label ?: "",
                menu = file.menu,
            ),
        )
        return MenuImportResult.Imported(get(id)!!.info, file.menu.drinks.count { !it.hidden })
    }

    /** All versions of the place, newest first (price history). */
    fun versions(placeId: PlaceId): List<MenuVersionInfo> =
        queries.selectByPlace(placeId.value, ::infoOf).executeAsList()

    /** The current menu of the place, or null if it has none yet. */
    fun current(placeId: PlaceId): MenuVersion? =
        queries.selectCurrent(placeId.value, ::versionOf).executeAsOneOrNull()

    fun get(id: MenuVersionId): MenuVersion? =
        queries.selectById(id.value, ::versionOf).executeAsOneOrNull()
}

@Suppress("FunctionParameterNaming", "UNUSED_PARAMETER")
internal fun infoOf(
    id: String,
    place_id: String,
    valid_from: String,
    label: String,
    recorded_at: Long,
    device_id: String,
    sequence: Long,
    menu: String,
): MenuVersionInfo = MenuVersionInfo(
    id = MenuVersionId(id),
    placeId = PlaceId(place_id),
    validFrom = LocalDate.parse(valid_from),
    label = label,
    recordedAt = recorded_at,
)

@Suppress("FunctionParameterNaming")
internal fun versionOf(
    id: String,
    place_id: String,
    valid_from: String,
    label: String,
    recorded_at: Long,
    device_id: String,
    sequence: Long,
    menu: String,
): MenuVersion = MenuVersion(
    info = infoOf(id, place_id, valid_from, label, recorded_at, device_id, sequence, menu),
    menu = MenuJson.decode(menu),
)
