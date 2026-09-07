param([ValidateSet('ios','macos','all')][string]$Platform = 'all')
$ErrorActionPreference = 'Stop'
$quillRoot = Split-Path -Parent $PSScriptRoot
$quillSwiftRoot = Join-Path $env:LOCALAPPDATA 'Programs\Swift'
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
$quillSdkVersion = Get-ChildItem -LiteralPath (Join-Path $quillSwiftRoot 'Platforms') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $quillSdkVersion) { throw 'Install the Swift Windows toolchain first.' }
$env:SDKROOT = Join-Path $quillSdkVersion.FullName 'Windows.platform\Developer\SDKs\Windows.sdk'
$quillVsWhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$quillVs = & $quillVsWhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (-not $quillVs) { throw 'Swift Windows tests require Visual Studio C++ build tools.' }
& (Join-Path $quillVs 'Common7\Tools\Launch-VsDevShell.ps1') -Arch amd64 -HostArch amd64 -SkipAutomaticLocation
$quillTargets = if ($Platform -eq 'all') { @('ios','macos') } else { @($Platform) }
foreach ($quillTarget in $quillTargets) {
    & swift test --package-path (Join-Path $quillRoot "$quillTarget\QuillCore") --jobs 2
    if ($LASTEXITCODE -ne 0) { throw "$quillTarget portable core tests failed." }
}
