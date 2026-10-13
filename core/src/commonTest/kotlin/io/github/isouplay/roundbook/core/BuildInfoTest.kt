package io.github.isouplay.roundbook.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Placeholder: shows that the test path of :core runs. */
class BuildInfoTest {

    @Test
    fun nameIsRoundbook() {
        assertEquals("Roundbook", BuildInfo.NAME)
    }

    @Test
    fun versionIsSemantic() {
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(BuildInfo.VERSION), BuildInfo.VERSION)
    }
}
