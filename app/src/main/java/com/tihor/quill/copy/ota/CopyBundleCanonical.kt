package com.tihor.quill.copy.ota

/**
 * The **exact** byte sequence that an OTA copy bundle's Ed25519 signature is computed over (M13a).
 *
 * The signature does **not** cover the raw bytes of the received file — those include the `signature`
 * field itself and arbitrary whitespace/key ordering. Instead, both the signer (M13b authoring/CI) and
 * the verifier reconstruct a canonical byte string from the *parsed* `(revision, strings)` and sign /
 * verify over that. Wire-format whitespace and key order are therefore irrelevant; only the canonical
 * reconstruction matters.
 *
 * ## Canonical form (M13b MUST reproduce this byte-for-byte)
 *
 * Compact UTF-8 JSON, no insignificant whitespace, with exactly two top-level members **in this order**:
 *
 * ```
 * {"revision":<R>,"strings":{<members>}}
 * ```
 *
 * - `<R>`: the revision as a base-10 integer in shortest form — no leading zeros (except the literal
 *   `0`), no `+` sign, no decimal point (it is an `Int`). Equivalent to Kotlin `Int.toString()`.
 * - `<members>`: each `strings` entry rendered as `"<key>":"<value>"`, joined by `,` with **no spaces**.
 *   Entries are ordered by their **keys ascending in UTF-8 byte lexicographic order**. (Every key the app
 *   uses is ASCII, so this equals Unicode code-point order and Java `String.compareTo`.)
 * - Both keys and values are emitted as JSON string literals with this escaping, and **nothing else**:
 *   `\` → `\\`, `"` → `\"`, U+0008 → `\b`, U+0009 → `\t`, U+000A → `\n`, U+000C → `\f`, U+000D → `\r`;
 *   any other character with code point `< 0x20` → `\u00xx` (lowercase hex, 4 digits). Every other
 *   character — including `/`, `$`, and all non-ASCII — is emitted **literally as UTF-8** (no escaping).
 * - The result is encoded as **UTF-8 with no BOM and no trailing newline**.
 *
 * ### Worked example
 * `revision = 42`, `strings = {"a.b": "Hi", "z": "%1$d"}` canonicalizes to the UTF-8 bytes of:
 * ```
 * {"revision":42,"strings":{"a.b":"Hi","z":"%1$d"}}
 * ```
 */
internal object CopyBundleCanonical {

    /** Build the canonical signing input for [revision] + [strings]. See the class KDoc for the spec. */
    fun bytes(revision: Int, strings: Map<String, String>): ByteArray =
        string(revision, strings).toByteArray(Charsets.UTF_8)

    /** The canonical string (UTF-8 bytes of this are what gets signed/verified). */
    fun string(revision: Int, strings: Map<String, String>): String {
        val sb = StringBuilder()
        sb.append("{\"revision\":").append(revision).append(",\"strings\":{")
        // Sort by UTF-8 bytes. For the ASCII keys in use this is identical to String natural order, but
        // we compare by encoded bytes so a future non-ASCII key still matches the documented rule.
        val sortedKeys = strings.keys.sortedWith(byUtf8Bytes)
        sortedKeys.forEachIndexed { i, key ->
            if (i > 0) sb.append(',')
            appendJsonString(sb, key)
            sb.append(':')
            appendJsonString(sb, strings.getValue(key))
        }
        sb.append("}}")
        return sb.toString()
    }

    private val byUtf8Bytes = Comparator<String> { a, b ->
        val ba = a.toByteArray(Charsets.UTF_8)
        val bb = b.toByteArray(Charsets.UTF_8)
        val n = minOf(ba.size, bb.size)
        var i = 0
        while (i < n) {
            // Compare as unsigned bytes.
            val d = (ba[i].toInt() and 0xFF) - (bb[i].toInt() and 0xFF)
            if (d != 0) return@Comparator d
            i++
        }
        ba.size - bb.size
    }

    private fun appendJsonString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c.code) {
                0x5C -> sb.append("\\\\") // backslash
                0x22 -> sb.append("\\\"") // double quote
                0x08 -> sb.append("\\b")
                0x09 -> sb.append("\\t")
                0x0A -> sb.append("\\n")
                0x0C -> sb.append("\\f")
                0x0D -> sb.append("\\r")
                else ->
                    if (c.code < 0x20) {
                        sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(c)
                    }
            }
        }
        sb.append('"')
    }
}
