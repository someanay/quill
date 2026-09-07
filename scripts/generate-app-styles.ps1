param([switch]$Check)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$rows = @(Import-Csv (Join-Path $root 'docs/app-style-catalog.csv'))
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$lines = [Collections.Generic.List[string]]::new()
$lines.Add(@'
package com.tihor.quill.style

/**
 * Offline app defaults generated from docs/app-style-catalog.csv.
 * See docs/app-styles.md for sources and assignment policy.
 * User overrides take priority; unlisted apps use the user's global default.
 */
object CuratedAppStyles {
    val MAP: Map<String, WritingStyle> = mapOf(
'@)
foreach ($row in ($rows | Sort-Object -Property style,package)) {
    if ($row.package -notmatch '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$') {
        throw "Invalid package: $($row.package)"
    }
    if (-not $seen.Add($row.package)) { throw "Duplicate package: $($row.package)" }
    if ($row.style -notin @('FORMAL','NEUTRAL','CASUAL')) { throw "Invalid style: $($row.style)" }
    if ($row.source -notmatch '^https://(play.google.com/store/apps/details\?|f-droid.org/packages/)') {
        throw "Missing catalog source for $($row.package)"
    }
    $label = (($row.name -replace '[\r\n\u2028\u2029]', ' ') -replace '\uFEFF', '').Trim()
    $lines.Add('        "' + $row.package + '" to WritingStyle.' + $row.style + ', // ' + $label)
}
$lines.Add(@'
    )

    operator fun get(packageName: String?): WritingStyle? = packageName?.let { MAP[it] }
}
'@)
$output = ($lines -join "`n").Replace("`r`n", "`n") + "`n"
$target = Join-Path $root 'app/src/main/java/com/tihor/quill/style/CuratedAppStyles.kt'
if ($Check) {
    if (([IO.File]::ReadAllText($target).Replace("`r`n", "`n")) -cne $output) {
        throw 'CuratedAppStyles.kt is out of date. Run scripts/generate-app-styles.ps1.'
    }
} else {
    [IO.File]::WriteAllText($target, $output, [Text.UTF8Encoding]::new($false))
}
Write-Output "Validated $($rows.Count) app style defaults."
