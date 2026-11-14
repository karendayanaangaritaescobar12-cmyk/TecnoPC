param([switch]$Build, [switch]$Cloud, [string]$Java='java', [string]$Maven='mvn')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jar=Join-Path $root 'java-backend\target\tecnopc-admin-api-1.0.0.jar'
if ([string]::IsNullOrWhiteSpace($env:DB_USER) -or $env:DB_USER -eq 'root' -or [string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    throw 'Configura DB_USER y DB_PASSWORD con una cuenta MySQL dedicada; no uses root.'
}
if ($Cloud) {
    if ([string]::IsNullOrWhiteSpace($env:DB_HOST) -or $env:DB_HOST -notmatch '^[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?$') {
        throw 'Configura DB_HOST con el servidor de tu base en linea, sin protocolo ni parametros.'
    }
    if (!$env:DB_NAME) { $env:DB_NAME='tecnopc' }
    if ($env:DB_NAME -notmatch '^[A-Za-z0-9_]+$') { throw 'DB_NAME debe contener solo letras, numeros y guion bajo.' }
    if (!$env:DB_PORT) { $env:DB_PORT='4000' }
    $cloudPort=0
    if (![int]::TryParse($env:DB_PORT,[ref]$cloudPort) -or $cloudPort -lt 1 -or $cloudPort -gt 65535) {
        throw 'DB_PORT debe ser un puerto entre 1 y 65535.'
    }
}
if ($Build -or !(Test-Path -LiteralPath $jar)) {
    & $Maven -f (Join-Path $root 'java-backend\pom.xml') package
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el servidor Java.' }
}
Write-Host 'Tienda: http://localhost:8081/ (el servidor debe permanecer en ejecucion).'
if(!$Build){
    foreach($candidate in @('tecnopc-api-productos.jar','tecnopc-api-asesor.jar','tecnopc-api-recibos.jar','tecnopc-api-pedidos.jar','tecnopc-api-avisos.jar','tecnopc-api-seguridad-1.4.0.jar','tecnopc-api-resiliencia-1.5.0.jar','tecnopc-api-idempotencia-1.6.1.jar')){
        $candidatePath=Join-Path $root ('java-backend\target\'+$candidate)
        if(Test-Path -LiteralPath $candidatePath){$jar=$candidatePath}
    }
}
$resetPath=Join-Path $root '.local-secrets\account-reset.properties'
if(Test-Path -LiteralPath $resetPath){$env:TECNOPC_ACCOUNT_RESET_FILE=$resetPath}
if ($Cloud) { & $Java -jar $jar '--spring.profiles.active=cloud' }
else { & $Java -jar $jar }
if ($LASTEXITCODE -ne 0) { throw 'El servidor se detuvo con error; revisa MySQL y las variables de entorno.' }
