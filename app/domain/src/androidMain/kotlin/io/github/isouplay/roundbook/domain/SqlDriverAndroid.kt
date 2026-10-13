package io.github.isouplay.roundbook.domain

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase

/** Storage on Android: a database file in the app's private folder. Creates and migrates the schema. */
fun createAndroidSqlDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(RoundbookDatabase.Schema, context, Roundbook.DATABASE_FILE_NAME)
