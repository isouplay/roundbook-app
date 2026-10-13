package io.github.isouplay.roundbook.core.menu

import kotlin.test.Test
import kotlin.test.assertEquals

class LabellingTest {

    private val milk = Labelling(listOf(Allergen.MILK), emptyList())
    private val nothing = Labelling(emptyList(), emptyList())
    private val caffeine = Labelling(listOf(Allergen.SULPHITES), listOf(Additive.CAFFEINE))

    @Test
    fun aMixContainsWhatEitherPartContains() {
        val combined = combineLabelling(listOf("Likör" to milk, "Energie" to caffeine))
        assertEquals(Labelling(listOf(Allergen.MILK, Allergen.SULPHITES), listOf(Additive.CAFFEINE)), combined.labelling)
        assertEquals(emptyList(), combined.missing)
    }

    @Test
    fun aPartWithoutInformationIsNamedAndNeverHidden() {
        assertEquals(CombinedLabelling(milk, listOf("Saft")), combineLabelling(listOf("Likör" to milk, "Saft" to null)))
        // "contains nothing" is not claimed if one part has no information.
        assertEquals(CombinedLabelling(null, emptyList()), combineLabelling(listOf("Korn" to nothing, "Saft" to null)))
        assertEquals(CombinedLabelling(nothing, emptyList()), combineLabelling(listOf("Korn" to nothing, "Wasser" to nothing)))
        assertEquals(CombinedLabelling(null, emptyList()), combineLabelling(listOf("Korn" to null)))
    }
}
