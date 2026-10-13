package io.github.isouplay.roundbook.core.menu

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** A menu file after reading: the menu, plus date and label from the envelope if given. */
data class MenuFile(
    val menu: Menu,
    /** "valid from" of the file; null if the file has none (the caller then takes today). */
    val validFrom: LocalDate?,
    /** Name of the version in the file (e.g. "Kartencheck"); null if none. */
    val label: String?,
)

sealed interface MenuReadResult {
    data class Success(val file: MenuFile) : MenuReadResult

    data class Failure(val problem: MenuProblem) : MenuReadResult
}

/**
 * Reads a menu file of the old app: format `roundbook-karte`, `format_version` 1
 * (export `/api/menu/export`), envelope with `menu` in the flat format v2. A bare menu (v2, without
 * envelope) is read too, like the old app does.
 *
 * Checks like the old app (`menu.pruefe_karte`, `menu._normalize_menu`): limits and required fields;
 * the first problem stops the reading, nothing is read half. Unknown fields are ignored, so older
 * and newer exports can be read. The file is recognised by its shape, not by its `format` label.
 */
object MenuFileReader {

    private val json = Json { isLenient = false }

    fun read(text: String): MenuReadResult = try {
        MenuReadResult.Success(readOrThrow(text))
    } catch (e: MenuProblemException) {
        MenuReadResult.Failure(e.problem)
    }

    private fun readOrThrow(text: String): MenuFile {
        if (text.length > MenuLimits.FILE_BYTES_MAX) {
            fail(MenuProblemKind.FILE_TOO_LARGE, MenuPlace.File, limit = MenuLimits.FILE_BYTES_MAX.toDouble())
        }
        val root = try {
            json.parseToJsonElement(text.removePrefix("﻿"))
        } catch (e: SerializationException) {
            fail(MenuProblemKind.NOT_JSON, MenuPlace.File)
        } catch (e: IllegalArgumentException) {
            fail(MenuProblemKind.NOT_JSON, MenuPlace.File)
        }
        if (root !is JsonObject) fail(MenuProblemKind.NOT_RECOGNIZED, MenuPlace.File)
        if ("versions" in root) fail(MenuProblemKind.PRICE_HISTORY_NOT_SUPPORTED, MenuPlace.File)

        val formatVersion = root["format_version"]
        if (formatVersion.isNumber()) {
            val v = formatVersion.asDouble()
            if (v > 1) fail(MenuProblemKind.FORMAT_VERSION_TOO_NEW, MenuPlace.File, number = v)
        }

        val menuElement: JsonElement? = when {
            "menu" in root -> root["menu"]
            "drinks" in root || "categories" in root -> root
            else -> fail(MenuProblemKind.NOT_RECOGNIZED, MenuPlace.File)
        }
        if (menuElement !is JsonObject) fail(MenuProblemKind.NOT_AN_OBJECT, MenuPlace.Menu)

        val validFrom = readDate(root["valid_from"])
        val label = root["label"]
        checkText(label, MenuPlace.File, MenuField.VERSION_LABEL, MenuLimits.NAME_MAX)

        val menu = readMenu(menuElement)
        return MenuFile(
            menu = menu,
            validFrom = validFrom,
            label = label.textOrNull()?.trim()?.ifEmpty { null },
        )
    }

    private fun readDate(element: JsonElement?): LocalDate? {
        if (element.isNone() || (element.isString() && element.textOrNull()!!.isEmpty())) return null
        if (!element.isString()) fail(MenuProblemKind.INVALID_DATE, MenuPlace.File, MenuField.VALID_FROM)
        val raw = element.textOrNull()!!.trim()
        // The old app accepts any ISO date; a date with time ("2026-10-12T19:50") counts with its date.
        val datePart = if (raw.length > 10 && (raw[10] == 'T' || raw[10] == ' ')) raw.substring(0, 10) else raw
        return try {
            LocalDate.parse(datePart)
        } catch (e: IllegalArgumentException) {
            fail(MenuProblemKind.INVALID_DATE, MenuPlace.File, MenuField.VALID_FROM, text = shortForMessage(raw))
        }
    }

