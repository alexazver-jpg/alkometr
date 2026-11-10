@echo off
chcp 65001 >nul
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0save_version.ps1" %*
if errorlevel 1 pause
endlocal
