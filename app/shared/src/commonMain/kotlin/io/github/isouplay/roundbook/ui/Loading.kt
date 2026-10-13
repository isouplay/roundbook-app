package io.github.isouplay.roundbook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** A value read from the storage; null as a whole means "still loading" (not the same as a value null). */
data class Loaded<T>(val value: T)

/** Collects a flow of the storage; the state is null until the first value arrived. */
@Composable
fun <T> Flow<T>.collectLoaded(): State<Loaded<T>?> = remember(this) { map { Loaded(it) } }.collectAsState(null)

/** Now, milliseconds since 1970-01-01 UTC. */
@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
