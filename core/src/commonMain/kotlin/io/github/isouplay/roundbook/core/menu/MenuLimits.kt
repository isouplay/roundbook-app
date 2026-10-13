package io.github.isouplay.roundbook.core.menu

/**
 * Limits for a menu file, the same as in the old app (`menu.py`, package 13). A menu file is foreign
 * input: values outside the limits are rejected, never bent into shape. Text lengths count Unicode
 * code points, like Python's `len`.
 */
object MenuLimits {
    /** Vol.-%: pure alcohol from the shop has 96. */
    const val ABV_MAX: Double = 96.0

    /** Per size, in the currency of the menu. */
    const val PRICE_MAX: Double = 9999.99

    /** Per size: the largest container (keg) in litres. */
    const val LITERS_MAX: Double = 50.0

    /** Spirit per mix size in centilitres (all of the largest container). */
    const val SPIRIT_CL_MAX: Double = LITERS_MAX * 100

    /** Drink, category, mix tier, mix, bar, version label. */
    const val NAME_MAX: Int = 80

    /** Size label ("0,5 l"), currency. */
    const val LABEL_MAX: Int = 40
    const val NOTE_MAX: Int = 300

    /** One emoji, also with modifiers. */
    const val ICON_MAX: Int = 16
    const val DRINKS_MAX: Int = 2000
    const val CATEGORIES_MAX: Int = 100

    /** Per drink or per mix. */
    const val SIZES_MAX: Int = 20
    const val MIX_TIERS_MAX: Int = 50

    /** Per mix tier. */
    const val MIXES_MAX: Int = 50

    /** Size of a menu file (the old app: 5 MB; a real menu has about 40 KB). */
    const val FILE_BYTES_MAX: Int = 5 * 1024 * 1024

    /** Allowed ids: letters a–z, digits, - and _, at most 64. */
    val ID_PATTERN: Regex = Regex("[A-Za-z0-9_-]{1,64}")
}
