namespace Quill.Windows.Copy;

/// <summary>
/// Copy Catalog key policy for the Windows app — the C# mirror of the Android app's
/// <c>CopyKeys</c>. The full key set and the baked-in defaults live in
/// <c>Assets/copy/strings.default.json</c> (the single source of truth for text); this class
/// only classifies keys the manifest generator cannot infer from the strings alone.
///
/// COMPLIANCE (locked): the privacy / data-safety promises and the bring-your-own-key warning.
/// These are locked to the binary and must NEVER be soft-coded behind a remote-override layer —
/// exactly the Quill hard rule (see repo CLAUDE.md and spec §2.3): they are the strings Google /
/// store review requires to match real behaviour, and the ones an attacker controlling a copy
/// host would most want to rewrite (phishing / false privacy claims). The copy tool renders these
/// read-only and refuses to stage an override for them.
///
/// There is no runtime consumer of these sets yet — the Windows app still renders its baked-in
/// literals directly. They exist so the generated <c>windows/copy-manifest.json</c> marks each key
/// <c>overridable</c> correctly for the copy-authoring tool. When the views are wired to the
/// catalog (and, later, on-device OTA lands), the same policy applies unchanged.
/// </summary>
public static class CopyKeys
{
    /// <summary>Keys that must stay binary-only: never overridable, never OTA-overridable.</summary>
    public static readonly IReadOnlySet<string> Compliance = new HashSet<string>
    {
        "compliance.privacy.private_by_construction_eyebrow",
        "compliance.privacy.no_account_no_server",
        "compliance.privacy.byok_key_required",
        "compliance.privacy.provider_cost_no_key",
        "compliance.privacy.audio_temp_history_local",
        "compliance.privacy.chip_key_label",
        "compliance.privacy.chip_key_value",
        "compliance.privacy.chip_audio_label",
        "compliance.privacy.chip_audio_value",
        "compliance.privacy.chip_backend_label",
        "compliance.privacy.chip_backend_value",
        "compliance.privacy.private_by_construction_lead",
        "compliance.privacy.audio_deleted_no_server",
        "compliance.privacy.sensitive_field_blocked_detail",
        "compliance.privacy.audio_direct_to_provider",
    };

    /// <summary>
    /// Keys whose baked-in default is intentionally blank (an override may contain no % tokens).
    /// None on Windows today — status containers that start empty are filled from other keys at
    /// runtime, so they are not catalog keys themselves.
    /// </summary>
    public static readonly IReadOnlySet<string> OptionalBlank = new HashSet<string>();
}
