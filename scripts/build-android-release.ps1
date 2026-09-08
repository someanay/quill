param(
    [ValidateRange(1,2100000000)][int]$VersionCode = 10,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]*$')][string]$VersionName = '1.0.9',
    [switch]$ApplyCopy,
    [string]$CopyToolPath = (Join-Path $env:USERPROFILE 'quill-copy-tool')
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
function Check-Exit([string]$step) { if ($LASTEXITCODE -ne 0) { throw "$step failed (exit $LASTEXITCODE)." } }
Push-Location $root
try {
    if ($ApplyCopy) {
        $toolRoot = (Resolve-Path -LiteralPath $CopyToolPath).Path
        $oldAppPath = $env:QUILL_APP_REPO_PATH
        try {
            $env:QUILL_APP_REPO_PATH = $root
            Push-Location $toolRoot
            try { & npm.cmd run apply:source; Check-Exit 'Apply copy to app source' }
            finally { Pop-Location }
        } finally { $env:QUILL_APP_REPO_PATH = $oldAppPath }
    }
    $versionArgs = @("-PquillVersionCode=$VersionCode", "-PquillVersionName=$VersionName", '-PrequireReleaseSigning=true')
    & "$root\gradlew.bat" generateCopyManifest @versionArgs
    Check-Exit 'Generate copy manifest'
    & "$root\gradlew.bat" testDebugUnitTest lintDebug assembleRelease bundleRelease @versionArgs
    Check-Exit 'Android release checks and build'

    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    if (-not $sdk -and (Test-Path "$root\local.properties")) {
        $sdkLine = Get-Content "$root\local.properties" | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) { $sdk = ($sdkLine -replace '^sdk\.dir=', '').Replace('\:', ':').Replace('\\', '\') }
    }
    if (-not $sdk) { throw 'Set ANDROID_HOME to verify the release signatures.' }
    $buildTools = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if (-not $buildTools) { throw 'Android SDK build tools are missing.' }
    $apk = "$root\app\build\outputs\apk\release\app-release.apk"
    $aab = "$root\app\build\outputs\bundle\release\app-release.aab"
    & (Join-Path $buildTools.FullName 'apksigner.bat') verify $apk
    Check-Exit 'APK signature verification'
    & (Join-Path $buildTools.FullName 'zipalign.exe') -c -P 16 4 $apk
    Check-Exit 'APK ZIP alignment'
    $jarsigner = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jarsigner.exe")) { "$env:JAVA_HOME\bin\jarsigner.exe" }
        elseif (Test-Path 'C:\Program Files\Android\Android Studio\jbr\bin\jarsigner.exe') { 'C:\Program Files\Android\Android Studio\jbr\bin\jarsigner.exe' }
        else { (Get-Command jarsigner.exe -ErrorAction Stop).Source }
    $signatureReport = & $jarsigner '-J-Duser.language=en' -verify $aab 2>&1
    Check-Exit 'AAB signature verification'
    if (($signatureReport -join "`n") -notmatch 'jar verified\.') { throw 'AAB is not signed.' }

    $output = Join-Path $root 'dist/android'
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    foreach ($extension in @('apk','aab')) {
        $inputFile = if ($extension -eq 'apk') { $apk } else { $aab }
        $filename = "Quill-$VersionName-release.$extension"
        $destination = Join-Path $output $filename
        Copy-Item -LiteralPath $inputFile -Destination $destination -Force
        $digest = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
        "$digest  $filename" | Set-Content -LiteralPath "$destination.sha256" -Encoding ascii
    }
    @{ versionCode=$VersionCode; versionName=$VersionName; builtAt=[DateTime]::UtcNow.ToString('o'); copyRevision=(Get-Content app/src/main/assets/copy/strings.default.json -Raw | ConvertFrom-Json).revision } |
        ConvertTo-Json | Set-Content (Join-Path $output 'latest-build.json') -Encoding utf8
    Write-Host "Signed APK and AAB ready in $output (version code $VersionCode)."
    Write-Host 'Upload the AAB in Play Console. Use a higher version code for every new Play upload.'
} finally { Pop-Location }