    // ------------------------------------------------------------------ the menu (flat format v2)

    private fun readMenu(menu: JsonObject): Menu {
        if ("drinks" !in menu && isNested(menu)) {
            fail(MenuProblemKind.NESTED_FORMAT_NOT_SUPPORTED, MenuPlace.File)
        }
        check(menu)
        return build(menu)
    }

    /** The older nested format (drinks inside categories, prices at categories or tiers). */
    private fun isNested(menu: JsonObject): Boolean {
        if ("tiers" in menu) return true
        val categories = menu["categories"] as? JsonArray ?: return false
        return categories.any { c -> c is JsonObject && ("drinks" in c || "sizes" in c || "tier" in c) }
    }

    /** Limits and types, in the order of the old app (`pruefe_karte`). */
    private fun check(menu: JsonObject) {
        checkText(menu["bar"], MenuPlace.Menu, MenuField.BAR_NAME, MenuLimits.NAME_MAX)
        checkText(menu["currency"], MenuPlace.Menu, MenuField.CURRENCY, MenuLimits.LABEL_MAX)

        for (c in checkList(menu["categories"], MenuPlace.Menu, MenuField.CATEGORIES, MenuLimits.CATEGORIES_MAX)) {
            val place = MenuPlace.Category(placeName(c["name"], c["id"]))
            checkId(c["id"], place)
            checkText(c["name"], place, MenuField.NAME, MenuLimits.NAME_MAX)
            checkText(c["icon"], place, MenuField.ICON, MenuLimits.ICON_MAX)
            checkBoolean(c["mixer"], place, MenuField.MIXER)
        }

        // Like `flat.get("mix_tiers") or {}`: a missing or empty value means no tiers.
        val tiersElement = menu["mix_tiers"]
        val tiers: JsonElement = if (tiersElement.isTruthy()) tiersElement!! else JsonObject(emptyMap())

        for (d in checkList(menu["drinks"], MenuPlace.Menu, MenuField.DRINKS, MenuLimits.DRINKS_MAX)) {
            val place = MenuPlace.Drink(placeName(d["name"], d["id"]))
            checkText(d["name"], place, MenuField.NAME, MenuLimits.NAME_MAX)
            checkId(d["id"], place)
            checkText(d["note"], place, MenuField.NOTE, MenuLimits.NOTE_MAX)
            checkNumber(d["abv"], place, MenuField.ABV, MenuLimits.ABV_MAX)
            checkSizes(d["sizes"], place, withSpirit = false)
            checkBoolean(d["mixer"], place, MenuField.MIXER)
            checkLabelling(d, place)
            val tier = d["tier"]
            if (!tier.isNone() && !(tier.isString() && tier.textOrNull() == "")) {
                val known = tier.isString() && tiers is JsonObject && tier.textOrNull()!! in tiers
                if (!known) fail(MenuProblemKind.UNKNOWN_MIX_TIER, place, text = shortForMessage(tier.pyText()))
            }
        }

        if (tiers !is JsonObject) fail(MenuProblemKind.NOT_AN_OBJECT, MenuPlace.Menu, MenuField.MIX_TIERS)
        if (tiers.size > MenuLimits.MIX_TIERS_MAX) {
            fail(
                MenuProblemKind.TOO_MANY, MenuPlace.Menu, MenuField.MIX_TIERS,
                count = tiers.size, limit = MenuLimits.MIX_TIERS_MAX.toDouble(),
            )
        }
        for ((tierId, t) in tiers) {
            val place = MenuPlace.MixTier(shortForMessage(tierId))
            checkId(JsonPrimitive(tierId), place)
            if (t !is JsonObject) fail(MenuProblemKind.NOT_AN_OBJECT, place)
            checkText(t["name"], place, MenuField.NAME, MenuLimits.NAME_MAX)
            checkText(t["mix_category"], place, MenuField.CATEGORY, MenuLimits.NAME_MAX)
            for (m in checkList(t["mixes"], place, MenuField.MIXES, MenuLimits.MIXES_MAX)) {
                val mixPlace = MenuPlace.Mix(place, placeName(m["name"], m["id"]))
                checkId(m["id"], mixPlace)
                checkText(m["name"], mixPlace, MenuField.NAME, MenuLimits.NAME_MAX)
                checkText(m["match"], mixPlace, MenuField.MATCH, MenuLimits.NAME_MAX)
                checkText(m["category"], mixPlace, MenuField.CATEGORY, MenuLimits.NAME_MAX)
                checkSizes(m["sizes"], mixPlace, withSpirit = true)
            }
        }
    }

