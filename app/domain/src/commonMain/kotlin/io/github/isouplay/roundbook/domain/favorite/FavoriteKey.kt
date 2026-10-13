package io.github.isouplay.roundbook.domain.favorite

import io.github.isouplay.roundbook.core.menu.DrinkId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * What a favourite is, and what bookings are counted by (konzept-kennungen.md): a drink is its id; a mix is
 * spirit id plus mixer id ("egal / anderes" without mixer counts for itself). Renaming a drink or moving it
 * to another category changes nothing.
 */
sealed interface FavoriteKey {
    /** Stored form: the drink id, or `mix:["spirit id","mixer id"]` (empty mixer id for "egal / anderes"). */
    val code: String

    data class OfDrink(val drinkId: DrinkId) : FavoriteKey {
        override val code: String get() = drinkId.value
    }

    data class OfMix(val spiritId: DrinkId, val mixerId: DrinkId?) : FavoriteKey {
        override val code: String
            get() = MIX_PREFIX + JsonArray(listOf(JsonPrimitive(spiritId.value), JsonPrimitive(mixerId?.value ?: ""))).toString()
    }

    companion object {
        const val MIX_PREFIX: String = "mix:"

        /** Prefix of a mix in the old app; read as well, never written. */
        const val OLD_MIX_PREFIX: String = "mischung:"

        /**
         * Reads a stored key. A mix of the old app (`mischung:[…]`) is understood if it already holds ids
         * (two parts); older forms with names or a size cannot be mapped without the old menu – null.
         */
        fun parse(code: String): FavoriteKey? {
            val body = when {
                code.startsWith(MIX_PREFIX) -> code.removePrefix(MIX_PREFIX)
                code.startsWith(OLD_MIX_PREFIX) -> code.removePrefix(OLD_MIX_PREFIX)
                else -> return if (code.isEmpty() || code.contains('/') || code.contains(':')) null else OfDrink(DrinkId(code))
            }
            val parts = try {
                Json.parseToJsonElement(body).jsonArray.map { it.jsonPrimitive.contentOrNull }
            } catch (e: IllegalArgumentException) {
                return null
            }
            if (parts.size != 2 || parts.any { it == null }) return null
            val spirit = parts[0]!!
            val mixer = parts[1]!!
            if (spirit.isEmpty() || spirit.contains('/')) return null
            return OfMix(DrinkId(spirit), if (mixer.isEmpty()) null else DrinkId(mixer))
        }
    }
}
