package io.github.isouplay.roundbook.core.menu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuPickingTest {

    private val menu: Menu = (MenuFileReader.read(TestMenus.KRUG_EXPORT) as MenuReadResult.Success).file.menu
    private val nebelgeist: Drink = menu.drink(DrinkId("nebelgeist"))!!

    @Test
    fun tilesOfACategoryAreVisibleAndAlphabetical() {
        assertEquals(listOf("Kräuter Spezial (Fass)", "Krug Hell"), menu.visibleDrinksOf(CategoryId("bier")).map { it.name })
    }

    @Test
    fun searchFindsByNameAndCategoryWithoutHiddenDrinks() {
        assertEquals(listOf("Kräuter Spezial (Fass)"), menu.searchDrinks("krauter").map { it.name })
        assertEquals(listOf("Kräuter Spezial (Fass)", "Krug Hell"), menu.searchDrinks("BIER").map { it.name })
        assertTrue(menu.searchDrinks("Altbestand").isEmpty())
        assertTrue(menu.searchDrinks("  ").isEmpty())
    }

    @Test
    fun mixersComeFromTheTickOrTheCategory() {
        // Wasser has the tick "no mixer" although its category is a mixer category.
        assertEquals(listOf("Zitronenlimo", "Energie"), menu.mixers().map { it.name })
        assertNull(menu.mixer(DrinkId("wasser")))
        assertEquals("Energie", menu.mixer(DrinkId("energie"))?.name)
    }

    @Test
    fun mixSizesByLiters() {
        assertTrue(menu.isMixable(nebelgeist))
        assertEquals(listOf("0,2 l", "0,4 l"), menu.mixSizeLabels(nebelgeist))
        assertEquals(emptyList(), menu.mixSizeLabels(menu.drink(DrinkId("krug-hell"))!!))
    }

    @Test
    fun mixPriceComesFromTheTier() {
        // A mixer with its own mix (search word "Energie") draws that price …
        val energie = menu.mixOffer(nebelgeist, "0,2 l", "Energie")!!
        assertEquals(MixId("energie"), energie.mix.id)
        assertEquals(620L, energie.size.priceCents)
        assertEquals(4.0, energie.size.spiritCl)
        // … any other mixer, or none, the general mix.
        assertEquals(550L, menu.mixOffer(nebelgeist, "0,2 l", "Zitronenlimo")!!.size.priceCents)
        assertEquals(550L, menu.mixOffer(nebelgeist, "0,2 l", null)!!.size.priceCents)
        // The mix with search word has no 0,4 l: the general mix holds for every mixer.
        assertEquals(890L, menu.mixOffer(nebelgeist, "0,4 l", "Energie")!!.size.priceCents)
        assertNull(menu.mixOffer(nebelgeist, "1 l", null))
        assertEquals("mit Mischgetränk", menu.generalMix(nebelgeist)!!.name)
    }
}
