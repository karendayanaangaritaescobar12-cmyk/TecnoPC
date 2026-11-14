param([string]$Output='logs\seguridad-dependencias-osv.json')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$components=@{}
$moduleNames=@('java-backend','java-desktop','java-vendedor','java-tecnico')
foreach($module in $moduleNames){
    $bomPath=Join-Path $root ($module+'\target\bom.json')
    if(!(Test-Path -LiteralPath $bomPath)){throw "Falta SBOM: $bomPath"}
    $bom=Get-Content -LiteralPath $bomPath -Raw | ConvertFrom-Json
    foreach($component in $bom.components){
        if($component.purl -notlike 'pkg:maven/*' -or $component.group -eq 'com.tecnopc'){continue}
        $name=$component.group+':'+$component.name
        $key=$name+'@'+$component.version
        if(!$components.ContainsKey($key)){
            $components[$key]=@{name=$name;version=$component.version;purl=$component.purl;modules=@($module)}
        }elseif($components[$key].modules -notcontains $module){$components[$key].modules+=@($module)}
    }
}
$packages=@($components.Values | Sort-Object name,version)
$queries=@($packages | ForEach-Object {@{package=@{ecosystem='Maven';name=$_.name};version=$_.version}})
$response=Invoke-RestMethod -Method Post -Uri 'https://api.osv.dev/v1/querybatch' -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes((@{queries=$queries}|ConvertTo-Json -Depth 10))) -TimeoutSec 60
if(@($response.results).Count -ne $packages.Count){throw 'Respuesta OSV incompleta'}
$findings=@()
for($i=0;$i -lt $packages.Count;$i++){
    $result=$response.results[$i]
    if($result.next_page_token){throw 'OSV requiere paginacion; informe incompleto'}
    foreach($vulnerability in $result.vulns){
        $findings+=@{package=$packages[$i].name;version=$packages[$i].version;id=$vulnerability.id;modified=$vulnerability.modified;modules=$packages[$i].modules}
    }
}
$details=@{}
foreach($id in @($findings.id | Sort-Object -Unique)){
    $details[$id]=Invoke-RestMethod -Uri ('https://api.osv.dev/v1/vulns/'+[Uri]::EscapeDataString($id)) -TimeoutSec 60
}
$report=@{
    checked_at_utc=[DateTime]::UtcNow.ToString('o');source='https://api.osv.dev/v1/querybatch';
    status='complete';scope='Maven runtime/compile dependencies from four CycloneDX SBOMs; excludes OS images, bundled JDK and native binary analysis';
    packages=$packages;findings=$findings;advisories=$details
}
$destination=Join-Path $root $Output
$report|ConvertTo-Json -Depth 50|Set-Content -LiteralPath $destination -Encoding UTF8
Write-Output ('Paquetes consultados: '+$packages.Count)
Write-Output ('Coincidencias OSV: '+$findings.Count)
foreach($finding in $findings){Write-Output ($finding.package+' '+$finding.version+' '+$finding.id)}