    private fun checkSizes(sizes: JsonElement?, parent: MenuPlace, withSpirit: Boolean) {
        checkList(sizes, parent, MenuField.SIZES, MenuLimits.SIZES_MAX).forEachIndexed { i, size ->
            val label = size["label"]
            val hasLabel = !label.isNone() && !(label.isString() && label.textOrNull() == "")
            val place = MenuPlace.Size(parent, if (hasLabel) shortForMessage(label.pyText()) else null, i + 1)
            checkText(label, place, MenuField.SIZE_LABEL, MenuLimits.LABEL_MAX)
            val price = size["price"]
            if (price.isNone() || (price.isString() && price.textOrNull() == "")) {
                fail(MenuProblemKind.PRICE_MISSING, place, MenuField.PRICE)
            }
            val value = checkNumber(price, place, MenuField.PRICE, MenuLimits.PRICE_MAX)!!
            centsOf(value, place)
            val liters = checkNumber(size["l"], place, MenuField.VOLUME, MenuLimits.LITERS_MAX)
            if (withSpirit || "spirit_cl" in size) {
                val cl = checkNumber(size["spirit_cl"], place, MenuField.SPIRIT, MenuLimits.SPIRIT_CL_MAX)
                if (cl != null && cl != 0.0 && liters != null && liters != 0.0 && cl > liters * 100 + 1e-9) {
                    fail(MenuProblemKind.SPIRIT_EXCEEDS_VOLUME, place, MenuField.SPIRIT, number = cl, secondNumber = liters)
                }
            }
        }
    }

    private fun checkLabelling(drink: JsonObject, place: MenuPlace) {
        checkCodes(drink["allergene"], place, MenuField.ALLERGENS, MenuProblemKind.UNKNOWN_ALLERGEN, MenuProblemKind.DUPLICATE_ALLERGEN) {
            Allergen.fromCode(it) != null
        }
        checkCodes(drink["zusatzstoffe"], place, MenuField.ADDITIVES, MenuProblemKind.UNKNOWN_ADDITIVE, MenuProblemKind.DUPLICATE_ADDITIVE) {
            Additive.fromCode(it) != null
        }
    }

    /** A list of known codes, each at most once. Unknown codes are rejected, not left out. */
    private fun checkCodes(
        element: JsonElement?,
        place: MenuPlace,
        field: MenuField,
        unknown: MenuProblemKind,
        duplicate: MenuProblemKind,
        isKnown: (String) -> Boolean,
    ) {
        if (element.isNone()) return
        if (element !is JsonArray) fail(MenuProblemKind.NOT_A_LIST, place, field)
        val seen = mutableSetOf<String>()
        for (x in element) {
            val code = if (x.isString()) x.textOrNull()!! else null
            if (code == null || !isKnown(code)) fail(unknown, place, field, text = shortForMessage(x.pyText()))
            if (!seen.add(code)) fail(duplicate, place, field, text = shortForMessage(code))
        }
    }

    // ------------------------------------------------------------------ building the model

