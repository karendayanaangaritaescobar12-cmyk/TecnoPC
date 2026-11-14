$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$packages=@(
    @('dist\Interfaz-Mejorada\TecnoPC-Administrador','java-desktop\target\tecnopc-desktop-1.0.0.jar'),
    @('dist\Facturacion-Demo\TecnoPC-Ventas','java-vendedor\target\tecnopc-vendedor-1.0.0.jar'),
    @('dist\Interfaz-Mejorada\TecnoPC-Tecnico','java-tecnico\target\tecnopc-tecnico-1.0.0.jar')
)
foreach($package in $packages){
    $app=Join-Path $root ($package[0]+'\app')
    Copy-Item -LiteralPath (Join-Path $root $package[1]) -Destination (Join-Path $app 'app.jar')
    Copy-Item -LiteralPath (Join-Path $root 'desktop-common\target\desktop-common-1.0.0.jar') -Destination (Join-Path $app 'lib\desktop-common-sessionfix.jar')
    foreach($library in Get-ChildItem -LiteralPath (Join-Path $app 'lib') -Filter 'desktop-common*.jar'){
        Copy-Item -LiteralPath (Join-Path $root 'desktop-common\target\desktop-common-1.0.0.jar') -Destination $library.FullName
    }
    Copy-Item -LiteralPath (Join-Path $root 'frontend\favicon.ico') -Destination (Join-Path $app 'tecnopc.ico')
    Write-Output ('Actualizado: '+$package[0])
}
# The local launcher selects this complete backend build when it exists.
Copy-Item -LiteralPath (Join-Path $root 'java-backend\target\tecnopc-admin-api-1.0.0.jar') -Destination (Join-Path $root 'java-backend\target\tecnopc-api-avisos.jar')
