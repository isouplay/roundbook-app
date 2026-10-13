package io.github.isouplay.roundbook.core.menu

/** Length in Unicode code points (like Python's `len`); a surrogate pair counts once. */
fun String.codePointLength(): Int {
    var count = 0
    var i = 0
    while (i < length) {
        i += if (this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) 2 else 1
        count++
    }
    return count
}

/** The first [n] code points. */
fun String.takeCodePoints(n: Int): String {
    var count = 0
    var i = 0
    while (i < length && count < n) {
        i += if (this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) 2 else 1
        count++
    }
    return substring(0, i)
}

/**
 * Drink id from a name, exactly as the old app builds it (`menu._slug`): lower case, ä/ö/ü/ß
 * spelled out, every other character that is not a letter or digit becomes "-", dashes at the ends
 * removed. "Wasseralfinger Spezial (Fass)" → "wasseralfinger-spezial--fass".
 * It must stay the same id as before, otherwise entries and pins from the old app no longer fit.
 */
fun slugOf(name: String): String {
    val lower = name.lowercase()
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
    val out = StringBuilder()
    var i = 0
    while (i < lower.length) {
        val c = lower[i]
        if (c.isHighSurrogate() && i + 1 < lower.length && lower[i + 1].isLowSurrogate()) {
            // One code point outside the basic plane (emoji): one dash, like Python.
            out.append('-')
            i += 2
            continue
        }
        out.append(if (c.isLetterOrDigit()) c else '-')
        i++
    }
    return out.toString().trim('-')
}
