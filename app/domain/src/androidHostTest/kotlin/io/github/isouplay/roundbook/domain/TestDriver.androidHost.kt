package io.github.isouplay.roundbook.domain

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase

// Android host tests run on the JVM, so they use the JDBC driver in memory.
actual fun createTestDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { RoundbookDatabase.Schema.create(it) }
