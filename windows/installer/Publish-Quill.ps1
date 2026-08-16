param(
    [ValidateSet('win-x64', 'win-arm64')]
    [string]$Runtime = 'win-x64'
)

$ErrorActionPreference = 'Stop'
$windowsRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $windowsRoot
$project = Join-Path $windowsRoot 'src\Quill.Windows\Quill.Windows.csproj'
$artifactRoot = Join-Path $windowsRoot 'artifacts'
$stageRoot = Join-Path $artifactRoot "stage-$Runtime"
$publishRoot = Join-Path $stageRoot 'app'
$payload = Join-Path $stageRoot 'payload.zip'
$installer = Join-Path $artifactRoot "Quill-$Runtime-Setup.exe"
$sed = Join-Path $stageRoot 'Quill-Setup.sed'

if (Test-Path $stageRoot) { Remove-Item -LiteralPath $stageRoot -Recurse -Force }
New-Item -ItemType Directory -Force -Path $publishRoot | Out-Null
New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
if (Test-Path $installer) { Remove-Item -LiteralPath $installer -Force }

dotnet publish $project --configuration Release --runtime $Runtime --self-contained true --output $publishRoot
Compress-Archive -Path (Join-Path $publishRoot '*') -DestinationPath $payload -CompressionLevel Optimal
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Install-Quill.ps1') -Destination $stageRoot -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Uninstall-Quill.ps1') -Destination $stageRoot -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Quill-Setup.cmd') -Destination $stageRoot -Force

$stage = $stageRoot
$target = $installer
$sedContent = @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=1
HideExtractAnimation=1
UseLongFileName=1
InsideCompressed=0
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=I
InstallPrompt=%InstallPrompt%
DisplayLicense=
FinishMessage=%FinishMessage%
TargetName=%TargetName%
FriendlyName=%FriendlyName%
AppLaunched=%AppLaunched%
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
SourceFiles=SourceFiles
[Strings]
InstallPrompt=Install Quill for Windows?
FinishMessage=Quill is ready from the Start Menu.
FriendlyName=Quill for Windows
TargetName=$target
AppLaunched=Quill-Setup.cmd
FILE0=Install-Quill.ps1
FILE1=Uninstall-Quill.ps1
FILE2=Quill-Setup.cmd
FILE3=payload.zip
[SourceFiles]
SourceFiles0=$stage\
[SourceFiles0]
%FILE0%=
%FILE1%=
%FILE2%=
%FILE3%=
"@
Set-Content -LiteralPath $sed -Value $sedContent -Encoding ASCII

& "$env:WINDIR\System32\iexpress.exe" /N /Q $sed
for ($attempt = 0; $attempt -lt 180 -and -not (Test-Path $installer); $attempt++) {
    Start-Sleep -Seconds 1
}
if (-not (Test-Path $installer)) { throw "IExpress did not create $installer" }
Start-Sleep -Seconds 5
Get-ChildItem -LiteralPath $artifactRoot -Filter '~Quill-*' -File -ErrorAction SilentlyContinue |
    Remove-Item -Force -ErrorAction SilentlyContinue

Write-Host "Created installer: $installer"
Write-Host "Published app: $publishRoot"
