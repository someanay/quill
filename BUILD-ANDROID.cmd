@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-android-release.ps1" %*
set "buildExit=%errorlevel%"
echo.
if not "%buildExit%"=="0" echo Build failed. Read the error above.
pause
exit /b %buildExit%