    /** The checked file as model, like `_normalize_menu` of the old app (consistency rules). */
    private fun build(menu: JsonObject): Menu {
        val categories = mutableListOf<Category>()
        val categoryIds = mutableSetOf<String>()
        objects(menu["categories"]).forEachIndexed { ci, c ->
            val id = c["id"].takeIf { it.isTruthy() }?.pyText() ?: "cat$ci"
            if (!categoryIds.add(id)) fail(MenuProblemKind.DUPLICATE_CATEGORY_ID, MenuPlace.Menu, text = shortForMessage(id))
            categories += Category(
                id = CategoryId(id),
                name = c["name"].textOrNull() ?: id,
                icon = c["icon"].textOrNull() ?: "",
                countsAsDrink = if ("counts_as_drink" in c) c["counts_as_drink"].isTruthy() else true,
                mixer = c["mixer"].isTruthy(),
            )
        }

        val tiersElement = menu["mix_tiers"] as? JsonObject ?: JsonObject(emptyMap())
        val tiers = tiersElement.map { (tierId, t) -> buildTier(tierId, t as JsonObject) }

        val drinks = mutableListOf<Drink>()
        val drinkIds = mutableSetOf<String>()
        objects(menu["drinks"]).forEachIndexed { di, d ->
            val name = (d["name"].textOrNull() ?: "").trim()
            if (name.isEmpty()) fail(MenuProblemKind.DRINK_WITHOUT_NAME, MenuPlace.DrinkNumber(di + 1))
            val place = MenuPlace.Drink(shortForMessage(name))

            val categoryRef = d["category"]
            val categoryId = if (categoryRef.isString()) categoryRef.textOrNull()!! else null
            if (categoryId == null || categoryId !in categoryIds) {
                fail(
                    MenuProblemKind.UNKNOWN_CATEGORY, place, MenuField.CATEGORY,
                    text = shortForMessage(if (categoryRef.isNone()) "" else categoryRef.pyText()),
                )
            }

            val id = d["id"].takeIf { it.isTruthy() }?.pyText() ?: slugOf(name)
            if (!drinkIds.add(id)) fail(MenuProblemKind.DUPLICATE_DRINK_ID, place, text = shortForMessage(id))

            val tierRef = d["tier"].takeIf { it.isTruthy() }?.textOrNull()
            val tier = tierRef?.let { ref -> tiers.firstOrNull { it.id.value == ref } }
            if (tierRef != null && tier == null) fail(MenuProblemKind.UNKNOWN_MIX_TIER, place, text = shortForMessage(tierRef))

            val sizes = objects(d["sizes"]).mapIndexed { i, s ->
                val label = s["label"].textOrNull() ?: ""
                Size(
                    label = label,
                    liters = number(s["l"]),
                    priceCents = centsOf(number(s["price"])!!, MenuPlace.Size(place, label.ifEmpty { null }, i + 1)),
                )
            }
            if (sizes.map { it.label }.toSet().size != sizes.size) {
                fail(MenuProblemKind.DUPLICATE_SIZE_LABEL, place, MenuField.SIZE_LABEL)
            }
            val mixSizes = tier?.mixes?.sumOf { it.sizes.size } ?: 0
            if (sizes.isEmpty() && mixSizes == 0) fail(MenuProblemKind.DRINK_WITHOUT_PRICE, place, MenuField.PRICE)

            val mixer = d["mixer"]
            drinks += Drink(
                id = DrinkId(id),
                name = name,
                categoryId = CategoryId(categoryId),
                abv = number(d["abv"]) ?: 0.0,
                sizes = sizes,
                note = d["note"].textOrNull() ?: "",
                hidden = d["hidden"].isTruthy(),
                mixTierId = tier?.id,
                mixer = if (mixer.isBoolean()) (mixer as JsonPrimitive).booleanOrNull else null,
                ex = d["ex"].isTruthy(),
                labelling = labellingOf(d),
            )
        }

        return Menu(
            bar = menu["bar"].textOrNull() ?: "",
            currency = menu["currency"].textOrNull()?.ifEmpty { null } ?: "EUR",
            categories = categories,
            mixTiers = tiers,
            drinks = drinks,
        )
    }

