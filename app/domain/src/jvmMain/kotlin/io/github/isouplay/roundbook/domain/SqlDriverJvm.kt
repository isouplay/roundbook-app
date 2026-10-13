package io.github.isouplay.roundbook.domain

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase

/**
 * Storage on the JVM (fast tests): a database file at [path], or in memory if [path] is null.
 * Creates the schema in a new database and migrates an older one, like the drivers of Android and iOS
 * do; the schema version is kept in SQLite's user_version.
 */
fun createJvmSqlDriver(path: String?): SqlDriver {
    val driver = JdbcSqliteDriver(if (path == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:$path")
    val schema = RoundbookDatabase.Schema
    val current = userVersion(driver)
    if (current == 0L) {
        schema.create(driver)
    } else if (current < schema.version) {
        schema.migrate(driver, current, schema.version)
    }
    driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
    return driver
}

internal fun userVersion(driver: SqlDriver): Long = driver.executeQuery(
    null,
    "PRAGMA user_version",
    { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
    0,
).value
