package io.github.isouplay.roundbook

import io.github.isouplay.roundbook.core.BuildInfo
import io.github.isouplay.roundbook.domain.DomainInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/** Placeholder: shows that the test path of :app:shared runs and :app:domain is reachable. */
class SharedCommonTest {

    @Test
    fun domainIsReachable() {
        assertEquals(BuildInfo.VERSION, DomainInfo.version)
    }
}
