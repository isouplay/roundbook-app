package io.github.isouplay.roundbook.core.menu

/**
 * Why a menu file was rejected. Carries no user text: the app turns [kind], [place] and [field] into
 * a German message from its resources. Names in [place] and [text] are already shortened
 * (see [shortForMessage]).
 */
data class MenuProblem(
    val kind: MenuProblemKind,
    val place: MenuPlace,
    /** The field the problem is about, if any (name, price, volume …). */
    val field: MenuField? = null,
    /** The offending value or id as text, if it helps (e.g. "stark", an unknown allergen). */
    val text: String? = null,
    /** The offending number (e.g. 900 for 900 Vol.-%). */
    val number: Double? = null,
    /** The limit that was broken (e.g. 96 Vol.-%, 80 characters, 2000 drinks). */
    val limit: Double? = null,
    /** A count (e.g. 2001 drinks). */
    val count: Int? = null,
    /** Second number where a rule compares two values (litres for "spirit does not fit"). */
    val secondNumber: Double? = null,
)

enum class MenuProblemKind {
    // The file as a whole
    FILE_TOO_LARGE,
    NOT_JSON,
    NOT_RECOGNIZED,
    PRICE_HISTORY_NOT_SUPPORTED,
    NESTED_FORMAT_NOT_SUPPORTED,
    FORMAT_VERSION_TOO_NEW,
    INVALID_DATE,

    // Types and limits (old app: pruefe_karte)
    NOT_AN_OBJECT,
    NOT_A_LIST,
    NOT_TEXT,
    TEXT_TOO_LONG,
    NOT_A_NUMBER,
    NUMBER_OUT_OF_RANGE,
    NOT_A_BOOLEAN,
    INVALID_ID,
    TOO_MANY,
    PRICE_MISSING,
    PRICE_TOO_PRECISE,
    SPIRIT_EXCEEDS_VOLUME,
    UNKNOWN_ALLERGEN,
    DUPLICATE_ALLERGEN,
    UNKNOWN_ADDITIVE,
    DUPLICATE_ADDITIVE,
    UNKNOWN_MIX_TIER,

    // Consistency (old app: _normalize_menu)
    DUPLICATE_CATEGORY_ID,
    DRINK_WITHOUT_NAME,
    UNKNOWN_CATEGORY,
    DUPLICATE_DRINK_ID,
    DUPLICATE_SIZE_LABEL,
    DRINK_WITHOUT_PRICE,
    MIX_WITHOUT_NAME,
}

/** Field a problem is about. */
enum class MenuField {
    BAR_NAME,
    CURRENCY,
    VERSION_LABEL,
    VALID_FROM,
    CATEGORIES,
    DRINKS,
    MIX_TIERS,
    MIXES,
    SIZES,
    NAME,
    ICON,
    NOTE,
    ABV,
    SIZE_LABEL,
    PRICE,
    VOLUME,
    SPIRIT,
    MIXER,
    MATCH,
    CATEGORY,
    ALLERGENS,
    ADDITIVES,
}

/** Where in the file a problem is. Names are shortened for the message. */
sealed interface MenuPlace {
    /** The file itself (not JSON, unknown format, date). */
    data object File : MenuPlace

    /** The menu as a whole (bar name, currency, lists). */
    data object Menu : MenuPlace

    data class Category(val name: String) : MenuPlace

    data class Drink(val name: String) : MenuPlace

    /** A drink without a name, by its position (1-based). */
    data class DrinkNumber(val number: Int) : MenuPlace

    /** A size of a drink or mix: by its label, or by its position (1-based) if it has none. */
    data class Size(val parent: MenuPlace, val label: String?, val number: Int) : MenuPlace

    data class MixTier(val id: String) : MenuPlace

    data class Mix(val tier: MixTier, val name: String) : MenuPlace
}

/** Exception used inside the reader to stop at the first problem. */
internal class MenuProblemException(val problem: MenuProblem) : Exception(problem.kind.name)

/** Name for a message: at most 40 code points, then "…", so a 10 000 character name gives no huge message. */
fun shortForMessage(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.codePointLength() > 40) trimmed.takeCodePoints(40) + "…" else trimmed
}
