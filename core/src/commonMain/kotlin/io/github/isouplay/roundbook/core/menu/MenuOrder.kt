package io.github.isouplay.roundbook.core.menu

// Order of display and search, like the old app (konzept-auswahl-und-pinnwand.md, part 1): the menu is a
// collection, not an order. Drinks are shown alphabetically (German), sizes by liters. The menu file keeps
// its own order; these functions return sorted copies.

/**
 * German alphabetical order of names, like `Intl.Collator("de", { numeric: true, sensitivity: "base" })`
 * in the old app: Ä like A, ß like ss, upper and lower case alike, numbers in a name compared as numbers
 * ("Havanna 3 Jahre" before "Havanna 12 Jahre"). Equal names compare as 0, so a stable sort keeps the
 * order of the menu.
 */
object GermanOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val x = foldForSearch(a)
        val y = foldForSearch(b)
        var i = 0
        var j = 0
        while (i < x.length && j < y.length) {
            val c = x[i]
            val d = y[j]
            if (c.isAsciiDigit() && d.isAsciiDigit()) {
                val endI = digitsEnd(x, i)
                val endJ = digitsEnd(y, j)
                val byNumber = compareNumbers(x.substring(i, endI), y.substring(j, endJ))
                if (byNumber != 0) return byNumber
                i = endI
                j = endJ
                continue
            }
            if (c != d) return c.compareTo(d)
            i++
            j++
        }
        return (x.length - i).compareTo(y.length - j)
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

    private fun digitsEnd(s: String, from: Int): Int {
        var k = from
        while (k < s.length && s[k].isAsciiDigit()) k++
        return k
    }

    private fun compareNumbers(a: String, b: String): Int {
        val x = a.trimStart('0')
        val y = b.trimStart('0')
        if (x.length != y.length) return x.length.compareTo(y.length)
        return x.compareTo(y)
    }
}

/**
 * Text for comparing and searching: lower case, accents removed (ä → a, é → e), ß → ss.
 * "Kräuter" and "krauter" are the same; so are "Café" and "cafe".
 */
fun foldForSearch(text: String): String {
    val out = StringBuilder(text.length)
    for (c in text.lowercase()) {
        when (c) {
            'ä', 'à', 'á', 'â', 'ã', 'å' -> out.append('a')
            'ö', 'ò', 'ó', 'ô', 'õ' -> out.append('o')
            'ü', 'ù', 'ú', 'û' -> out.append('u')
            'é', 'è', 'ê', 'ë' -> out.append('e')
            'í', 'ì', 'î', 'ï' -> out.append('i')
            'ç' -> out.append('c')
            'ñ' -> out.append('n')
            'ß' -> out.append("ss")
            else -> out.append(c)
        }
    }
    return out.toString()
}

/** A copy sorted alphabetically by [name] (German order); equal names keep their order. */
fun <T> List<T>.sortedByName(name: (T) -> String): List<T> = sortedWith { a, b -> GermanOrder.compare(name(a), name(b)) }

/**
 * A copy sorted by liters, smallest first. Items without liters ("Pitcher") cannot be placed: they come
 * last, in the order of the menu.
 */
fun <T> List<T>.sortedByLiters(liters: (T) -> Double?): List<T> = sortedWith { a, b ->
    val la = liters(a)?.takeIf { it > 0.0 }
    val lb = liters(b)?.takeIf { it > 0.0 }
    when {
        la == null && lb == null -> 0
        la == null -> 1
        lb == null -> -1
        else -> la.compareTo(lb)
    }
}
