package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.core.BuildInfo

/** Placeholder until the event log arrives. Shows that :app:domain sees :core. */
object DomainInfo {
    val version: String get() = BuildInfo.VERSION
}
