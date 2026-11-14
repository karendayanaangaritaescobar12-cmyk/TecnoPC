param(
  [Parameter(Mandatory=$true)][ValidateSet('admin','ventas','tecnico')][string]$App,
  [ValidateSet('app-image','exe','msi')][string]$Type='app-image',
  [string]$JdkHome='', [string]$Maven='mvn', [string]$MavenRepo='',
  [string]$ApiUrl='http://localhost:8081', [string]$Destination=''
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$map=@{admin=@('java-desktop','com.tecnopc.desktop.Launcher','TecnoPC-Administrador');ventas=@('java-vendedor','com.tecnopc.vendedor.Launcher','TecnoPC-Ventas');tecnico=@('java-tecnico','com.tecnopc.tecnico.Launcher','TecnoPC-Tecnico')}
$dir,$main,$name=$map[$App]
if(!$JdkHome){
  $savedJdk=Join-Path $root '.tools\jdk21-path.txt'
  if(Test-Path -LiteralPath $savedJdk){$JdkHome=(Get-Content -LiteralPath $savedJdk -Raw).Trim()}
  elseif($env:JAVA_HOME){$JdkHome=$env:JAVA_HOME}
  else {throw 'Indica -JdkHome con un JDK completo que incluya jpackage y jlink.'}
}
$jpackage=Join-Path $JdkHome 'bin\jpackage.exe'
if(!(Test-Path -LiteralPath $jpackage)){throw 'Este JDK no contiene jpackage.exe.'}
$api=[Uri]$ApiUrl
if(!$api.IsAbsoluteUri -or $api.UserInfo -or $api.Query -or $api.Fragment -or
   !(($api.Scheme -eq 'https') -or ($api.Scheme -eq 'http' -and $api.Host -in @('localhost','127.0.0.1','[::1]','::1')))){
  throw 'La API debe usar HTTPS; HTTP solo se permite hacia este equipo.'
}
if(!$Destination){$Destination=Join-Path $root 'dist'}
$Destination=[IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
if($Type -eq 'app-image' -and (Test-Path -LiteralPath (Join-Path $Destination $name))){throw 'La carpeta de salida ya existe. Usa otro -Destination para conservar la version anterior.'}
$inputDir=Join-Path $root ($dir+'\target\package-input-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
$oldJavaHome=$env:JAVA_HOME
$env:JAVA_HOME=$JdkHome
Push-Location (Join-Path $root $dir)
try {
  $commonPom=Join-Path $root 'desktop-common\pom.xml'
  if(Test-Path -LiteralPath $commonPom){
    $commonArgs=@('-f',$commonPom,'-DskipTests','install')
    if($MavenRepo){$commonArgs+=('-Dmaven.repo.local='+$MavenRepo)}
    & $Maven @commonArgs
    if($LASTEXITCODE -ne 0){throw 'Fallo la compilacion de desktop-common'}
  }
  $mavenArgs=@('-DskipTests','package','dependency:copy-dependencies',('-DoutputDirectory='+(Join-Path $inputDir 'lib')))
  if($MavenRepo){$mavenArgs+=('-Dmaven.repo.local='+$MavenRepo)}
  & $Maven @mavenArgs
  if($LASTEXITCODE -ne 0){throw 'Fallo Maven'}
  $jar=Get-ChildItem -LiteralPath target -Filter '*.jar' -File | Where-Object {$_.Name -notlike '*original*'} | Select-Object -First 1
  if(!$jar){throw 'No se genero JAR'}
  Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $inputDir 'app.jar')
  $packageArgs=@('--type',$Type,'--name',$name,'--app-version','1.3.0','--vendor','TecnoPC',
    '--input',$inputDir,'--main-jar','app.jar','--main-class',$main,'--dest',$Destination,
    '--add-modules','java.base,java.desktop,java.net.http,java.logging,java.xml,java.sql,jdk.crypto.ec,jdk.unsupported',
    '--java-options','-Dfile.encoding=UTF-8','--java-options',('-Dtecnopc.api.url='+$ApiUrl))
  if($Type -in @('exe','msi')){$packageArgs+=@('--win-menu','--win-shortcut','--win-per-user-install')}
  & $jpackage @packageArgs
  if($LASTEXITCODE -ne 0){throw 'Fallo jpackage; los instaladores exe/msi requieren WiX compatible.'}
  Write-Output ('Salida: '+(Join-Path $Destination $name))
} finally {Pop-Location; $env:JAVA_HOME=$oldJavaHome}
