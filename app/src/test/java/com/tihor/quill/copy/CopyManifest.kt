package com.tihor.quill.copy

import com.tihor.quill.copy.ota.Placeholders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * Generator for the repo-root `copy-manifest.json` — a machine-readable export of the Copy Catalog for
 * the external copy-authoring tool (quill-copy-tool). Deterministic and committed; [CopyManifestTest]
 * fails the build if the committed file drifts from [CopyKeys] / `strings.default.json`, the same guard
 * pattern as `NoComposeStringLiteralsTest`. Regenerate with `./gradlew :app:generateCopyManifest`.
 *
 * Lives in the **test** source set on purpose: the manifest is tooling metadata, not a runtime asset, so
 * neither it nor this generator should ship in the APK (that is also why the file goes at the repo root,
 * not under `assets/`). Placeholder tokens come from the app's own [Placeholders] parser — the exact code
 * the device-side bundle gauntlet uses for parity — so the copy tool can prove its extractor agrees with
 * the device instead of reimplementing (and drifting from) the contract.
 */
object CopyManifest {

    /**
     * Inputs for the `placeholderTestVectors` block; the expected tokens are always computed by
     * [Placeholders], never hand-written, so the vectors document the parser's real behaviour (quirks
     * included — e.g. `"50% off"` is read as a `%o` specifier). Covers: empty, no tokens, `%s`, `%d`,
     * explicit positional, precision, repeated positional, two-implicit, out-of-order explicit, width+
     * precision, `%%` escape, literal-percent, mixed literal+token, `%n`, and token+escape.
     */
    val TEST_VECTORS: List<String> = listOf(
        "",
        "No placeholders here",
        "%s",
        "%d",
        "%1\$s",
        "%2\$.2f",
        "%1\$s and %1\$s again",
        "%s = %d",
        "%2\$s then %1\$d",
        "%05.2f",
        "100%%",
        "50% off",
        "%1\$d items",
        "line one%nline two",
        "%d%% done",
    )

    /**
     * Placeholder tokens for [template], derived from [Placeholders.signature] (argument-index ->
     * conversion char). Rendered as canonical `%<index>$<conv>` tokens sorted by argument index, so
     * `"%s"` and `"%1$s"` both normalise to `["%1$s"]` — exactly the parity contract the verifier checks.
     */
    fun placeholderTokens(template: String): List<String> =
        Placeholders.signature(template).toSortedMap().map { (index, conv) -> "%$index\$$conv" }

    /**
     * The exact `copy-manifest.json` content: keys sorted by UTF-8 byte order, 2-space indent, `\n` line
     * endings, trailing newline, no timestamps. Every [CopyKeys.ALL] key appears exactly once.
     */
    fun render(strings: Map<String, String>, revision: Int): String {
        val root = buildJsonObject {
            put("revision", revision)
            putJsonObject("keys") {
                CopyKeys.ALL.sortedWith(byUtf8Bytes).forEach { key ->
                    val default = strings[key]
                        ?: error("strings.default.json is missing a key referenced in CopyKeys.ALL: $key")
                    putJsonObject(key) {
                        put("default", default)
                        putJsonArray("placeholders") { placeholderTokens(default).forEach { add(it) } }
                        put("overridable", key in CopyKeys.OVERRIDABLE)
                        put("optionalBlank", key in CopyKeys.OPTIONAL_BLANK)
                    }
                }
            }
            putJsonArray("placeholderTestVectors") {
                TEST_VECTORS.forEach { input ->
                    addJsonObject {
                        put("input", input)
                        putJsonArray("tokens") { placeholderTokens(input).forEach { add(it) } }
                    }
                }
            }
        }
        return PRETTY.encodeToString(JsonObject.serializer(), root) + "\n"
    }

    /** Parse the baked-in catalog (revision + strings) straight off the assets file — no Android needed. */
    fun loadCatalog(): CopyCatalog =
        LENIENT.decodeFromString(CopyCatalog.serializer(), assetsFile().readText())

    /** The committed manifest at the repo root (never under `assets/` — it must not ship in the APK). */
    fun manifestFile(): File = repoRoot().resolve("copy-manifest.json")

    private fun assetsFile(): File = moduleDir().resolve("src/main/assets/copy/strings.default.json")

    /** The `app/` module dir, whether the process runs from the repo root or the module dir. */
    private fun moduleDir(): File = listOf(File("."), File("app"))
        .firstOrNull { File(it, "src/main/assets/copy/strings.default.json").isFile }
        ?.canonicalFile
        ?: error("Cannot locate the app module from cwd=${File(".").canonicalPath}")

    private fun repoRoot(): File = moduleDir().parentFile

    /** UTF-8 byte lexicographic order — the documented Copy Catalog key order (see CopyBundleCanonical). */
    private val byUtf8Bytes = Comparator<String> { a, b ->
        val ba = a.toByteArray(Charsets.UTF_8)
        val bb = b.toByteArray(Charsets.UTF_8)
        val n = minOf(ba.size, bb.size)
        for (i in 0 until n) {
            val d = (ba[i].toInt() and 0xFF) - (bb[i].toInt() and 0xFF)
            if (d != 0) return@Comparator d
        }
        ba.size - bb.size
    }

    private val PRETTY = Json { prettyPrint = true; prettyPrintIndent = "  " }
    private val LENIENT = Json { ignoreUnknownKeys = true }
}

/** Entry point for the `:app:generateCopyManifest` Gradle task. Writes the manifest to the repo root. */
fun main() {
    val catalog = CopyManifest.loadCatalog()
    val out = CopyManifest.manifestFile()
    out.writeText(CopyManifest.render(catalog.strings, catalog.revision))
    println("Wrote ${out.absolutePath} (${catalog.strings.size} strings, revision ${catalog.revision})")
}
