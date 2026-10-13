package io.github.isouplay.roundbook.domain

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase

/** Storage on iOS: a database file in the app's private folder. Creates and migrates the schema. */
fun createIosSqlDriver(): SqlDriver = NativeSqliteDriver(RoundbookDatabase.Schema, Roundbook.DATABASE_FILE_NAME)
