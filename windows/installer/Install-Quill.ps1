$ErrorActionPreference = 'Stop'

$installDir = Join-Path $env:LOCALAPPDATA 'Programs\Quill'
$startMenuDir = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Quill'
$payload = Join-Path $PSScriptRoot 'payload.zip'
$uninstallerSource = Join-Path $PSScriptRoot 'Uninstall-Quill.ps1'

New-Item -ItemType Directory -Force -Path $installDir, $startMenuDir | Out-Null
Expand-Archive -Path $payload -DestinationPath $installDir -Force
Copy-Item -LiteralPath $uninstallerSource -Destination (Join-Path $installDir 'Uninstall-Quill.ps1') -Force

$exe = Join-Path $installDir 'Quill.Windows.exe'
$shortcutPath = Join-Path $startMenuDir 'Quill.lnk'
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $exe
$shortcut.WorkingDirectory = $installDir
$shortcut.IconLocation = "$exe,0"
$shortcut.Description = 'Quill voice dictation'
$shortcut.Save()

$uninstallKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\Quill'
New-Item -Path $uninstallKey -Force | Out-Null
Set-ItemProperty -Path $uninstallKey -Name DisplayName -Value 'Quill for Windows'
Set-ItemProperty -Path $uninstallKey -Name DisplayVersion -Value '0.1.0'
Set-ItemProperty -Path $uninstallKey -Name Publisher -Value 'Quill'
Set-ItemProperty -Path $uninstallKey -Name InstallLocation -Value $installDir
Set-ItemProperty -Path $uninstallKey -Name DisplayIcon -Value "$exe,0"
Set-ItemProperty -Path $uninstallKey -Name UninstallString -Value "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$installDir\Uninstall-Quill.ps1`""

Start-Process -FilePath $exe -WorkingDirectory $installDir
Write-Host "Quill installed. A Start Menu shortcut was created at: $shortcutPath"
