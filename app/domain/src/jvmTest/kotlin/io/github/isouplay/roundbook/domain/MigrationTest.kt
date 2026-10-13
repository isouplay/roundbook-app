package io.github.isouplay.roundbook.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.isouplay.roundbook.domain.entry.DrinkChoice
import io.github.isouplay.roundbook.domain.place.PlaceKind
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A storage of package 2 (schema version 1, file `databases/1.db`) is migrated to version 2: places and
 * events stay, evenings and entries work afterwards.
 */
class MigrationTest {

    private val file: File = File.createTempFile("roundbook-migration", ".db").also { it.delete() }

    @AfterTest
    fun cleanUp() {
        file.delete()
    }

    @Test
    fun aStorageOfPackageTwoIsMigrated() {
        File("src/commonMain/sqldelight/databases/1.db").copyTo(file)
        // As the app of package 2 left it: schema version 1, one device, one place with its event.
        JdbcSqliteDriver("jdbc:sqlite:${file.path}").use { old ->
            old.execute(null, "PRAGMA user_version = 1", 0)
            old.execute(null, "INSERT INTO device(singleton, device_id) VALUES (1, 'device-alt')", 0)
            old.execute(
                null,
                "INSERT INTO event(id, device_id, sequence, recorded_at, type, payload, payload_version) VALUES " +
                    "('e1', 'device-alt', 1, 1000, 'place.created', " +
                    "'{\"placeId\":\"p1\",\"name\":\"Erfundener Keller\",\"kind\":\"LOCAL\"}', 1)",
                0,
            )
            old.execute(null, "INSERT INTO place(id, name, kind, created_at) VALUES ('p1', 'Erfundener Keller', 'LOCAL', 1000)", 0)
        }

        val driver = createJvmSqlDriver(file.path)
        val r = Roundbook(driver)
        assertEquals(Roundbook.SCHEMA_VERSION, userVersion(driver))
        assertEquals(DeviceId("device-alt"), r.deviceId)
        val place = r.places.all().single()
        assertEquals(PlaceId("p1"), place.id)
        assertEquals(PlaceKind.LOCAL, place.kind)

        val evening = (r.evenings.start(place.id) as io.github.isouplay.roundbook.domain.evening.StartResult.Started).evening
        r.entries.book(evening.id, DrinkChoice.Free("Hausrunde", null, null, 250, true))
        r.eventLog.rebuildViews()
        assertEquals(1, r.entries.ofEvening(evening.id).size)
        assertEquals(3L, r.eventLog.count())
        driver.close()
    }
}
