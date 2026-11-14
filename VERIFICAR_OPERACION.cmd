@echo off
setlocal
title TecnoPC - Verificacion de operacion
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0packaging\verificar-operacion.ps1"
echo.
pause
endlocal
