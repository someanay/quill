package com.tihor.quill.copy.ota

import java.io.File

/**
 * Persists the last **validated** OTA copy bundle as its raw signed bytes in app-private storage
 * (M13a, spec §2.3 / §4.4). Storing the raw signed bytes (not a merged catalog) means the signature is
 * **re-verified on every launch** before re-apply, so a tampered cache file is rejected and we fall back
 * to baked-in — a corrupt cache can never poison the resolved copy.
 *
 * Writes are atomic (temp file + rename) so a crash mid-write can't leave a half-written bundle.
 *
 * The cache directory ([dir], `filesDir/copy`) is excluded from backup (`backup_rules.xml` /
 * `data_extraction_rules.xml`), consistent with the rest of app-private data.
 *
 * @param dir the directory the bundle file lives in (production: `File(context.filesDir, "copy")`).
 * A plain [File] dependency keeps this unit-testable with a temp dir, no Android framework needed.
 */
class CopyBundleCache(private val dir: File) {

    private val file = File(dir, BUNDLE_FILE)

    /** The cached raw signed bundle bytes, or null if none / unreadable. */
    fun read(): ByteArray? = try {
        if (file.isFile) file.readBytes() else null
    } catch (_: Exception) {
        null
    }

    /** Atomically replace the cached bundle with [rawBundleBytes]. Returns true on success. */
    fun write(rawBundleBytes: ByteArray): Boolean = try {
        if (!dir.exists()) dir.mkdirs()
        val tmp = File(dir, "$BUNDLE_FILE.tmp")
        tmp.writeBytes(rawBundleBytes)
        // renameTo is atomic on the same filesystem; fall back to delete+rename if the target exists.
        if (file.exists()) file.delete()
        val ok = tmp.renameTo(file)
        if (!ok) tmp.delete()
        ok
    } catch (_: Exception) {
        false
    }

    /** Remove the cached bundle (e.g. after it fails re-verification). */
    fun clear() {
        try {
            file.delete()
        } catch (_: Exception) {
            // best-effort
        }
    }

    companion object {
        private const val BUNDLE_FILE = "override.json"
    }
}
