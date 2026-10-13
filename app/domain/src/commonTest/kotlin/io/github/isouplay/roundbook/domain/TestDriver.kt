package io.github.isouplay.roundbook.domain

import app.cash.sqldelight.db.SqlDriver

/** A fresh, empty database in memory with the current schema. */
expect fun createTestDriver(): SqlDriver
