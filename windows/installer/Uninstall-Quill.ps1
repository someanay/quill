$ErrorActionPreference = 'Stop'
$installRoot = [IO.Path]::GetFullPath($PSScriptRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
if ($installRoot -eq [IO.Path]::GetPathRoot($installRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)) { throw 'Refusing to uninstall from a drive root.' }
$receiptPath = Join-Path $installRoot '.quill-install.json'
$receipt = Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
if ($receipt.schema -ne 1 -or -not [string]::Equals($receipt.destination,$installRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'The installation receipt does not match this folder.' }
$walk = $installRoot
while ($walk) {
    if ((Get-Item -LiteralPath $walk -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing to uninstall through a directory link.' }
    $parent = [IO.Directory]::GetParent($walk)
    $walk = if ($parent) { $parent.FullName } else { $null }
}
$targets = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($relative in $receipt.files) {
    if ([IO.Path]::IsPathRooted($relative)) { throw 'Invalid absolute uninstall entry.' }
    $target = [IO.Path]::GetFullPath((Join-Path $installRoot $relative))
    if (-not $target.StartsWith($installRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Uninstall entry escapes the installation folder.' }
    if (-not $targets.Add($target)) { throw 'Duplicate uninstall entry.' }
}
foreach ($entry in Get-ChildItem -LiteralPath $installRoot -Recurse -Force) {
    if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'The installation folder contains a directory or file link.' }
}
foreach ($target in $targets) { if ((Test-Path -LiteralPath $target) -and -not (Test-Path -LiteralPath $target -PathType Leaf)) { throw 'An uninstall file was replaced by a folder.' } }
$appExecutable = Join-Path $installRoot 'Quill.exe'
Get-Process Quill -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $appExecutable } | Stop-Process
if ($receipt.shortcut) {
    $shortcutPath = Join-Path ([Environment]::GetFolderPath('Programs')) 'Quill.lnk'
    if (Test-Path -LiteralPath $shortcutPath) {
        $shell = New-Object -ComObject WScript.Shell
        if ($shell.CreateShortcut($shortcutPath).TargetPath -eq $appExecutable) { Remove-Item -LiteralPath $shortcutPath -Force }
    }
}
if ($receipt.registration) {
    $registration = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\Quill.NativeV2'
    if ((Get-ItemProperty -Path $registration -Name InstallLocation -ErrorAction SilentlyContinue).InstallLocation -eq $installRoot) { Remove-Item -LiteralPath $registration }
    $startupPath = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
    $startupCommand = (Get-ItemProperty -LiteralPath $startupPath -Name Quill -ErrorAction SilentlyContinue).Quill
    if ([string]::Equals($startupCommand,('"' + $appExecutable + '" --background'),[StringComparison]::OrdinalIgnoreCase)) {
        Remove-ItemProperty -LiteralPath $startupPath -Name Quill
    }
}
foreach ($target in $targets) { if (Test-Path -LiteralPath $target -PathType Leaf) { Remove-Item -LiteralPath $target -Force } }
Remove-Item -LiteralPath $receiptPath -Force
foreach ($directory in Get-ChildItem -LiteralPath $installRoot -Directory -Recurse -Force | Sort-Object { $_.FullName.Length } -Descending) {
    if ([IO.Directory]::GetFileSystemEntries($directory.FullName).Length -eq 0) { Remove-Item -LiteralPath $directory.FullName }
}
if ([IO.Directory]::GetFileSystemEntries($installRoot).Length -eq 0) { Remove-Item -LiteralPath $installRoot }
Write-Output 'Quill uninstalled. Local settings, history and provider keys were preserved.'
