package com.tihor.quill.style

/**
 * Resolves which [WritingStyle] a dictation should be written in (scope doc §5). Three tiers,
 * evaluated fresh on every dictation, and it **always** resolves — there is no "no style" outcome:
 *
 * ```
 * 1. User override for this package   (persisted; WritingStyleStore)
 * 2. Curated default for this package (in code; CuratedAppStyles)
 * 3. Global default                   (user-set; ships Neutral)
 * ```
 *
 * A null/blank package (the accessibility service couldn't tell us what was focused) skips tiers 1 and
 * 2 and lands on the global default, rather than failing — style must never be able to block or alter
 * a dictation's delivery.
 *
 * Because tier 2 is consulted at resolve time and never written to storage, deleting an override drops
 * straight back to the *curated* default — not to whatever the override happened to be, and not to a
 * stale snapshot of an older curated guess. That is the whole of "reset to default".
 *
 * Pure (no Android, no storage): both maps are passed in, so the ordering is directly unit-testable —
 * same contract as `BubbleGeometry` and `CleanupGuard`.
 */
object WritingStyleResolver {

    /**
     * @param packageName the app focused when recording started (`OverlayService.recordingPackage`).
     * @param overrides the user's explicit per-app choices — the only tier that is persisted.
     * @param globalDefault the user's global default; ships as [WritingStyle.DEFAULT].
     * @param curated the curated in-code map; injectable so tests can pin their own fixture.
     */
    fun resolve(
        packageName: String?,
        overrides: Map<String, WritingStyle>,
        globalDefault: WritingStyle,
        curated: Map<String, WritingStyle> = CuratedAppStyles.MAP,
    ): WritingStyle {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return globalDefault
        return overrides[pkg] ?: curated[pkg] ?: globalDefault
    }
}
