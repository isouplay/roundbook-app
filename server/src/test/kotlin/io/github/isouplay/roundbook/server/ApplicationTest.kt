package io.github.isouplay.roundbook.server

import io.github.isouplay.roundbook.core.BuildInfo
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun healthAnswersOkWithVersion() = testApplication {
        application {
            module()
        }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok ${BuildInfo.VERSION}", response.bodyAsText())
    }

    @Test
    fun unknownPathIsNotFound() = testApplication {
        application {
            module()
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/").status)
    }
}
