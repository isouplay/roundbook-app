package io.github.isouplay.roundbook.core.menu

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Allergens and additives of a drink, as fixed lists (no free text), like `kennzeichnung.py` of the
 * old app. Three states on a drink: no information (`Drink.labelling == null`), checked and contains
 * nothing (both lists empty), contains something. The menu maintainer is responsible for the data;
 * the app only shows it.
 */
@Serializable
data class Labelling(
    val allergens: List<Allergen>,
    val additives: List<Additive>,
) {
    val containsSomething: Boolean get() = allergens.isNotEmpty() || additives.isNotEmpty()
}

/** The 14 main allergens. [code] is the id in the menu file; the order is the display order. */
@Serializable
enum class Allergen(val code: String) {
    @SerialName("gluten") GLUTEN("gluten"),
    @SerialName("krebstiere") CRUSTACEANS("krebstiere"),
    @SerialName("eier") EGGS("eier"),
    @SerialName("fisch") FISH("fisch"),
    @SerialName("erdnuesse") PEANUTS("erdnuesse"),
    @SerialName("soja") SOY("soja"),
    @SerialName("milch") MILK("milch"),
    @SerialName("schalenfruechte") TREE_NUTS("schalenfruechte"),
    @SerialName("sellerie") CELERY("sellerie"),
    @SerialName("senf") MUSTARD("senf"),
    @SerialName("sesam") SESAME("sesam"),
    @SerialName("sulfite") SULPHITES("sulfite"),
    @SerialName("lupinen") LUPIN("lupinen"),
    @SerialName("weichtiere") MOLLUSCS("weichtiere"),
    ;

    companion object {
        fun fromCode(code: String): Allergen? = entries.firstOrNull { it.code == code }
    }
}

/** Additives that must be declared. [code] is the id in the menu file. */
@Serializable
enum class Additive(val code: String) {
    @SerialName("farbstoff") COLOURING("farbstoff"),
    @SerialName("konservierungsstoff") PRESERVATIVE("konservierungsstoff"),
    @SerialName("antioxidationsmittel") ANTIOXIDANT("antioxidationsmittel"),
    @SerialName("geschmacksverstaerker") FLAVOUR_ENHANCER("geschmacksverstaerker"),
    @SerialName("geschwefelt") SULPHURATED("geschwefelt"),
    @SerialName("phosphat") PHOSPHATE("phosphat"),
    @SerialName("suessungsmittel") SWEETENER("suessungsmittel"),
    @SerialName("phenylalanin") PHENYLALANINE("phenylalanin"),
    @SerialName("koffein") CAFFEINE("koffein"),
    @SerialName("chinin") QUININE("chinin"),
    ;

    companion object {
        fun fromCode(code: String): Additive? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Allergens and additives of a mix: spirit and mixer together. [labelling] is null if nothing can be said;
 * [missing] names the parts without information while the others contain something.
 */
data class CombinedLabelling(
    val labelling: Labelling?,
    val missing: List<String>,
)

/**
 * Combines the labelling of the parts of a mix (name and labelling of each part), like the old app
 * (`zusammen` in angaben.js): what one part contains, the mix contains. A part without information hides
 * nothing: if the others contain something, that is shown together with the names of the parts without
 * information; otherwise the mix counts as "no information" – "contains nothing" is never claimed then.
 */
fun combineLabelling(parts: List<Pair<String, Labelling?>>): CombinedLabelling {
    val without = parts.filter { it.second == null }.map { it.first }
    val with = parts.mapNotNull { it.second }
    if (with.isEmpty()) return CombinedLabelling(null, emptyList())
    val combined = Labelling(
        allergens = with.flatMap { it.allergens }.distinct().sortedBy { it.ordinal },
        additives = with.flatMap { it.additives }.distinct().sortedBy { it.ordinal },
    )
    if (!combined.containsSomething && without.isNotEmpty()) return CombinedLabelling(null, emptyList())
    return CombinedLabelling(combined, if (combined.containsSomething) without else emptyList())
}
