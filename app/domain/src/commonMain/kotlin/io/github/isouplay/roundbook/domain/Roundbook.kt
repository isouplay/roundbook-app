package io.github.isouplay.roundbook.domain

import app.cash.sqldelight.db.SqlDriver
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase
import io.github.isouplay.roundbook.domain.event.EventLog
import io.github.isouplay.roundbook.domain.event.Projection
import io.github.isouplay.roundbook.domain.menu.MenuVersions
import io.github.isouplay.roundbook.domain.place.Places
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The local core of the app on one device: event log, places and menus, stored with SQLDelight.
 * The platform supplies the driver (Android, iOS; JVM for tests).
 */
class Roundbook(
    driver: SqlDriver,
    clock: () -> Long = ::systemNowMillis,
    today: () -> LocalDate = ::systemToday,
    newId: () -> String = ::randomId,
) {
    private val database = RoundbookDatabase(driver)

    /** Id of this installation: created at the first start, then kept. */
    val deviceId: DeviceId = run {
        database.deviceQueries.insertIfMissing(newId())
        DeviceId(database.deviceQueries.selectDeviceId().executeAsOne())
    }

    val eventLog: EventLog = EventLog(database, Projection(database), deviceId, clock, newId)

    val places: Places = Places(database, eventLog, newId)

    val menus: MenuVersions = MenuVersions(database, eventLog, places, today, newId)

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
