param([switch]$Cleanup)

$ErrorActionPreference = 'SilentlyContinue'
$installDir = Split-Path -Parent $PSCommandPath
$startMenuDir = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Quill'
$uninstallKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\Quill'

if (-not $Cleanup) {
    $tempScript = Join-Path $env:TEMP "Quill-Uninstall-$PID.ps1"
    Copy-Item -LiteralPath $PSCommandPath -Destination $tempScript -Force
    Start-Process powershell.exe -WindowStyle Hidden -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $tempScript, '-Cleanup')
    exit
}

Start-Sleep -Seconds 1
Remove-Item -Path $startMenuDir -Recurse -Force
Remove-Item -Path $uninstallKey -Recurse -Force
Remove-Item -Path $installDir -Recurse -Force
Remove-Item -LiteralPath $PSCommandPath -Force
