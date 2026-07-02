package com.tihor.quill.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Custom-provider base URL rule (decision 1): https + non-blank, with an actual host. */
class CustomEndpointTest {

    @Test
    fun acceptsHttpsUrls() {
        assertTrue(CustomEndpoint.isValidBaseUrl("https://api.example.com/v1"))
        assertTrue(CustomEndpoint.isValidBaseUrl("https://localhost:8080/openai/v1"))
        assertTrue(CustomEndpoint.isValidBaseUrl("  https://api.example.com/v1  ")) // trimmed
    }

    @Test
    fun rejectsBlank() {
        assertFalse(CustomEndpoint.isValidBaseUrl(""))
        assertFalse(CustomEndpoint.isValidBaseUrl("   "))
    }

    @Test
    fun rejectsHttp() {
        assertFalse(CustomEndpoint.isValidBaseUrl("http://api.example.com/v1"))
    }

    @Test
    fun rejectsSchemeOnlyOrGarbage() {
        assertFalse(CustomEndpoint.isValidBaseUrl("https://"))
        assertFalse(CustomEndpoint.isValidBaseUrl("api.example.com"))
        assertFalse(CustomEndpoint.isValidBaseUrl("https://has space.com/v1"))
    }
}
