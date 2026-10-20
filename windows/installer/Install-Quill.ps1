param(
    [string]$Destination = (Join-Path $env:LOCALAPPDATA 'Programs\Quill'),
    [switch]$NoLaunch, [switch]$NoShortcut, [switch]$NoRegistration
)
$ErrorActionPreference = 'Stop'
$packageRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$appSource = [IO.Path]::GetFullPath((Join-Path $packageRoot 'app'))
$Destination = [IO.Path]::GetFullPath($Destination).TrimEnd([IO.Path]::DirectorySeparatorChar)
if ($Destination -eq [IO.Path]::GetPathRoot($Destination).TrimEnd([IO.Path]::DirectorySeparatorChar)) { throw 'Choose a dedicated application folder.' }
if (-not (Test-Path -LiteralPath (Join-Path $appSource 'Quill.exe') -PathType Leaf)) { throw 'Extract the complete Quill download before installing.' }
if ((Get-Item -LiteralPath $appSource -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Package links are not supported.' }
if ($Destination -eq $packageRoot -or $Destination.StartsWith($packageRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or $packageRoot.StartsWith($Destination + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Choose an installation folder separate from the extracted package.' }
foreach ($entry in Get-ChildItem -LiteralPath $appSource -Force -Recurse) {
    if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Package links are not supported.' }
}
$files = @(Get-ChildItem -LiteralPath $appSource -File -Recurse -Force)
$verified = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($line in Get-Content -LiteralPath (Join-Path $packageRoot 'SHA256SUMS')) {
    if ($line -notmatch '^([a-fA-F0-9]{64})  (.+)$') { throw 'Invalid package checksum entry.' }
    $expected = $Matches[1]
    $relative = $Matches[2].Replace('/',[IO.Path]::DirectorySeparatorChar)
    $filePath = [IO.Path]::GetFullPath((Join-Path $packageRoot $relative))
    if (-not $filePath.StartsWith($appSource + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Package checksum path escapes app folder.' }
    if (-not $verified.Add($filePath)) { throw 'Duplicate package checksum entry.' }
    if ((Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash -ne $expected) { throw "Package verification failed: $relative" }
}
if ($verified.Count -ne $files.Count) { throw 'Package checksum inventory is incomplete.' }
foreach ($file in $files) { if (-not $verified.Contains($file.FullName)) { throw 'Package contains an unverified file.' } }
$uninstallSource = Join-Path $packageRoot 'Uninstall-Quill.ps1'
if (-not (Test-Path -LiteralPath $uninstallSource -PathType Leaf)) { throw 'The Quill uninstaller is missing from this package.' }
$walk = $Destination
while ($walk) {
    if ((Test-Path -LiteralPath $walk) -and ((Get-Item -LiteralPath $walk -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Installation through a directory link is not supported.' }
    $parent = [IO.Directory]::GetParent($walk)
    $walk = if ($parent) { $parent.FullName } else { $null }
}
if (Test-Path -LiteralPath $Destination) {
    foreach ($entry in Get-ChildItem -LiteralPath $Destination -Force -Recurse) {
        if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'The installation folder contains a directory or file link.' }
    }
}
$appExecutable = Join-Path $Destination 'Quill.exe'
if (Get-Process Quill -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $appExecutable }) { throw 'Quit Quill from its tray menu before installing this update.' }
$previousFiles = @()
$previousReceipt = Join-Path $Destination '.quill-install.json'
if (Test-Path -LiteralPath $previousReceipt) {
    $previous = Get-Content -LiteralPath $previousReceipt -Raw | ConvertFrom-Json
    if ($previous.schema -ne 1 -or -not [string]::Equals($previous.destination,$Destination,[StringComparison]::OrdinalIgnoreCase)) { throw 'The previous installation receipt does not match this folder.' }
    foreach ($relative in $previous.files) {
        $owned = [IO.Path]::GetFullPath((Join-Path $Destination $relative))
        if ([IO.Path]::IsPathRooted($relative) -or -not $owned.StartsWith($Destination + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'An existing installation entry escapes its folder.' }
        if ((Test-Path -LiteralPath $owned) -and -not (Test-Path -LiteralPath $owned -PathType Leaf)) { throw 'An existing application file was replaced by a folder.' }
        $previousFiles += $relative
    }
}
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
Get-ChildItem -LiteralPath $appSource -Force | Copy-Item -Destination $Destination -Recurse -Force
Copy-Item -LiteralPath $uninstallSource -Destination $Destination -Force
$installedFiles = @($files | ForEach-Object { $_.FullName.Substring($appSource.Length + 1) }) + @('Uninstall-Quill.ps1')
foreach ($relative in $previousFiles) {
    if ($relative -notin $installedFiles) {
        $obsolete = [IO.Path]::GetFullPath((Join-Path $Destination $relative))
        if (Test-Path -LiteralPath $obsolete -PathType Leaf) { Remove-Item -LiteralPath $obsolete -Force }
    }
}
$shortcutPath = Join-Path ([Environment]::GetFolderPath('Programs')) 'Quill.lnk'
if (-not $NoShortcut) {
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = $appExecutable
    $shortcut.WorkingDirectory = $Destination
    $shortcut.Description = 'Quill — dictate with your own provider key'
    $shortcut.IconLocation = Join-Path $Destination 'Assets\quill.ico'
    $shortcut.Save()
}
$registration = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\Quill.NativeV2'
if (-not $NoRegistration) {
    New-Item -Path $registration -Force | Out-Null
    $values = @{
        DisplayName = 'Quill'; DisplayVersion = '2.0.3'; InstallLocation = $Destination
        DisplayIcon = $appExecutable
        UninstallString = 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $Destination 'Uninstall-Quill.ps1') + '"'
    }
    foreach ($name in $values.Keys) { New-ItemProperty -Path $registration -Name $name -Value $values[$name] -PropertyType String -Force | Out-Null }
    New-ItemProperty -Path $registration -Name NoModify -Value 1 -PropertyType DWord -Force | Out-Null
    New-ItemProperty -Path $registration -Name NoRepair -Value 1 -PropertyType DWord -Force | Out-Null
}
$receipt = @{ schema = 1; destination = $Destination; files = $installedFiles; shortcut = (-not $NoShortcut); registration = (-not $NoRegistration) }
[IO.File]::WriteAllText((Join-Path $Destination '.quill-install.json'),($receipt | ConvertTo-Json -Depth 4),[Text.UTF8Encoding]::new($false))
Write-Output "Quill installed in $Destination. Settings and provider keys are preserved."
if (-not $NoLaunch) { Start-Process -FilePath $appExecutable -WindowStyle Hidden }
