param([string]$Destination = (Join-Path $env:LOCALAPPDATA 'Programs\Quill'),[switch]$NoLaunch,[switch]$NoShortcut)
$ErrorActionPreference = 'Stop'
$packageRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$appSource = Join-Path $packageRoot 'app'
if (-not (Test-Path -LiteralPath (Join-Path $appSource 'Quill.exe'))) { throw 'Extract the complete Quill download before installing.' }
$Destination = [IO.Path]::GetFullPath($Destination)
if ($Destination -eq [IO.Path]::GetPathRoot($Destination)) { throw 'Choose a dedicated application folder.' }
foreach ($line in Get-Content -LiteralPath (Join-Path $packageRoot 'SHA256SUMS')) {
    if ($line -notmatch '^([a-fA-F0-9]{64})  (.+)$') { throw 'Invalid package checksum entry.' }
    $expected = $Matches[1]
    $relative = $Matches[2].Replace('/',[IO.Path]::DirectorySeparatorChar)
    $filePath = [IO.Path]::GetFullPath((Join-Path $packageRoot $relative))
    if (-not $filePath.StartsWith($appSource + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Package checksum path escapes app folder.' }
    if ((Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash -ne $expected) { throw "Package verification failed: $relative" }
}
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
Get-ChildItem -LiteralPath $appSource -Force | Copy-Item -Destination $Destination -Recurse -Force
if (-not $NoShortcut) {
    $shortcutPath = Join-Path ([Environment]::GetFolderPath('Programs')) 'Quill.lnk'
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = Join-Path $Destination 'Quill.exe'
    $shortcut.WorkingDirectory = $Destination
    $shortcut.Description = 'Quill — dictate with your own provider key'
    $shortcut.IconLocation = Join-Path $Destination 'Assets\quill.ico'
    $shortcut.Save()
}
Write-Output "Quill installed in $Destination. Settings and provider keys are preserved."
if (-not $NoLaunch) { Start-Process -FilePath (Join-Path $Destination 'Quill.exe') -WindowStyle Hidden }
