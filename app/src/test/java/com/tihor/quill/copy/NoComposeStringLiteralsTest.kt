package com.tihor.quill.copy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CI/lint guard (spec §2.3): no hardcoded user-facing string literals in the UI — copy is read by key
 * via `copy(CopyKeys.X)`. Scans **every** `.kt` under `ui/` recursively (screens, app, components) so
 * a new screen can't silently drift back to literals. A line tagged `// copy-exempt` is skipped, so
 * the rare genuine non-UI string (brand name, model id, demo/sample data) is visible in diffs.
 */
class NoComposeStringLiteralsTest {

    // Each flags a user-facing string literal. `\"` = a literal double-quote opening a Kotlin string.
    private val patterns = listOf(
        Regex("Text\\s*\\(\\s*\""), // Text("…")
        Regex("\\b(?:title|text|subtitle|label|placeholder)\\s*=\\s*\""), // named-arg literals (Banner/QuillRow/fields)
        Regex(
            "\\b(?:QuillPrimaryButton|QuillTonalButton|QuillGhostButton|QuillDangerButton|" +
                "QuillChip|FieldLabel|HelpText|SectionHeader|QuillTopBarTitle)\\s*\\(\\s*\"",
        ), // text-first component literals
    )

    @Test
    fun uiComposables_useCopyKeys_notLiterals() {
        val base = baseUiDir()
        val offenders = mutableListOf<String>()
        base.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                if (line.contains("copy-exempt")) return@forEachIndexed
                if (patterns.any { it.containsMatchIn(line) }) {
                    offenders += "${file.relativeTo(base)}:${i + 1}: ${line.trim()}"
                }
            }
        }
        if (offenders.isNotEmpty()) {
            println("Copy-Catalog offenders (${offenders.size}):\n" + offenders.joinToString("\n"))
        }
        assertTrue(
            "User-facing string literals in ui/ — use copy(CopyKeys.X) or mark // copy-exempt:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    private fun baseUiDir(): File = listOf(
        File("app/src/main/java/com/tihor/quill/ui"),
        File("src/main/java/com/tihor/quill/ui"),
    ).firstOrNull { it.isDirectory }
        ?: error("Could not locate ui/ source dir (working dir = ${File("").absolutePath})")
}
