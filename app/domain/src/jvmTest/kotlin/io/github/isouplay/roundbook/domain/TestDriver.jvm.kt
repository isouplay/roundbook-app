package io.github.isouplay.roundbook.domain

import app.cash.sqldelight.db.SqlDriver

actual fun createTestDriver(): SqlDriver = createJvmSqlDriver(null)
