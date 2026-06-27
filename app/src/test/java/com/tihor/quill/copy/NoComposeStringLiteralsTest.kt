package com.tihor.quill.copy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CI/lint guard (spec §2.3): no hardcoded user-facing string literals in the UI — copy is read by key
 * via `copy(CopyKeys.X)`. Scans **every** `.kt` under `ui/` recursively (screens, app, components) so
 * a new screen can't silently drift back to literals.
 *
 * Matching is over the whole file text (not line-by-line), so a literal split across lines — e.g.
 * `Text(\n  "…")` or `contentDescription = if (x) "a" else "b"` — is still caught. A line carrying
 * `// copy-exempt` is skipped, so the rare genuine non-UI string (brand wordmark, model id, demo/
 * sample data, format glyph) stays visible in diffs.
 */
class NoComposeStringLiteralsTest {

    // Named args that carry user-facing copy. `[^\n"]*` before the quote lets us catch a literal even
    // when it sits behind an `if (…)`/elvis on the same line, e.g.
    // `contentDescription = if (reveal) "Hide key" else "Show key"`.
    private val copyArgs =
        "title|text|subtitle|label|placeholder|body|cta|confirmLabel|cancelLabel|hint|" +
            "contentDescription|heading|eyebrow|heard"

    // Text-first components whose first positional arg is user-facing copy.
    private val textFirstComponents =
        "QuillPrimaryButton|QuillTonalButton|QuillGhostButton|QuillDangerButton|QuillChip|" +
            "FieldLabel|HelpText|SectionHeader|QuillTopBarTitle"

    private val patterns = listOf(
        Regex("Text\\s*\\(\\s*\""), // Text("…")  (\s spans newlines → multiline form is caught)
        Regex("\\b(?:$copyArgs)\\s*=\\s*[^\\n\"]*\""), // named-arg literals (Banner/QuillRow/fields/icons)
        Regex("\\b(?:$textFirstComponents)\\s*\\(\\s*\""), // text-first component literals
    )

    @Test
    fun uiComposables_useCopyKeys_notLiterals() {
        val base = baseUiDir()
        val offenders = sortedSetOf<String>()
        base.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            val lines = text.lines()
            patterns.forEach { rx ->
                rx.findAll(text).forEach { m ->
                    val startLine = text.lineIndexOf(m.range.first)
                    val quoteLine = text.lineIndexOf(m.range.last)
                    // The exemption may sit on the call line or the literal's line (multiline case).
                    val exempt = (startLine..quoteLine).any { lines[it].contains("copy-exempt") }
                    if (!exempt) {
                        offenders += "${file.relativeTo(base).path.replace('\\', '/')}:${quoteLine + 1}: " +
                            lines[quoteLine].trim()
                    }
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

    /** 0-based index of the line containing the char at [index]. */
    private fun String.lineIndexOf(index: Int): Int = substring(0, index).count { it == '\n' }

    private fun baseUiDir(): File = listOf(
        File("app/src/main/java/com/tihor/quill/ui"),
        File("src/main/java/com/tihor/quill/ui"),
    ).firstOrNull { it.isDirectory }
        ?: error("Could not locate ui/ source dir (working dir = ${File("").absolutePath})")
}
