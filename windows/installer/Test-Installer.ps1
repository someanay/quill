$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$runRoot = Join-Path $repository ('dist\verification\installer-tests\' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
$passed = 0
function Assert-True([bool]$condition,[string]$message) { if (-not $condition) { throw $message } }
function Fixture([string]$name) {
    $package = Join-Path $runRoot ($name + '\package')
    New-Item -ItemType Directory -Path (Join-Path $package 'app\Assets') -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $package 'app\Quill.exe'),'fixture executable: never launched')
    [IO.File]::WriteAllText((Join-Path $package 'app\Assets\copy.json'),'{"fixture":true}')
    foreach ($file in 'Install-Quill.ps1','Uninstall-Quill.ps1') { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $package }
    $lines = @(Get-ChildItem -LiteralPath (Join-Path $package 'app') -File -Recurse | ForEach-Object { '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash,$_.FullName.Substring($package.Length + 1).Replace('\','/') })
    [IO.File]::WriteAllLines((Join-Path $package 'SHA256SUMS'),$lines)
    return @{ package = $package; destination = (Join-Path $runRoot ($name + '\installed')); lines = $lines }
}
function Install($fixture) {
    & (Join-Path $fixture.package 'Install-Quill.ps1') -Destination $fixture.destination -NoLaunch -NoShortcut -NoRegistration | Out-Null
}
function Expect-Refusal([scriptblock]$action,[string]$message) {
    $refused = $false
    try { & $action } catch { $refused = $true }
    Assert-True $refused $message
}
$valid = Fixture 'valid'
Install $valid
Assert-True (Test-Path -LiteralPath (Join-Path $valid.destination 'Quill.exe')) 'Valid package did not install.'
$receipt = Get-Content -LiteralPath (Join-Path $valid.destination '.quill-install.json') -Raw | ConvertFrom-Json
Assert-True ($receipt.files.Count -eq 3 -and -not $receipt.shortcut -and -not $receipt.registration) 'Isolated receipt is incorrect.'
$passed++
[IO.File]::WriteAllText((Join-Path $valid.destination 'user-note.txt'),'preserve this unrelated file')
& (Join-Path $valid.destination 'Uninstall-Quill.ps1') | Out-Null
Assert-True (-not (Test-Path -LiteralPath (Join-Path $valid.destination 'Quill.exe'))) 'Uninstall left its executable behind.'
Assert-True ((Get-Content -LiteralPath (Join-Path $valid.destination 'user-note.txt') -Raw) -eq 'preserve this unrelated file') 'Uninstall deleted an unrelated file.'
$passed++
$tampered = Fixture 'tampered'
[IO.File]::WriteAllText((Join-Path $tampered.package 'app\Quill.exe'),'changed payload')
Expect-Refusal { Install $tampered } 'Tampered payload was accepted.'
Assert-True (-not (Test-Path -LiteralPath $tampered.destination)) 'Tampered install wrote a destination.'
$passed++
$missing = Fixture 'missing'
[IO.File]::WriteAllLines((Join-Path $missing.package 'SHA256SUMS'),@($missing.lines[0]))
Expect-Refusal { Install $missing } 'Incomplete checksum inventory was accepted.'
$passed++
$extra = Fixture 'extra'
[IO.File]::WriteAllText((Join-Path $extra.package 'app\unlisted.dll'),'unverified')
Expect-Refusal { Install $extra } 'Unlisted payload was accepted.'
$passed++
$duplicate = Fixture 'duplicate'
[IO.File]::WriteAllLines((Join-Path $duplicate.package 'SHA256SUMS'),@($duplicate.lines) + @($duplicate.lines[0]))
Expect-Refusal { Install $duplicate } 'Duplicate checksum entry was accepted.'
$passed++
$escape = Fixture 'escape'
[IO.File]::WriteAllLines((Join-Path $escape.package 'SHA256SUMS'),@(('0' * 64) + '  app/../../outside.txt'))
Expect-Refusal { Install $escape } 'Checksum traversal was accepted.'
$passed++
$uninstallEscape = Fixture 'uninstall-escape'
Install $uninstallEscape
$outside = Join-Path (Split-Path $uninstallEscape.destination -Parent) 'outside.txt'
[IO.File]::WriteAllText($outside,'outside fixture')
$receiptPath = Join-Path $uninstallEscape.destination '.quill-install.json'
$receipt = Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
$receipt.files += '..\outside.txt'
$receipt | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $receiptPath
Expect-Refusal { & (Join-Path $uninstallEscape.destination 'Uninstall-Quill.ps1') } 'Uninstaller accepted an escaping path.'
Assert-True ((Get-Content -LiteralPath $outside -Raw) -eq 'outside fixture') 'Uninstaller touched an outside file.'
Assert-True (Test-Path -LiteralPath (Join-Path $uninstallEscape.destination 'Quill.exe')) 'Uninstall validation was not completed before mutation.'
$passed++
$wrongRoot = Fixture 'uninstall-root'
Install $wrongRoot
$receiptPath = Join-Path $wrongRoot.destination '.quill-install.json'
$receipt = Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
$receipt.destination = $runRoot
$receipt | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $receiptPath
Expect-Refusal { & (Join-Path $wrongRoot.destination 'Uninstall-Quill.ps1') } 'Mismatched installation receipt was accepted.'
$passed++
$registeredCheck = 'Skipped: an existing Quill registration or startup entry was preserved.'
$registration = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\Quill.NativeV2'
$startupPath = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
if (-not (Test-Path -LiteralPath $registration) -and $null -eq (Get-ItemProperty -LiteralPath $startupPath -Name Quill -ErrorAction SilentlyContinue).Quill) {
    $registered = Fixture 'registered'
    & (Join-Path $registered.package 'Install-Quill.ps1') -Destination $registered.destination -NoLaunch -NoShortcut | Out-Null
    $entry = Get-ItemProperty -LiteralPath $registration
    Assert-True ($entry.InstallLocation -eq $registered.destination -and $entry.DisplayName -eq 'Quill' -and $entry.UninstallString.Contains('Uninstall-Quill.ps1')) 'Installed apps registration is incorrect.'
    & (Join-Path $registered.destination 'Uninstall-Quill.ps1') | Out-Null
    Assert-True (-not (Test-Path -LiteralPath $registration)) 'Uninstall left its own registration behind.'
    $registeredCheck = 'Passed: created and removed only this fixture registration.'
    $passed++
}
$upgrade = Fixture 'upgrade'
Install $upgrade
[IO.File]::WriteAllText((Join-Path $upgrade.destination 'user-note.txt'),'keep')
Remove-Item -LiteralPath (Join-Path $upgrade.package 'app\Assets\copy.json')
[IO.File]::WriteAllText((Join-Path $upgrade.package 'app\Assets\new.json'),'new payload')
$upgradeHashes = @(Get-ChildItem -LiteralPath (Join-Path $upgrade.package 'app') -File -Recurse | ForEach-Object { '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash,$_.FullName.Substring($upgrade.package.Length + 1).Replace('\','/') })
[IO.File]::WriteAllLines((Join-Path $upgrade.package 'SHA256SUMS'),$upgradeHashes)
Install $upgrade
Assert-True (-not (Test-Path -LiteralPath (Join-Path $upgrade.destination 'Assets\copy.json'))) 'Upgrade left an obsolete owned file.'
Assert-True (Test-Path -LiteralPath (Join-Path $upgrade.destination 'Assets\new.json')) 'Upgrade missed a new payload file.'
Assert-True (Test-Path -LiteralPath (Join-Path $upgrade.destination 'user-note.txt')) 'Upgrade removed an unrelated file.'
& (Join-Path $upgrade.destination 'Uninstall-Quill.ps1') | Out-Null
$passed++
$startupCheck = 'Skipped: isolated hosted runner required; existing startup entry preserved.'
if ($env:GITHUB_ACTIONS -eq 'true' -and $env:RUNNER_OS -eq 'Windows' -and -not (Test-Path -LiteralPath $registration) -and $null -eq (Get-ItemProperty -LiteralPath $startupPath -Name Quill -ErrorAction SilentlyContinue).Quill) {
    # These checks create only disposable runner entries, never a desktop user's
    # existing Quill registration or sign-in preference.
    try {
        if (-not (Test-Path -LiteralPath $startupPath)) { New-Item -Path $startupPath | Out-Null }
        $startup = Fixture 'startup-repair'
        New-ItemProperty -LiteralPath $startupPath -Name Quill -Value ('"' + (Join-Path $runRoot 'old-development\Quill.Windows.exe') + '" --background') -PropertyType String -Force | Out-Null
        & (Join-Path $startup.package 'Install-Quill.ps1') -Destination $startup.destination -NoLaunch -NoShortcut | Out-Null
        $expectedStartup = '"' + (Join-Path $startup.destination 'Quill.exe') + '" --background'
        Assert-True ((Get-ItemProperty -LiteralPath $startupPath -Name Quill).Quill -eq $expectedStartup) 'Upgrade did not repair the stale Quill startup executable.'
        & (Join-Path $startup.destination 'Uninstall-Quill.ps1') | Out-Null
        Assert-True ($null -eq (Get-ItemProperty -LiteralPath $startupPath -Name Quill -ErrorAction SilentlyContinue).Quill) 'Uninstall left its own startup entry behind.'
        $passed++

        $disabled = Fixture 'startup-disabled'
        & (Join-Path $disabled.package 'Install-Quill.ps1') -Destination $disabled.destination -NoLaunch -NoShortcut | Out-Null
        Assert-True ($null -eq (Get-ItemProperty -LiteralPath $startupPath -Name Quill -ErrorAction SilentlyContinue).Quill) 'Install enabled startup without an existing choice.'
        & (Join-Path $disabled.destination 'Uninstall-Quill.ps1') | Out-Null
        $passed++

        $unrelated = Fixture 'startup-unrelated'
        $unrelatedCommand = '"' + (Join-Path $runRoot 'another-app\Unrelated.exe') + '" --background'
        New-ItemProperty -LiteralPath $startupPath -Name Quill -Value $unrelatedCommand -PropertyType String -Force | Out-Null
        & (Join-Path $unrelated.package 'Install-Quill.ps1') -Destination $unrelated.destination -NoLaunch -NoShortcut | Out-Null
        Assert-True ((Get-ItemProperty -LiteralPath $startupPath -Name Quill).Quill -eq $unrelatedCommand) 'Install changed an unrelated startup command.'
        & (Join-Path $unrelated.destination 'Uninstall-Quill.ps1') | Out-Null
        Assert-True ((Get-ItemProperty -LiteralPath $startupPath -Name Quill).Quill -eq $unrelatedCommand) 'Uninstall removed an unrelated startup command.'
        $passed++
        $startupCheck = 'Passed: repaired stale Quill startup, preserved disabled/unrelated entries, removed only its own startup command.'
    } finally {
        Remove-ItemProperty -LiteralPath $startupPath -Name Quill -ErrorAction SilentlyContinue
        $entry = Get-ItemProperty -LiteralPath $registration -ErrorAction SilentlyContinue
        if ($entry.InstallLocation -and $entry.InstallLocation.StartsWith($runRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { Remove-Item -LiteralPath $registration }
    }
}
[IO.File]::WriteAllText((Join-Path $runRoot 'results.json'),(@{ passed = $passed; scope = 'Fresh synthetic files only; no launch or shortcuts; existing registration preserved.'; registration = $registeredCheck; startup = $startupCheck } | ConvertTo-Json))
Write-Output "$passed installer integrity and uninstall boundary checks passed. Evidence: $runRoot"
