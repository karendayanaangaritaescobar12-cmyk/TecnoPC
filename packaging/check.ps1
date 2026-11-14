param(
    [switch]$Browser,
    [string]$Maven = 'mvn',
    [string]$Npm = 'npm',
    [string]$MavenRepo = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    & $Npm run check
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las comprobaciones del frontend.' }

    $mavenArguments = @('--batch-mode', '--no-transfer-progress', '-f', 'java-backend/pom.xml', 'test')
    if ($MavenRepo) { $mavenArguments += "-Dmaven.repo.local=$MavenRepo" }
    & $Maven @mavenArguments
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas del backend.' }

    if ($Browser) {
        & $Npm run test:browser
        if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas del navegador.' }
    }
    Write-Host 'Comprobaciones completadas correctamente.'
} finally {
    Pop-Location
}
