package com.tihor.quill.settings

/**
 * Validation for the Custom provider's user-supplied OpenAI-compatible base URL (decision 1):
 * https-only and non-blank, with an actual host. Pure so the rule is unit-testable.
 */
object CustomEndpoint {
    fun isValidBaseUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("https://") &&
            trimmed.length > "https://".length &&
            trimmed.none { it.isWhitespace() }
    }
}
