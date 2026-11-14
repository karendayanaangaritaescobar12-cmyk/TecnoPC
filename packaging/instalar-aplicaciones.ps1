param([string]$PackageRoot='')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
if(!$PackageRoot -and (Test-Path -LiteralPath (Join-Path $root 'dist\Idempotencia-20261113-1.6.1'))){$PackageRoot='dist\Idempotencia-20261113-1.6.1'}
if(!$PackageRoot -and (Test-Path -LiteralPath (Join-Path $root 'dist\Seguridad-20261113'))){$PackageRoot='dist\Seguridad-20261113'}
$apps=@(
    @{Name='TecnoPC-Administrador';Source='dist\Interfaz-Mejorada\TecnoPC-Administrador'},
    @{Name='TecnoPC-Ventas';Source='dist\Facturacion-Demo\TecnoPC-Ventas'},
    @{Name='TecnoPC-Tecnico';Source='dist\Interfaz-Mejorada\TecnoPC-Tecnico'}
)
if($PackageRoot){
    foreach($app in $apps){$app.Source=Join-Path $PackageRoot $app.Name}
}
# Validate every package before writing to the user installation directory.
foreach($app in $apps){
    $source=Join-Path $root $app.Source
    foreach($relative in @(($app.Name+'.exe'),'app\app.jar','runtime\bin\server\jvm.dll')){
        if(!(Test-Path -LiteralPath (Join-Path $source $relative))){throw "Paquete incompleto: $source ($relative)"}
    }
    foreach($line in Get-Content -LiteralPath (Join-Path $source ('app\'+$app.Name+'.cfg'))){
        if($line.StartsWith('app.classpath=$APPDIR\')){
            $dependency=$line.Substring('app.classpath=$APPDIR\'.Length)
            if(!(Test-Path -LiteralPath (Join-Path $source ('app\'+$dependency)))){throw "Falta dependencia: $dependency"}
        }
    }
}
$installRoot=Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) ('Programs\TecnoPC\'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
$menu=Join-Path ([Environment]::GetFolderPath('Programs')) 'TecnoPC'
$desktop=[Environment]::GetFolderPath('Desktop')
New-Item -ItemType Directory -Path $installRoot,$menu -Force | Out-Null
$shell=New-Object -ComObject WScript.Shell
foreach($app in $apps){
    $destination=Join-Path $installRoot $app.Name
    Copy-Item -LiteralPath (Join-Path $root $app.Source) -Destination $destination -Recurse
    $icon=Join-Path $destination 'app\tecnopc.ico'
    Copy-Item -LiteralPath (Join-Path $root 'frontend\favicon.ico') -Destination $icon
    $exe=Join-Path $destination ($app.Name+'.exe')
    if((Get-FileHash -LiteralPath $exe).Hash -ne (Get-FileHash -LiteralPath (Join-Path (Join-Path $root $app.Source) ($app.Name+'.exe'))).Hash){throw 'Fallo al verificar la copia'}
    foreach($folder in @($desktop,$menu)){
        $linkPath=Join-Path $folder ($app.Name+'.lnk')
        $link=$shell.CreateShortcut($linkPath)
        $link.TargetPath=$exe
        $link.WorkingDirectory=$destination
        $link.IconLocation=$icon+',0'
        $link.Save()
        if($shell.CreateShortcut($linkPath).TargetPath -ne $exe){throw "No se pudo verificar $linkPath"}
    }
    Write-Output ('Instalada: '+$exe)
}
