package io.github.isouplay.roundbook.domain

import app.cash.sqldelight.db.SqlDriver
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase
import io.github.isouplay.roundbook.domain.entry.Entries
import io.github.isouplay.roundbook.domain.evening.Evenings
import io.github.isouplay.roundbook.domain.event.EventLog
import io.github.isouplay.roundbook.domain.event.Projection
import io.github.isouplay.roundbook.domain.favorite.Favorites
import io.github.isouplay.roundbook.domain.menu.MenuVersions
import io.github.isouplay.roundbook.domain.place.Places
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The local core of the app on one device: event log, places, menus, evenings, entries and favourites,
 * stored with SQLDelight. The platform supplies the driver (Android, iOS; JVM for tests).
 *
 * The functions of the parts work directly on the storage and are meant for a background thread: the UI
 * reads through the `…Flow()` functions (they run on [dispatcher] and update themselves) and writes through
 * [inBackground].
 */
class Roundbook(
    driver: SqlDriver,
    clock: () -> Long = ::systemNowMillis,
    today: () -> LocalDate = ::systemToday,
    newId: () -> String = ::randomId,
    timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val database = RoundbookDatabase(driver)

    /** One writer at a time: an event takes the running number after the last one. */
    private val writeLock = Mutex()

    /** Id of this installation: created at the first start, then kept. */
    val deviceId: DeviceId = run {
        database.deviceQueries.insertIfMissing(newId())
        DeviceId(database.deviceQueries.selectDeviceId().executeAsOne())
    }

    val eventLog: EventLog = EventLog(database, Projection(database), deviceId, clock, newId)

    val places: Places = Places(database, eventLog, newId, dispatcher)

    val menus: MenuVersions = MenuVersions(database, eventLog, places, today, newId, dispatcher)

    val favorites: Favorites = Favorites(database, eventLog, clock, today, timeZone, dispatcher)

    val evenings: Evenings = Evenings(database, eventLog, places, favorites, newId, dispatcher)

    val entries: Entries = Entries(database, eventLog, evenings, menus, clock, newId, dispatcher)

    /** Runs [block] off the main thread, one at a time. For everything the UI does with the storage. */
    suspend fun <T> inBackground(block: Roundbook.() -> T): T = withContext(dispatcher) {
        writeLock.withLock { block() }
    }

    companion object {
        /** Schema version of the storage (SQLite user_version). Rises with each migration. */
        val SCHEMA_VERSION: Long get() = RoundbookDatabase.Schema.version

        /** Name of the database file on the device. */
        const val DATABASE_FILE_NAME: String = "roundbook.db"
    }
}

@OptIn(ExperimentalTime::class)
internal fun systemNowMillis(): Long = Clock.System.now().toEpochMilliseconds()

@OptIn(ExperimentalTime::class)
internal fun systemToday(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

@OptIn(ExperimentalUuidApi::class)
internal fun randomId(): String = Uuid.random().toString()