    private fun buildTier(tierId: String, t: JsonObject): MixTier {
        val place = MenuPlace.MixTier(shortForMessage(tierId))
        val mixes = objects(t["mixes"]).map { m ->
            val name = m["name"]
            if (name.isNone()) fail(MenuProblemKind.MIX_WITHOUT_NAME, place)
            val mixName = name.pyText()
            val mixPlace = MenuPlace.Mix(place, shortForMessage(mixName))
            Mix(
                id = MixId(m["id"].takeIf { it.isTruthy() }?.pyText() ?: mixName),
                name = mixName,
                match = m["match"].textOrNull() ?: "",
                category = m["category"].textOrNull(),
                sizes = objects(m["sizes"]).mapIndexed { i, s ->
                    val label = s["label"].textOrNull() ?: ""
                    MixSize(
                        label = label,
                        liters = number(s["l"]),
                        priceCents = centsOf(number(s["price"])!!, MenuPlace.Size(mixPlace, label.ifEmpty { null }, i + 1)),
                        spiritCl = number(s["spirit_cl"]) ?: 0.0,
                    )
                },
            )
        }
        return MixTier(
            id = MixTierId(tierId),
            name = t["name"].textOrNull() ?: tierId,
            mixCategory = t["mix_category"].textOrNull(),
            mixes = mixes,
        )
    }

    private fun labellingOf(drink: JsonObject): Labelling? {
        val allergens = drink["allergene"]
        val additives = drink["zusatzstoffe"]
        if (allergens.isNone() && additives.isNone()) return null
        return Labelling(
            allergens = (allergens as? JsonArray).orEmpty().mapNotNull { it.textOrNull()?.let(Allergen::fromCode) },
            additives = (additives as? JsonArray).orEmpty().mapNotNull { it.textOrNull()?.let(Additive::fromCode) },
        )
    }

    // ------------------------------------------------------------------ checks (old app: _zahl_pruefen & co.)

    private fun checkText(element: JsonElement?, place: MenuPlace, field: MenuField, max: Int) {
        if (element.isNone()) return
        if (!(element.isString() || element.isNumber())) fail(MenuProblemKind.NOT_TEXT, place, field)
        if (element.pyText().codePointLength() > max) {
            fail(MenuProblemKind.TEXT_TOO_LONG, place, field, limit = max.toDouble())
        }
    }

    /** Only ids that are set; a missing id is built from the name. */
    private fun checkId(element: JsonElement?, place: MenuPlace) {
        if (element.isNone() || (element.isString() && element.textOrNull() == "")) return
        if (!element.isString() || !MenuLimits.ID_PATTERN.matches(element.textOrNull()!!)) {
            fail(MenuProblemKind.INVALID_ID, place, text = shortForMessage(element.pyText()))
        }
    }

    private fun checkList(element: JsonElement?, place: MenuPlace, field: MenuField, max: Int): List<JsonObject> {
        if (element.isNone()) return emptyList()
        if (element !is JsonArray) fail(MenuProblemKind.NOT_A_LIST, place, field)
        if (element.size > max) {
            fail(MenuProblemKind.TOO_MANY, place, field, count = element.size, limit = max.toDouble())
        }
        return element.map { it as? JsonObject ?: fail(MenuProblemKind.NOT_AN_OBJECT, place, field) }
    }

    /** Number or empty. Text that is no number, NaN/Infinity and booleans are problems. */
    private fun checkNumber(element: JsonElement?, place: MenuPlace, field: MenuField, max: Double): Double? {
        if (element.isNone()) return null
        if (element.isBoolean() || element !is JsonPrimitive) fail(MenuProblemKind.NOT_A_NUMBER, place, field)
        val value: Double = if (element.isString) {
            val raw = element.content
            if (raw.isEmpty()) return null
            parseDecimal(raw) ?: fail(MenuProblemKind.NOT_A_NUMBER, place, field, text = raw.takeCodePoints(20))
        } else {
            element.doubleOrNull ?: fail(MenuProblemKind.NOT_A_NUMBER, place, field)
        }
        if (!value.isFinite()) fail(MenuProblemKind.NOT_A_NUMBER, place, field)
        if (value < 0 || value > max) {
            fail(MenuProblemKind.NUMBER_OUT_OF_RANGE, place, field, number = value, limit = max)
        }
        return value
    }

