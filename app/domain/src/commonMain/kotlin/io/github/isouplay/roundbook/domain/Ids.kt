package io.github.isouplay.roundbook.domain

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class PlaceId(val value: String)

/** One version of the menu of a place (price history). */
@Serializable
@JvmInline
value class MenuVersionId(val value: String)

@Serializable
@JvmInline
value class EventId(val value: String)

/** Id of this installation, random, created at the first start. */
@Serializable
@JvmInline
value class DeviceId(val value: String)

/** One evening at a place. */
@Serializable
@JvmInline
value class EveningId(val value: String)

/** One entry (booking) of an evening. */
@Serializable
@JvmInline
value class EntryId(val value: String)
