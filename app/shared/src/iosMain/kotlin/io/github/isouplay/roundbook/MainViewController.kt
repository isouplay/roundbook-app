package io.github.isouplay.roundbook

import androidx.compose.ui.window.ComposeUIViewController
import io.github.isouplay.roundbook.domain.Roundbook
import io.github.isouplay.roundbook.domain.createIosSqlDriver

/** One storage for the whole app, opened at the first use. */
private val roundbook: Roundbook by lazy { Roundbook(createIosSqlDriver()) }

fun MainViewController() = ComposeUIViewController { App(roundbook) }
