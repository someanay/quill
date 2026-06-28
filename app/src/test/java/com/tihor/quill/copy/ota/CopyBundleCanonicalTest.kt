package com.tihor.quill.copy.ota

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The canonical signing input must be byte-stable and match the documented contract (M13a). M13b's
 * signer reproduces these exact bytes, so any drift here is a breaking change.
 */
class CopyBundleCanonicalTest {

    @Test
    fun workedExample_matchesDoc() {
        val s = CopyBundleCanonical.string(42, linkedMapOf("z" to "%1\$d", "a.b" to "Hi"))
        // Keys sorted ascending; no whitespace; member order revision then strings; `$` not escaped.
        assertEquals("""{"revision":42,"strings":{"a.b":"Hi","z":"%1${'$'}d"}}""", s)
    }

    @Test
    fun emptyStrings() {
        assertEquals("""{"revision":0,"strings":{}}""", CopyBundleCanonical.string(0, emptyMap()))
    }

    @Test
    fun keysSortedByUtf8Bytes_regardlessOfInsertionOrder() {
        val a = CopyBundleCanonical.string(1, linkedMapOf("b" to "1", "a" to "2", "c" to "3"))
        val b = CopyBundleCanonical.string(1, linkedMapOf("c" to "3", "a" to "2", "b" to "1"))
        assertEquals(a, b)
        assertEquals("""{"revision":1,"strings":{"a":"2","b":"1","c":"3"}}""", a)
    }

    @Test
    fun escaping_quoteBackslashAndControls() {
        val s = CopyBundleCanonical.string(
            1,
            mapOf("k" to "a\"b\\c\nd\te"),
        )
        assertEquals("""{"revision":1,"strings":{"k":"a\"b\\c\nd\te"}}""", s)
    }

    @Test
    fun escaping_otherControlCharUsesLowercaseUnicode() {
        val s = CopyBundleCanonical.string(1, mapOf("k" to "x\u000By"))
        assertEquals("""{"revision":1,"strings":{"k":"x\u000by"}}""", s)
    }

    @Test
    fun nonAsciiAndSlash_emittedRaw() {
        // Forward slash is NOT escaped; non-ASCII is emitted literally as UTF-8 (no \\u escaping).
        val s = CopyBundleCanonical.string(1, mapOf("k" to "a/b — café 你好"))
        assertEquals("""{"revision":1,"strings":{"k":"a/b — café 你好"}}""", s)
    }

    @Test
    fun bytes_areUtf8OfString() {
        val rev = 7
        val strings = mapOf("k" to "café")
        assertEquals(
            CopyBundleCanonical.string(rev, strings),
            CopyBundleCanonical.bytes(rev, strings).toString(Charsets.UTF_8),
        )
    }
}
