package io.github.isouplay.roundbook

import android.app.Application
import io.github.isouplay.roundbook.domain.Roundbook
import io.github.isouplay.roundbook.domain.createAndroidSqlDriver

/** Holds the one storage of the app, opened at the first use. */
class RoundbookApplication : Application() {
    val roundbook: Roundbook by lazy { Roundbook(createAndroidSqlDriver(this)) }
}
