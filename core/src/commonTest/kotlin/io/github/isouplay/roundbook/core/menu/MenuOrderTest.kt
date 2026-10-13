package io.github.isouplay.roundbook.core.menu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MenuOrderTest {

    @Test
    fun germanOrderIgnoresUmlautsAndCase() {
        val names = listOf("Zwetschge", "Äpfelkorn", "apfelsaft", "Obstler", "Öko-Limo", "Asbach")
        assertEquals(
            listOf("Äpfelkorn", "apfelsaft", "Asbach", "Obstler", "Öko-Limo", "Zwetschge"),
            names.sortedByName { it },
        )
    }

    @Test
    fun numbersInNamesCountAsNumbers() {
        val names = listOf("Havanna 12 Jahre", "Havanna 3 Jahre", "Havanna 7 Jahre")
        assertEquals(listOf("Havanna 3 Jahre", "Havanna 7 Jahre", "Havanna 12 Jahre"), names.sortedByName { it })
    }

    @Test
    fun equalNamesKeepTheOrderOfTheMenu() {
        val items = listOf("b" to 1, "a" to 2, "B" to 3)
        assertEquals(listOf(2, 1, 3), items.sortedByName { it.first }.map { it.second })
        assertEquals(0, GermanOrder.compare("Straße", "STRASSE"))
        assertTrue(GermanOrder.compare("Bier", "Biere") < 0)
    }

    @Test
    fun sizesByLitersWithoutLitersLast() {
        val sizes = listOf("0,4" to 0.4, "Turm" to null, "0,3" to 0.3, "Pitcher" to null, "0,2" to 0.2)
        assertEquals(listOf("0,2", "0,3", "0,4", "Turm", "Pitcher"), sizes.sortedByLiters { it.second }.map { it.first })
    }

    @Test
    fun searchTextIsFolded() {
        assertEquals("krauter spezial strasse cafe", foldForSearch("Kräuter Spezial Straße Café"))
    }
}
