package com.anjo.routing

import com.anjo.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class TextApiRouteTest {
    
    @Test
    fun testPostValidTextReturnsAccepted() = testApplication {
        application {
            module()
        }
        val response = client.post("/api/text") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"text":"Hello World"}""")
        }
        
        assertEquals(HttpStatusCode.Accepted, response.status)
        val body = response.bodyAsText()
        assertContains(body, "accepted")
    }
    
    @Test
    fun testPostBlankTextReturnsBadRequest() = testApplication {
        application {
            module()
        }
        val response = client.post("/api/text") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"text":""}""")
        }
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertContains(body, "error")
    }
    
    @Test
    fun testPostOversizeTextReturnsBadRequest() = testApplication {
        application {
            module()
        }
        val longText = "a".repeat(200)  // Over max of 128
        val response = client.post("/api/text") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"text":"$longText"}""")
        }
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertContains(body, "error")
    }
}


