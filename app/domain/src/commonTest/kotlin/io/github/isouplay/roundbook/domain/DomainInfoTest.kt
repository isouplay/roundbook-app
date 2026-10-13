package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.core.BuildInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/** Placeholder: shows that the test path of :app:domain runs and :core is reachable. */
class DomainInfoTest {

    @Test
    fun versionComesFromCore() {
        assertEquals(BuildInfo.VERSION, DomainInfo.version)
    }
}
