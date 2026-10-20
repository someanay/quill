param([string]$Configuration = 'Release',[ValidateSet('win-x64','win-arm64')][string]$Runtime = 'win-x64',[switch]$RunNativeSmoke,[switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$nativeRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$quillRoot = [IO.Path]::GetFullPath((Join-Path $nativeRoot '..'))
$appProject = Join-Path $nativeRoot 'src\Quill.Windows\Quill.Windows.csproj'
$testProject = Join-Path $nativeRoot 'tests\Quill.Windows.Tests\Quill.Windows.Tests.csproj'
$distRoot = Join-Path $quillRoot 'dist\windows'
$packageRoot = Join-Path $distRoot ("packages\Quill-Windows-2.0.3-$Runtime-" + [Guid]::NewGuid().ToString('N'))
$publishRoot = Join-Path $packageRoot 'app'
New-Item -ItemType Directory -Path $publishRoot -Force | Out-Null
if (-not $SkipTests) {
    & dotnet test $testProject -c $Configuration --nologo --logger 'trx;LogFileName=windows-tests.trx'
    if ($LASTEXITCODE -ne 0) { throw 'Windows tests failed.' }
}
if ($RunNativeSmoke) {
    & dotnet build (Join-Path $nativeRoot 'tests\Quill.NativeSmoke\Quill.NativeSmoke.csproj') -c $Configuration --nologo
    if ($LASTEXITCODE -ne 0) { throw 'Native smoke build failed.' }
    & (Join-Path $nativeRoot "tests\Quill.NativeSmoke\bin\$Configuration\net10.0-windows\Quill.NativeSmoke.exe") (Join-Path $distRoot 'verification\native-smoke.json')
    if ($LASTEXITCODE -ne 0) { throw 'Native interactive tests failed.' }
}
& dotnet publish $appProject -c $Configuration -r $Runtime --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:DebugType=None -p:DebugSymbols=false -o $publishRoot --nologo
if ($LASTEXITCODE -ne 0) { throw 'Windows publish failed.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Install-Quill.ps1') -Destination $packageRoot
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Uninstall-Quill.ps1') -Destination $packageRoot
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Quill-Setup.cmd') -Destination $packageRoot
Copy-Item -LiteralPath (Join-Path $nativeRoot 'README.md') -Destination (Join-Path $packageRoot 'README.txt')
Copy-Item -LiteralPath (Join-Path $nativeRoot 'THIRD-PARTY-NOTICES.md') -Destination $packageRoot
Copy-Item -LiteralPath (Join-Path $nativeRoot 'VERIFICATION.md') -Destination $packageRoot
$hashLines = Get-ChildItem -LiteralPath $publishRoot -File -Recurse | Sort-Object FullName | ForEach-Object { '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(),[IO.Path]::GetRelativePath($packageRoot,$_.FullName).Replace('\','/') }
[IO.File]::WriteAllLines((Join-Path $packageRoot 'SHA256SUMS'),$hashLines,[Text.UTF8Encoding]::new($false))
$archive = Join-Path $distRoot "Quill-Windows-2.0.3-$Runtime.zip"
Compress-Archive -Path (Join-Path $packageRoot '*') -DestinationPath $archive -Force
$hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$archive.sha256","$hash  $([IO.Path]::GetFileName($archive))`n",[Text.UTF8Encoding]::new($false))
Write-Output "Ready: $archive"
Write-Output "Verified package folder: $packageRoot"
Write-Output "SHA-256: $hash"
