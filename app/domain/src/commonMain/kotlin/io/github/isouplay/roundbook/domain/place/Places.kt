package io.github.isouplay.roundbook.domain.place

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.isouplay.roundbook.core.menu.MenuLimits
import io.github.isouplay.roundbook.core.menu.codePointLength
import io.github.isouplay.roundbook.domain.PlaceId
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase
import io.github.isouplay.roundbook.domain.event.EventLog
import io.github.isouplay.roundbook.domain.event.EventType
import io.github.isouplay.roundbook.domain.event.PlaceCreated
import io.github.isouplay.roundbook.domain.event.PlaceRenamed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext

/** Kind of a place. In stage 1 only [LOCAL] (on this device only). */
@Serializable
enum class PlaceKind {
    LOCAL,
    PRIVATE,
    PUBLIC,
}

data class Place(
    val id: PlaceId,
    val name: String,
    val kind: PlaceKind,
    /** Milliseconds since 1970-01-01 UTC. */
    val createdAt: Long,
)

enum class PlaceNameProblem {
    EMPTY,
    TOO_LONG,
}

sealed interface PlaceResult {
    data class Done(val place: Place) : PlaceResult

    data class Rejected(val problem: PlaceNameProblem) : PlaceResult

    data object UnknownPlace : PlaceResult
}

/** Places of this device. Changes go through the event log; reading uses the cached view. */
class Places internal constructor(
    private val database: RoundbookDatabase,
    private val eventLog: EventLog,
    private val newId: () -> String,
    private val readContext: CoroutineContext,
) {
    /** All places, by name. */
    fun all(): List<Place> = sortedPlaces(database.placeQueries.selectAll(::placeOf).executeAsList())

    /** All places, by name; updates itself. */
    fun allFlow(): Flow<List<Place>> =
        database.placeQueries.selectAll(::placeOf).asFlow().mapToList(readContext).map(::sortedPlaces)

    private fun sortedPlaces(places: List<Place>): List<Place> =
        places.sortedWith(compareBy({ it.name.lowercase() }, { it.id.value }))

    fun get(id: PlaceId): Place? = database.placeQueries.selectById(id.value, ::placeOf).executeAsOneOrNull()

    fun create(name: String, kind: PlaceKind = PlaceKind.LOCAL): PlaceResult {
        val clean = name.trim()
        problemOf(clean)?.let { return PlaceResult.Rejected(it) }
        val id = PlaceId(newId())
        eventLog.append(EventType.PLACE_CREATED, PlaceCreated.serializer(), PlaceCreated(id, clean, kind))
        return PlaceResult.Done(get(id)!!)
    }

    fun rename(id: PlaceId, name: String): PlaceResult {
        val clean = name.trim()
        problemOf(clean)?.let { return PlaceResult.Rejected(it) }
        if (get(id) == null) return PlaceResult.UnknownPlace
        eventLog.append(EventType.PLACE_RENAMED, PlaceRenamed.serializer(), PlaceRenamed(id, clean))
        return PlaceResult.Done(get(id)!!)
    }

    private fun problemOf(name: String): PlaceNameProblem? = when {
        name.isEmpty() -> PlaceNameProblem.EMPTY
        name.codePointLength() > MenuLimits.NAME_MAX -> PlaceNameProblem.TOO_LONG
        else -> null
    }

    companion object {
        /** Longest name of a place, like the name of a bar on the menu. */
        const val NAME_MAX: Int = MenuLimits.NAME_MAX
    }
}

@Suppress("FunctionParameterNaming")
internal fun placeOf(id: String, name: String, kind: String, created_at: Long): Place = Place(
    id = PlaceId(id),
    name = name,
    kind = PlaceKind.entries.firstOrNull { it.name == kind } ?: PlaceKind.LOCAL,
    createdAt = created_at,
)
