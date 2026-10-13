package io.github.isouplay.roundbook.domain

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase

actual fun createTestDriver(): SqlDriver = inMemoryDriver(RoundbookDatabase.Schema)
