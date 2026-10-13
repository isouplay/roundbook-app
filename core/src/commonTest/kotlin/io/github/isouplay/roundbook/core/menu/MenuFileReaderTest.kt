package io.github.isouplay.roundbook.core.menu

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuFileReaderTest {

    private fun ok(text: String): MenuFile {
        val result = MenuFileReader.read(text)
        assertIs<MenuReadResult.Success>(result, "expected success, got $result")
        return result.file
    }

    private fun problem(text: String): MenuProblem {
        val result = MenuFileReader.read(text)
        assertIs<MenuReadResult.Failure>(result, "expected a problem, got $result")
        return result.problem
    }

    private fun num(v: Number) = JsonPrimitive(v)

    private fun str(v: String) = JsonPrimitive(v)

    // ------------------------------------------------------------------ valid files

    @Test
    fun readsEnvelopeWithDateAndLabel() {
        val file = ok(TestMenus.KRUG_EXPORT)
        assertEquals(LocalDate(2026, 10, 1), file.validFrom)
        assertEquals("Herbstkarte", file.label)
        assertEquals("Zum Erfundenen Krug", file.menu.bar)
        assertEquals("EUR", file.menu.currency)
    }

    @Test
    fun readsCategoriesWithIconAndSwitches() {
        val menu = ok(TestMenus.KRUG_EXPORT).menu
        assertEquals(listOf("bier", "schnaps", "alkoholfrei", "snacks"), menu.categories.map { it.id.value })
        val beer = menu.category(CategoryId("bier"))!!
        assertEquals("🍺", beer.icon)
        assertTrue(beer.countsAsDrink)
        assertFalse(beer.mixer)
        assertTrue(menu.category(CategoryId("alkoholfrei"))!!.mixer)
        assertFalse(menu.category(CategoryId("snacks"))!!.countsAsDrink)
    }

    @Test
    fun readsDrinksWithSizesAndPricesInCents() {
        val menu = ok(TestMenus.KRUG_EXPORT).menu
        val hell = menu.drink(DrinkId("krug-hell"))!!
        assertEquals("Krug Hell", hell.name)
        assertEquals(CategoryId("bier"), hell.categoryId)
        assertEquals(4.9, hell.abv)
        assertEquals(listOf(Size("0,3 l", 0.3, 320), Size("0,5 l", 0.5, 450)), hell.sizes)
        // Whole numbers and numbers as text are prices too.
        assertEquals(400L, menu.drinks.first { it.name == "Kräuter Spezial (Fass)" }.sizes.single().priceCents)
        assertEquals(390L, menu.drink(DrinkId("energie"))!!.sizes.single().priceCents)
        // A size without litres.
        assertNull(menu.drink(DrinkId("brezel"))!!.sizes.single().liters)
    }

    @Test
    fun drinkWithoutIdGetsTheIdOfTheOldApp() {
        val menu = ok(TestMenus.KRUG_EXPORT).menu
        val drink = menu.drinks.first { it.name == "Kräuter Spezial (Fass)" }
        assertEquals(DrinkId("kraeuter-spezial--fass"), drink.id)
    }

    @Test
    fun readsNoteHiddenExAndMixerTick() {
        val menu = ok(TestMenus.KRUG_EXPORT).menu
        assertEquals("Hausmarke", menu.drink(DrinkId("nebelgeist"))!!.note)
        assertTrue(menu.drink(DrinkId("altbestand"))!!.hidden)
        assertTrue(menu.drink(DrinkId("tauchboot"))!!.ex)
        val water = menu.drink(DrinkId("wasser"))!!
        val soft = menu.category(CategoryId("alkoholfrei"))!!
        assertEquals(false, water.mixer)
        assertFalse(water.isMixer(soft), "the tick on the drink wins over the category")
        val lemonade = menu.drink(DrinkId("zitronenlimo"))!!
        assertNull(lemonade.mixer)
        assertTrue(lemonade.isMixer(soft), "without tick the category decides")
    }

    @Test
    fun readsMixTiersWithMixesAndSpirit() {
        val menu = ok(TestMenus.KRUG_EXPORT).menu
        val spirit = menu.drink(DrinkId("nebelgeist"))!!
        assertEquals(MixTierId("k1"), spirit.mixTierId)
        val tier = menu.mixTier(MixTierId("k1"))!!
        assertEquals("Hausstufe", tier.name)
        assertNull(tier.mixCategory)
        assertEquals(listOf("mische", "energie"), tier.mixes.map { it.id.value })
        val general = tier.mixes[0]
        assertEquals("", general.match)
        assertEquals(MixSize("0,2 l", 0.2, 550, 4.0), general.sizes[0])
        assertEquals(MixSize("0,4 l", 0.4, 890, 8.0), general.sizes[1])
        assertEquals("Energie", tier.mixes[1].match)
        assertEquals(tier.mixes, menu.mixesOf(spirit))
        assertTrue(menu.mixesOf(menu.drink(DrinkId("krug-hell"))!!).isEmpty())
    }

    @Test
    fun allergensHaveThreeStates() {
        val menu = ok(TestMenus.KRUG_EXPORT).menu
        // No information: both fields missing.
        assertNull(menu.drink(DrinkId("tauchboot"))!!.labelling)
        // Checked, contains nothing: both lists empty.
        val water = menu.drink(DrinkId("wasser"))!!.labelling!!
        assertEquals(Labelling(emptyList(), emptyList()), water)
        assertFalse(water.containsSomething)
        // Contains something; a missing second list counts as empty.
        val hell = menu.drink(DrinkId("krug-hell"))!!.labelling!!
        assertEquals(Labelling(listOf(Allergen.GLUTEN), emptyList()), hell)
        assertTrue(hell.containsSomething)
        val lemonade = menu.drink(DrinkId("zitronenlimo"))!!.labelling!!
        assertEquals(listOf(Additive.CAFFEINE, Additive.COLOURING), lemonade.additives)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        // The fixture has unknown fields in envelope, menu, category, tier, drink and size.
        val menu = ok(TestMenus.KRUG_EXPORT).menu
        assertEquals(9, menu.drinks.size)
    }

    @Test
    fun hiddenDrinksAreKeptButNotShown() {
        val menu = ok(TestMenus.KRUG_EXPORT).menu
        assertTrue(menu.drinks.any { it.id == DrinkId("altbestand") })
        val shown = menu.sections().flatMap { it.drinks }.map { it.id.value }
        assertFalse("altbestand" in shown)
        assertEquals(listOf("bier", "schnaps", "alkoholfrei", "snacks"), menu.sections().map { it.category.id.value })
    }

    @Test
    fun readsBareMenuWithoutEnvelope() {
        val file = ok(TestMenus.BARE)
        assertNull(file.validFrom)
        assertNull(file.label)
        assertEquals("Dunkles Erfunden", file.menu.drinks.single().name)
        assertEquals("", file.menu.bar)
    }

    @Test
    fun dateWithTimeCountsWithItsDate() {
        val file = ok(TestMenus.bare(envelope = { it["valid_from"] = str("2026-10-12T19:50:21+00:00") }))
        assertEquals(LocalDate(2026, 10, 12), file.validFrom)
    }

    @Test
    fun valuesAtTheLimitsAreAccepted() {
        ok(TestMenus.bare(size = { it["price"] = num(9999.99) }))
        ok(TestMenus.bare(size = { it["l"] = num(50) }))
        ok(TestMenus.bare(drink = { it["abv"] = num(96) }))
        ok(TestMenus.bare(drink = { it["name"] = str("x".repeat(80)) }))
        ok(TestMenus.bare(menu = { it["mix_tiers"] = mixTier(liters = 50.0, spiritCl = 5000.0) }))
        ok(TestMenus.bare(menu = { it["mix_tiers"] = mixTier(liters = null, spiritCl = 5000.0) }))
    }

    @Test
    fun menuSurvivesStorageRoundTrip() {
        val menu = ok(TestMenus.KRUG_EXPORT).menu
        assertEquals(menu, MenuJson.decode(MenuJson.encode(menu)))
    }

    // ------------------------------------------------------------------ rejected files

    @Test
    fun rejectsWhatIsNoMenuFile() {
        assertEquals(MenuProblemKind.NOT_JSON, problem("{ kaputt").kind)
        assertEquals(MenuProblemKind.NOT_RECOGNIZED, problem("[1, 2]").kind)
        assertEquals(MenuProblemKind.NOT_RECOGNIZED, problem("""{"hello": 1}""").kind)
        assertEquals(MenuProblemKind.PRICE_HISTORY_NOT_SUPPORTED, problem("""{"versions": []}""").kind)
        assertEquals(
            MenuProblemKind.NESTED_FORMAT_NOT_SUPPORTED,
            problem("""{"categories": [{"id": "bier", "drinks": ["Pils"]}]}""").kind,
        )
        assertEquals(MenuProblemKind.NOT_AN_OBJECT, problem("""{"menu": [1]}""").kind)
    }

    @Test
    fun rejectsNewerFormatVersion() {
        val p = problem(TestMenus.bare(envelope = { it["format_version"] = num(2) }))
        assertEquals(MenuProblemKind.FORMAT_VERSION_TOO_NEW, p.kind)
        assertEquals(2.0, p.number)
    }

    @Test
    fun rejectsTooLargeFile() {
        val p = problem(" ".repeat(MenuLimits.FILE_BYTES_MAX + 1))
        assertEquals(MenuProblemKind.FILE_TOO_LARGE, p.kind)
    }

    @Test
    fun rejectsInvalidDate() {
        val p = problem(TestMenus.bare(envelope = { it["valid_from"] = str("2026-02-30") }))
        assertEquals(MenuProblemKind.INVALID_DATE, p.kind)
        assertEquals("2026-02-30", p.text)
    }

    @Test
    fun rejectsAbvAbove96WithPlaceAndValue() {
        val p = problem(TestMenus.bare(drink = { it["abv"] = num(900) }))
        assertEquals(MenuProblemKind.NUMBER_OUT_OF_RANGE, p.kind)
        assertEquals(MenuPlace.Drink("Dunkles Erfunden"), p.place)
        assertEquals(MenuField.ABV, p.field)
        assertEquals(900.0, p.number)
        assertEquals(96.0, p.limit)
    }

    @Test
    fun rejectsNegativePriceAndTooManyLitres() {
        val price = problem(TestMenus.bare(size = { it["price"] = num(-5) }))
        assertEquals(MenuProblemKind.NUMBER_OUT_OF_RANGE, price.kind)
        assertEquals(MenuField.PRICE, price.field)
        assertEquals(MenuPlace.Size(MenuPlace.Drink("Dunkles Erfunden"), "0,5 l", 1), price.place)

        val litres = problem(TestMenus.bare(size = { it["l"] = num(200) }))
        assertEquals(MenuProblemKind.NUMBER_OUT_OF_RANGE, litres.kind)
        assertEquals(MenuField.VOLUME, litres.field)
        assertEquals(50.0, litres.limit)
    }

    @Test
    fun rejectsTextThatIsNoNumber() {
        val p = problem(TestMenus.bare(drink = { it["abv"] = str("stark") }))
        assertEquals(MenuProblemKind.NOT_A_NUMBER, p.kind)
        assertEquals("stark", p.text)
        assertEquals(MenuProblemKind.NOT_A_NUMBER, problem(TestMenus.bare(drink = { it["abv"] = JsonPrimitive(true) })).kind)
    }

    @Test
    fun rejectsTooLongTexts() {
        val name = problem(TestMenus.bare(drink = { it["name"] = str("x".repeat(81)) }))
        assertEquals(MenuProblemKind.TEXT_TOO_LONG, name.kind)
        assertEquals(MenuField.NAME, name.field)
        assertEquals(80.0, name.limit)
        // The name in the message is shortened.
        assertEquals(MenuPlace.Drink("x".repeat(40) + "…"), name.place)

        val note = problem(TestMenus.bare(drink = { it["note"] = str("x".repeat(301)) }))
        assertEquals(MenuProblemKind.TEXT_TOO_LONG, note.kind)
        assertEquals(MenuField.NOTE, note.field)
    }

    @Test
    fun rejectsInvalidId() {
        val p = problem(TestMenus.bare(drink = { it["id"] = str("<b>") }))
        assertEquals(MenuProblemKind.INVALID_ID, p.kind)
        assertEquals("<b>", p.text)
    }

    @Test
    fun rejectsTooManyDrinks() {
        val p = problem(
            TestMenus.bare(menu = { m ->
                val drink = (m.getValue("drinks") as JsonArray)[0] as JsonObject
                m["drinks"] = JsonArray(
                    List(MenuLimits.DRINKS_MAX + 1) { i -> JsonObject(drink + ("id" to str("g$i"))) },
                )
            }),
        )
        assertEquals(MenuProblemKind.TOO_MANY, p.kind)
        assertEquals(MenuField.DRINKS, p.field)
        assertEquals(2001, p.count)
    }

    @Test
    fun rejectsMissingPrice() {
        assertEquals(MenuProblemKind.PRICE_MISSING, problem(TestMenus.bare(size = { it.remove("price") })).kind)
        assertEquals(MenuProblemKind.PRICE_MISSING, problem(TestMenus.bare(size = { it["price"] = str("") })).kind)
    }

    @Test
    fun rejectsPriceWithMoreThanTwoDecimals() {
        val p = problem(TestMenus.bare(size = { it["price"] = num(4.555) }))
        assertEquals(MenuProblemKind.PRICE_TOO_PRECISE, p.kind)
    }

    @Test
    fun rejectsSpiritThatDoesNotFitIntoTheSize() {
        val p = problem(TestMenus.bare(menu = { it["mix_tiers"] = mixTier(liters = 0.2, spiritCl = 30.0) }))
        assertEquals(MenuProblemKind.SPIRIT_EXCEEDS_VOLUME, p.kind)
        assertEquals(30.0, p.number)
        assertEquals(0.2, p.secondNumber)
    }

    @Test
    fun rejectsMixerThatIsNoBoolean() {
        val p = problem(TestMenus.bare(drink = { it["mixer"] = str("ja") }))
        assertEquals(MenuProblemKind.NOT_A_BOOLEAN, p.kind)
        assertEquals(MenuField.MIXER, p.field)
    }

    @Test
    fun rejectsUnknownAndDuplicateAllergens() {
        val unknown = problem(TestMenus.bare(drink = { it["allergene"] = JsonArray(listOf(str("gluten"), str("staub"))) }))
        assertEquals(MenuProblemKind.UNKNOWN_ALLERGEN, unknown.kind)
        assertEquals("staub", unknown.text)

        val twice = problem(TestMenus.bare(drink = { it["allergene"] = JsonArray(listOf(str("milch"), str("milch"))) }))
        assertEquals(MenuProblemKind.DUPLICATE_ALLERGEN, twice.kind)

        val additive = problem(TestMenus.bare(drink = { it["zusatzstoffe"] = JsonArray(listOf(str("glitzer"))) }))
        assertEquals(MenuProblemKind.UNKNOWN_ADDITIVE, additive.kind)

        val noList = problem(TestMenus.bare(drink = { it["allergene"] = str("gluten") }))
        assertEquals(MenuProblemKind.NOT_A_LIST, noList.kind)
        assertEquals(MenuField.ALLERGENS, noList.field)
    }

    @Test
    fun nullAllergensMeanNoInformation() {
        val file = ok(TestMenus.bare(drink = { it["allergene"] = JsonNull }))
        assertNull(file.menu.drinks.single().labelling)
    }

    @Test
    fun rejectsUnknownMixTierAndCategory() {
        val tier = problem(TestMenus.bare(drink = { it["tier"] = str("k9") }))
        assertEquals(MenuProblemKind.UNKNOWN_MIX_TIER, tier.kind)
        assertEquals("k9", tier.text)

        val category = problem(TestMenus.bare(drink = { it["category"] = str("wein") }))
        assertEquals(MenuProblemKind.UNKNOWN_CATEGORY, category.kind)
        assertEquals("wein", category.text)
    }

    @Test
    fun rejectsDuplicateIdsAndLabels() {
        val drinks = problem(
            TestMenus.bare(menu = { m ->
                val drink = (m.getValue("drinks") as JsonArray)[0]
                m["drinks"] = JsonArray(listOf(drink, drink))
            }),
        )
        assertEquals(MenuProblemKind.DUPLICATE_DRINK_ID, drinks.kind)
        assertEquals("dunkel", drinks.text)

        val categories = problem(
            TestMenus.bare(menu = { m ->
                val category = (m.getValue("categories") as JsonArray)[0]
                m["categories"] = JsonArray(listOf(category, category))
            }),
        )
        assertEquals(MenuProblemKind.DUPLICATE_CATEGORY_ID, categories.kind)

        val sizes = problem(
            TestMenus.bare(drink = { d ->
                val size = (d.getValue("sizes") as JsonArray)[0]
                d["sizes"] = JsonArray(listOf(size, size))
            }),
        )
        assertEquals(MenuProblemKind.DUPLICATE_SIZE_LABEL, sizes.kind)
    }

    @Test
    fun rejectsDrinkWithoutNameOrPrice() {
        val noName = problem(TestMenus.bare(drink = { it["name"] = str("  ") }))
        assertEquals(MenuProblemKind.DRINK_WITHOUT_NAME, noName.kind)
        assertEquals(MenuPlace.DrinkNumber(1), noName.place)

        val noPrice = problem(TestMenus.bare(drink = { it["sizes"] = JsonArray(emptyList()) }))
        assertEquals(MenuProblemKind.DRINK_WITHOUT_PRICE, noPrice.kind)
    }

    @Test
    fun drinkWithOnlyMixSizesHasAPrice() {
        val file = ok(
            TestMenus.bare(
                menu = { it["mix_tiers"] = mixTier(liters = 0.2, spiritCl = 4.0) },
                drink = { it["sizes"] = JsonArray(emptyList()); it["tier"] = str("k1") },
            ),
        )
        assertTrue(file.menu.drinks.single().sizes.isEmpty())
    }

    @Test
    fun rejectsListsThatAreNoLists() {
        val p = problem(TestMenus.bare(menu = { it["drinks"] = str("viele") }))
        assertEquals(MenuProblemKind.NOT_A_LIST, p.kind)
        assertEquals(MenuField.DRINKS, p.field)
        val entry = problem(TestMenus.bare(menu = { it["drinks"] = JsonArray(listOf(num(1))) }))
        assertEquals(MenuProblemKind.NOT_AN_OBJECT, entry.kind)
    }

    private fun mixTier(liters: Double?, spiritCl: Double): JsonObject {
        val size = buildMap<String, kotlinx.serialization.json.JsonElement> {
            put("label", str("Tonne"))
            if (liters != null) put("l", num(liters))
            put("price", num(3))
            put("spirit_cl", num(spiritCl))
        }
        val mix = JsonObject(
            mapOf("id" to str("cola"), "name" to str("mit Cola"), "sizes" to JsonArray(listOf(JsonObject(size)))),
        )
        return JsonObject(mapOf("k1" to JsonObject(mapOf("name" to str("Gespritzt"), "mixes" to JsonArray(listOf(mix))))))
    }
}
