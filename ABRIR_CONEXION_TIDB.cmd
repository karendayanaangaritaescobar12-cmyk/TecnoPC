@echo off
setlocal
title TecnoPC - Conexion a TiDB
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0packaging\conectar-tidb.ps1"
if errorlevel 1 (
    echo.
    echo No se pudo abrir la conexion. Comparte el error mostrado, sin contrasenas.
    pause
)
endlocal
