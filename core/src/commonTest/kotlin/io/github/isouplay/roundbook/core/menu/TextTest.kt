package io.github.isouplay.roundbook.core.menu

import kotlin.test.Test
import kotlin.test.assertEquals

class TextTest {

    @Test
    fun slugIsTheIdOfTheOldApp() {
        assertEquals("erdinger-alkoholfreies-weizen", slugOf("Erdinger Alkoholfreies Weizen"))
        assertEquals("wasseralfinger-spezial--fass", slugOf("Wasseralfinger Spezial (Fass)"))
        assertEquals("gruener-veltliner-ss", slugOf("Grüner Veltliner ß"))
        assertEquals("bier", slugOf("🍺 Bier"))
        assertEquals("a-b", slugOf("A🍺B"))
    }

    @Test
    fun lengthCountsCodePoints() {
        assertEquals(1, "🍺".codePointLength())
        assertEquals(3, "a🍺b".codePointLength())
        assertEquals("a🍺", "a🍺b".takeCodePoints(2))
    }

    @Test
    fun namesInMessagesAreShortened() {
        assertEquals("kurz", shortForMessage("  kurz "))
        assertEquals("y".repeat(40) + "…", shortForMessage("y".repeat(41)))
    }
}