    /** Yes/no field: missing or true/false – "ja" or 1 from a file is not interpreted. */
    private fun checkBoolean(element: JsonElement?, place: MenuPlace, field: MenuField) {
        if (!element.isNone() && !element.isBoolean()) fail(MenuProblemKind.NOT_A_BOOLEAN, place, field)
    }

    /**
     * Price in whole cents. The old app stored prices as decimal numbers; more than two decimals
     * would be silently rounded here, so they are rejected instead.
     */
    private fun centsOf(price: Double, place: MenuPlace): Long {
        val scaled = price * 100
        val cents = kotlin.math.round(scaled)
        if (kotlin.math.abs(scaled - cents) > 1e-6) {
            fail(MenuProblemKind.PRICE_TOO_PRECISE, place, MenuField.PRICE, number = price)
        }
        return cents.toLong()
    }

    // ------------------------------------------------------------------ helpers for checked values

    private fun objects(element: JsonElement?): List<JsonObject> = (element as? JsonArray).orEmpty().map { it as JsonObject }

    private fun number(element: JsonElement?): Double? {
        if (element.isNone() || element !is JsonPrimitive) return null
        return if (element.isString) {
            if (element.content.isEmpty()) null else parseDecimal(element.content)
        } else {
            element.doubleOrNull
        }
    }

    private fun placeName(name: JsonElement?, id: JsonElement?): String = shortForMessage(
        when {
            name.isTruthy() -> name.pyText()
            id.isTruthy() -> id.pyText()
            else -> "?"
        },
    )

    private val decimalPattern = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

    private fun parseDecimal(raw: String): Double? {
        val t = raw.trim()
        return if (decimalPattern.matches(t)) t.toDoubleOrNull() else null
    }

    private fun fail(
        kind: MenuProblemKind,
        place: MenuPlace,
        field: MenuField? = null,
        text: String? = null,
        number: Double? = null,
        limit: Double? = null,
        count: Int? = null,
        secondNumber: Double? = null,
    ): Nothing = throw MenuProblemException(
        MenuProblem(kind, place, field, text, number, limit, count, secondNumber),
    )
}

// JSON values with the meaning the old app (Python) gives them.

private fun JsonElement?.isNone(): Boolean = this == null || this is JsonNull

private fun JsonElement?.isString(): Boolean = this is JsonPrimitive && this !is JsonNull && this.isString

private fun JsonElement?.isBoolean(): Boolean =
    this is JsonPrimitive && this !is JsonNull && !this.isString && this.booleanOrNull != null

private fun JsonElement?.isNumber(): Boolean =
    this is JsonPrimitive && this !is JsonNull && !this.isString && this.booleanOrNull == null && this.doubleOrNull != null

private fun JsonElement?.asDouble(): Double = (this as JsonPrimitive).doubleOrNull ?: 0.0

/** Text of a string or number; null for anything else. */
private fun JsonElement?.textOrNull(): String? = when {
    isString() || isNumber() -> (this as JsonPrimitive).content
    else -> null
}

/** Text as Python's `str()` would show it, for messages. */
private fun JsonElement?.pyText(): String = when {
    this.isNone() -> "None"
    isString() || isNumber() -> (this as JsonPrimitive).content
    isBoolean() -> if ((this as JsonPrimitive).booleanOrNull == true) "True" else "False"
    else -> this.toString()
}

/** Python truthiness. */
private fun JsonElement?.isTruthy(): Boolean = when {
    this.isNone() -> false
    isBoolean() -> (this as JsonPrimitive).booleanOrNull == true
    isNumber() -> asDouble() != 0.0
    isString() -> (this as JsonPrimitive).content.isNotEmpty()
    this is JsonArray -> this.isNotEmpty()
    this is JsonObject -> this.isNotEmpty()
    else -> false
}
