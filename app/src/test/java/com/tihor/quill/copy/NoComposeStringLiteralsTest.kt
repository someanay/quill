package com.tihor.quill.copy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CI/lint guard (spec §2.3 / M8): no hardcoded user-facing string literals in composables — copy is
 * read by key via `copy(CopyKeys.X)`. Scans the on-design onboarding flow (the first fully-keyed
 * flow). Extend [SCANNED] as more screens migrate to the Copy Catalog.
 */
class NoComposeStringLiteralsTest {

    private val scanned = listOf(
        "src/main/java/com/tihor/quill/ui/OnboardingActivity.kt",
        "src/main/java/com/tihor/quill/ui/screens/HistoryScreen.kt",
    )

    @Test
    fun onboardingComposables_useCopyKeys_notTextLiterals() {
        // Matches a Text( call whose first argument is a string literal, e.g. Text("hello").
        val textLiteral = Regex("Text\\s*\\(\\s*\"")
        val offenders = mutableListOf<String>()
        var filesFound = 0

        for (rel in scanned) {
            val file = resolve(rel) ?: continue
            filesFound++
            file.readLines().forEachIndexed { index, line ->
                if (textLiteral.containsMatchIn(line)) {
                    offenders += "${file.name}:${index + 1}: ${line.trim()}"
                }
            }
        }

        assertTrue("Could not locate source files to scan (working dir issue).", filesFound > 0)
        assertTrue(
            "Hardcoded Text(\"…\") literals found — use copy(CopyKeys.X):\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    private fun resolve(rel: String): File? =
        listOf(File(rel), File("app/$rel")).firstOrNull { it.exists() }
}
