package com.tihor.quill.copy

/**
 * Format-placeholder parity (M13a, spec §2.3 / §6). UI copy is rendered with `String.format`
 * (`template.format(args…)`), so a source edit that changes a value's placeholder shape would crash
 * formatting at the call site (e.g. baked-in `"%1$d set"`, override `"set"` then `.format(3)` is fine,
 * but override `"%1$s of %2$d"` throws `MissingFormatArgumentException`). We therefore reject any
 * override whose placeholder signature differs from the baked-in template for that key.
 *
 * The signature is the map **argument-index -> conversion char**. Two templates are parity-equal iff
 * their signatures are equal. This is deliberately conservative — it also rejects benign placeholder
 * removals — which is the safe direction and matches the spec ("placeholders MATCH the baked-in
 * template"). The vast majority of keys have no placeholders, so parity is trivially `{} == {}` and
 * benign text edits pass.
 */
internal object Placeholders {

    // %[argument_index$][flags][width][.precision]conversion  — argument_index and conversion captured.
    // Matches Java Formatter syntax closely enough to compare arg contracts; `%%` (literal percent) and
    // `%n` (line separator) consume no argument and are ignored.
    //
    // The space is deliberately NOT in the flag class: literal copy like "50% off" must not parse a
    // phantom "% o" token. This keeps this signature in lock-step with the copy-authoring tool's
    // extractor (quill-copy-tool: src/lib/placeholders.ts / publish_gate.py), whose extractor-agreement
    // gate compares against the placeholderTestVectors this parser generates.
    private val SPEC = Regex("""%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?([a-zA-Z%])""")

    /** Argument-index -> conversion char for every argument-consuming specifier in [template]. */
    fun signature(template: String): Map<Int, Char> {
        val sig = mutableMapOf<Int, Char>()
        var implicit = 0
        for (m in SPEC.findAll(template)) {
            val conv = m.groupValues[2][0]
            if (conv == '%' || conv == 'n') continue // consume no argument
            val explicit = m.groupValues[1]
            val index = if (explicit.isNotEmpty()) explicit.toInt() else ++implicit
            // If the same index appears twice with different conversions the template is itself
            // malformed; record the first — equality still catches any divergence from baked-in.
            sig.putIfAbsent(index, conv)
        }
        return sig
    }

    /** True iff [candidate] has the same placeholder contract as [baked]. */
    fun parityMatches(baked: String, candidate: String): Boolean =
        signature(baked) == signature(candidate)
}
