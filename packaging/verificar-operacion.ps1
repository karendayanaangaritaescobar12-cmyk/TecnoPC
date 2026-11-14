param([string]$ServerUrl='http://127.0.0.1:8081',[ValidateRange(1,30)][int]$TimeoutSec=5)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$uri=$null
if(![Uri]::TryCreate($ServerUrl,[UriKind]::Absolute,[ref]$uri) -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or
    $uri.AbsolutePath -ne '/' -or !($uri.Scheme -eq 'https' -or ($uri.Scheme -eq 'http' -and $uri.Host -in @('localhost','127.0.0.1','[::1]','::1')))){
    throw 'Indica una URL HTTPS sin credenciales ni parametros; HTTP solo se permite hacia este equipo.'
}
$manifestPath=Join-Path $root 'documentacion\idempotencia\evidencias.json'
$manifest=Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$artifacts=@()
foreach($artifact in $manifest.artifacts){
    $path=[IO.Path]::GetFullPath((Join-Path $root $artifact.path))
    if(!$path.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'El manifiesto contiene una ruta fuera del proyecto'}
    $present=Test-Path -LiteralPath $path -PathType Leaf
    $valid=$present -and ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -eq $artifact.sha256)
    $artifacts+=@{archivo=$artifact.path;presente=$present;sha256Coincide=$valid}
}
function Probe([string]$Path){
    try{
        $result=Invoke-WebRequest -UseBasicParsing -Uri ($ServerUrl.TrimEnd('/')+$Path) -TimeoutSec $TimeoutSec
        return @{ruta=$Path;http=[int]$result.StatusCode;accesible=$true;plazoSegundos=$TimeoutSec}
    }catch{
        $status=$null
        if($_.Exception.Response){$status=[int]$_.Exception.Response.StatusCode}
        return @{ruta=$Path;http=$status;accesible=$false;plazoSegundos=$TimeoutSec;tipoError=$_.Exception.GetType().Name}
    }
}
$health=Probe '/healthz'
$liveness=Probe '/livez'
$activeJar=$null
$processVerified=$false
$stateFile=Join-Path $root 'conexion-tidb-estado.json'
if(Test-Path -LiteralPath $stateFile){
    $state=Get-Content -LiteralPath $stateFile -Raw|ConvertFrom-Json
    if($state.proceso){
        try{
            $running=Get-CimInstance Win32_Process -Filter ('ProcessId='+[int]$state.proceso) -ErrorAction Stop
            if($running -and $running.Name -in @('java.exe','javaw.exe')){
                $names=[regex]::Matches($running.CommandLine,'tecnopc[^"\s]+\.jar')
                if($names.Count -eq 1){$activeJar=$names[0].Value;$processVerified=$true}
            }
        }catch{ $processVerified=$false }
    }
}
$expectedJar='tecnopc-api-idempotencia-'+$manifest.release+'.jar'
$versionMatches=$processVerified -and $activeJar -eq $expectedJar
$pending=@()
if(@($artifacts|Where-Object {!$_.sha256Coincide}).Count){$pending+='Revisar artefactos ausentes o modificados.'}
if(!$versionMatches){$pending+='No se acredito que el proceso activo ejecute la entrega preparada.'}
if($health.http -ne 200){$pending+='No se acredito la salud del servicio dentro del plazo; revisar conexion y registros antes de atribuir la causa.'}
if($liveness.http -ne 200){$pending+='El proceso no expone la comprobacion de vida de las entregas nuevas.'}
$pending+='Comprobar fecha, retencion y restauracion de respaldo en TiDB Cloud.'
$pending+='Verificar migracion V3 y recorridos reales en un entorno separado.'
$pending+='Configurar y probar conciliacion de pagos en sandbox, sin cobros reales.'
$report=@{
    comprobadoUtc=[DateTime]::UtcNow.ToString('o');entregaPreparada=$manifest.release;
    artefactos=$artifacts;salud=$health;vida=$liveness;
    procesoLocal=@{comprobado=$processVerified;jar=$activeJar;coincideConEntrega=$versionMatches};
    respaldoProveedor='no_comprobado';restauracionRealComprobada=$false;
    migracionTiDBRealComprobada=$false;conciliacionSandboxComprobada=$false;
    preparadoParaCobrosReales=$false;pendientes=$pending
}
$logs=Join-Path $root 'logs';New-Item -ItemType Directory -Path $logs -Force|Out-Null
$destination=Join-Path $logs 'operacion-estado.json'
$report|ConvertTo-Json -Depth 10|Set-Content -LiteralPath $destination -Encoding UTF8
Write-Output ('Entrega preparada: '+$manifest.release)
Write-Output ('Artefactos correctos: '+@($artifacts|Where-Object {$_.sha256Coincide}).Count+'/'+$artifacts.Count)
Write-Output ('Salud HTTP: '+$health.http+'; vida HTTP: '+$liveness.http)
if($processVerified){Write-Output ('JAR activo: '+$activeJar)}else{Write-Output 'No se pudo comprobar el proceso local.'}
foreach($item in $pending){Write-Output ('PENDIENTE: '+$item)}
Write-Output ('Informe: '+$destination)
