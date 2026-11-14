param([string]$Destination='dist\Seguridad-20261113',[string]$AppVersion='1.4.0')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jdk=Join-Path $root '.tools\temurin21\jdk-21.0.13+9'
$destinationPath=[IO.Path]::GetFullPath((Join-Path $root $Destination))
if(!$destinationPath.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'La salida debe estar dentro del proyecto'}
$apps=@(
    @('java-desktop','TecnoPC-Administrador','com.tecnopc.desktop.Launcher','dist\Interfaz-Mejorada\TecnoPC-Administrador'),
    @('java-vendedor','TecnoPC-Ventas','com.tecnopc.vendedor.Launcher','dist\Facturacion-Demo\TecnoPC-Ventas'),
    @('java-tecnico','TecnoPC-Tecnico','com.tecnopc.tecnico.Launcher','dist\Interfaz-Mejorada\TecnoPC-Tecnico')
)
foreach($app in $apps){
    $module,$name,$main,$previous=$app
    if(Test-Path -LiteralPath (Join-Path $destinationPath $name)){throw "La entrega ya existe: $name. Usa otra carpeta de salida."}
    $inputPath=Join-Path $root ($module+'\target\security-input-'+[Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $inputPath -Force | Out-Null
    $jar=Get-ChildItem -LiteralPath (Join-Path $root ($module+'\target')) -Filter '*.jar' -File | Select-Object -First 1
    if(!$jar){throw "Falta JAR: $module"}
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $inputPath 'app.jar')
    Copy-Item -LiteralPath (Join-Path $root ($module+'\target\security-libs')) -Destination (Join-Path $inputPath 'lib') -Recurse
    & (Join-Path $jdk 'bin\jpackage.exe') --type app-image --name $name --app-version $AppVersion --vendor TecnoPC --input $inputPath --main-jar app.jar --main-class $main --dest $destinationPath --runtime-image (Join-Path $root ($previous+'\runtime')) --icon (Join-Path $root 'frontend\favicon.ico') --java-options '-Dfile.encoding=UTF-8' --java-options '-Dtecnopc.api.url=http://localhost:8081'
    if($LASTEXITCODE -ne 0){throw "Fallo al empaquetar $name"}
    Write-Output ('Paquete actualizado: '+(Join-Path $destinationPath $name))
}
