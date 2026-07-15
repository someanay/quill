package com.tihor.quill.copy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard for the generated repo-root `copy-manifest.json` (the Copy Catalog export the copy-authoring tool
 * consumes). Regenerates the content in memory from [CopyKeys] + `strings.default.json` via the same
 * [CopyManifest.render] the `:app:generateCopyManifest` task uses, and asserts the committed file matches
 * **byte for byte**. Same pattern as `NoComposeStringLiteralsTest`: the file cannot drift from the Copy
 * Catalog without a red build, and the failure message says exactly how to regenerate it.
 */
class CopyManifestTest {

    @Test
    fun committedManifest_matchesGeneratedContent_byteForByte() {
        val catalog = CopyManifest.loadCatalog()
        val expected = CopyManifest.render(catalog.strings, catalog.revision)
        val file = CopyManifest.manifestFile()

        assertTrue(
            "copy-manifest.json is missing at ${file.absolutePath} — " +
                "generate and commit it with:  ./gradlew :app:generateCopyManifest",
            file.isFile,
        )
        assertEquals(
            "copy-manifest.json is stale: it drifted from CopyKeys / strings.default.json. " +
                "Regenerate and commit it with:  ./gradlew :app:generateCopyManifest",
            expected,
            file.readText(),
        )
    }
}
