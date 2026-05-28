package com.anjo.routing

import com.anjo.module
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TextApiRouteTest {

    @Test
    fun testPostValidTextReturnsAccepted() = testApplication {
        application { module() }
        val response = client.post("/api/v1/text") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"text":"Hello World"}""")
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        val body = response.bodyAsText()
        assertContains(body, "accepted")
    }

    @Test
    fun testPostBlankTextReturnsBadRequest() = testApplication {
        application { module() }
        val response = client.post("/api/v1/text") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"text":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertContains(body, "error")
    }

    @Test
    fun testPostOversizeTextReturnsBadRequest() = testApplication {
        application { module() }
        val longText = "a".repeat(200)  // Over max of 128
        val response = client.post("/api/v1/text") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"text":"$longText"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertContains(body, "error")
    }
}
