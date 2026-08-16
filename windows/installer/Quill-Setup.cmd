@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-Quill.ps1"
if errorlevel 1 (
  echo Quill installation failed.
  pause
)
