param([string]$Java='')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[Windows.Forms.Application]::EnableVisualStyles()
$projectRoot=Split-Path $PSScriptRoot -Parent
$logDirectory=Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$statusPath=Join-Path $projectRoot 'conexion-tidb-estado.json'
$jarPath=Join-Path $projectRoot 'java-backend\target\tecnopc-admin-api-1.0.0.jar'
$productsJar=Join-Path $projectRoot 'java-backend\target\tecnopc-api-productos.jar'
if(Test-Path -LiteralPath $productsJar){$jarPath=$productsJar}
$advisorJar=Join-Path $projectRoot 'java-backend\target\tecnopc-api-asesor.jar'
if(Test-Path -LiteralPath $advisorJar){$jarPath=$advisorJar}
$receiptsJar=Join-Path $projectRoot 'java-backend\target\tecnopc-api-recibos.jar'
if(Test-Path -LiteralPath $receiptsJar){$jarPath=$receiptsJar}
$ordersJar=Join-Path $projectRoot 'java-backend\target\tecnopc-api-pedidos.jar'
if(Test-Path -LiteralPath $ordersJar){$jarPath=$ordersJar}
$notificationsJar=Join-Path $projectRoot 'java-backend\target\tecnopc-api-avisos.jar'
if(Test-Path -LiteralPath $notificationsJar){$jarPath=$notificationsJar}
$securityJar=Join-Path $projectRoot 'java-backend\target\tecnopc-api-seguridad-1.4.0.jar'
if(Test-Path -LiteralPath $securityJar){$jarPath=$securityJar}
$resilienceJar=Join-Path $projectRoot 'java-backend\target\tecnopc-api-resiliencia-1.5.0.jar'
if(Test-Path -LiteralPath $resilienceJar){$jarPath=$resilienceJar}
$idempotencyJar=Join-Path $projectRoot 'java-backend\target\tecnopc-api-idempotencia-1.6.1.jar'
if(Test-Path -LiteralPath $idempotencyJar){$jarPath=$idempotencyJar}
if (!$Java) {
    $bundledJava=Join-Path $projectRoot '.tools\temurin21\jdk-21.0.13+9\bin\java.exe'
    $javaCommand=Get-Command java -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $bundledJava) { $Java=$bundledJava }
    elseif ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { $Java=Join-Path $env:JAVA_HOME 'bin\java.exe' }
    elseif ($javaCommand) { $Java=$javaCommand.Source }
    else { $Java='C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr\bin\java.exe' }
}
$savedErrorPreference=$ErrorActionPreference
try {
    $ErrorActionPreference='Continue'
    $javaVersionOutput=(& $Java -version 2>&1 | Out-String)
} finally { $ErrorActionPreference=$savedErrorPreference }
if ($LASTEXITCODE -ne 0 -or $javaVersionOutput -notmatch 'version "(\d+)' -or [int]$Matches[1] -lt 21) {
    throw 'TecnoPC requiere Java 21 o superior. Configura JAVA_HOME o usa el Java incluido en .tools\temurin21.'
}
function Save-ConnectionState([string]$State,[int]$ServerProcess=0) {
    @{estado=$State;proceso=$ServerProcess} | ConvertTo-Json | Set-Content -LiteralPath $statusPath -Encoding UTF8
}
Save-ConnectionState 'esperando_datos'
$form=New-Object Windows.Forms.Form
$form.Text='TecnoPC - Conexion segura a TiDB'
$form.Size=New-Object Drawing.Size(660,615)
$form.StartPosition='CenterScreen'
$form.FormBorderStyle='FixedDialog'
$form.MaximizeBox=$false
$form.TopMost=$true
$form.Add_Shown({$form.WindowState='Normal';$form.Activate();$form.BringToFront()})
$intro=New-Object Windows.Forms.Label
$intro.Location=New-Object Drawing.Point(20,15)
$intro.Size=New-Object Drawing.Size(605,48)
$intro.Text='Introduce aqui la clave NUEVA de TiDB. Las claves no se guardan en archivos ni se envian al chat. Las tablas se crearan automaticamente en la base elegida.'
$form.Controls.Add($intro)
$fields=@{}
$definitions=@(
    @('host','Servidor TiDB','gateway01.ap-northeast-1.prod.aws.tidbcloud.com',$false),
    @('port','Puerto','4000',$false),
    @('database','Base de datos','tecnopc',$false),
    @('user','Usuario TiDB completo','edVThP881JHoZXp.root',$false),
    @('password','Clave nueva de TiDB','',$true),
    @('admin','Administrador inicial','administrador',$false),
    @('adminPassword','Clave del administrador (8+ caracteres)','',$true),
    @('confirm','Repite la clave del administrador','',$true)
)
$row=0
foreach($definition in $definitions) {
    $label=New-Object Windows.Forms.Label
    $label.Location=New-Object Drawing.Point(20,(75+$row*43))
    $label.Size=New-Object Drawing.Size(265,28)
    $label.Text=$definition[1]
    $field=New-Object Windows.Forms.TextBox
    $field.Location=New-Object Drawing.Point(290,(72+$row*43))
    $field.Size=New-Object Drawing.Size(330,25)
    $field.Text=$definition[2]
    $field.UseSystemPasswordChar=[bool]$definition[3]
    $fields[$definition[0]]=$field
    $form.Controls.Add($label)
    $form.Controls.Add($field)
    $row++
}
$message=New-Object Windows.Forms.Label
$message.Location=New-Object Drawing.Point(20,425)
$message.Size=New-Object Drawing.Size(605,70)
$message.Text='Conecta por TLS y verifica el certificado del servidor. La clave del administrador es propia de TecnoPC; no tiene que ser la de TiDB.'
$form.Controls.Add($message)
$connect=New-Object Windows.Forms.Button
$connect.Text='Conectar e iniciar TecnoPC'
$connect.Location=New-Object Drawing.Point(20,505)
$connect.Size=New-Object Drawing.Size(235,36)
$form.Controls.Add($connect)
$link=New-Object Windows.Forms.LinkLabel
$link.Location=New-Object Drawing.Point(280,514)
$link.Size=New-Object Drawing.Size(325,26)
$link.Text='http://localhost:8081/'
$link.Visible=$false
$link.Add_LinkClicked({ Start-Process 'http://localhost:8081/' })
$form.Controls.Add($link)
$script:starting=$false
$form.Add_FormClosing({param($sender,$event) if($script:starting){$event.Cancel=$true} else {foreach($key in @('password','adminPassword','confirm')){$fields[$key].Clear()}}})
$connect.Add_Click({
    if($script:starting){return}
    $server=$fields['host'].Text.Trim()
    $database=$fields['database'].Text.Trim()
    $user=$fields['user'].Text.Trim()
    $admin=$fields['admin'].Text.Trim()
    $port=0
    if($server -notmatch '^[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?$' -or
       ![int]::TryParse($fields['port'].Text,[ref]$port) -or $port -lt 1 -or $port -gt 65535 -or
       $database -notmatch '^[A-Za-z0-9_]+$' -or $database -in @('sys','mysql','information_schema','performance_schema') -or
       !$user -or !$fields['password'].Text) {
        $message.Text='Revisa host, puerto, usuario y clave. Usa tecnopc o una base propia; no uses sys ni otra base interna.';return
    }
    if($admin -notmatch '^[a-zA-Z0-9._-]{3,60}$' -or $fields['adminPassword'].Text.Length -lt 8 -or
       [Text.Encoding]::UTF8.GetByteCount($fields['adminPassword'].Text) -gt 72 -or
       $fields['adminPassword'].Text -cne $fields['confirm'].Text) {
        $message.Text='Usa un administrador de 3-60 caracteres y una clave de 8+ caracteres (hasta 72 bytes). Las dos claves deben coincidir.';return
    }
    if(!(Test-Path -LiteralPath $Java) -or !(Test-Path -LiteralPath $jarPath)) {
        $message.Text='No se encontro Java o el servidor compilado. Revisa la instalacion.';Save-ConnectionState 'falta_java_o_jar';return
    }
    $script:starting=$true
    $connect.Enabled=$false
    $form.TopMost=$false
    $message.Text='Conectando e instalando las tablas... Puede tardar unos minutos. Espera en esta ventana.'
    [Windows.Forms.Application]::DoEvents()
    $serverProcess=$null
    try {
        $listener=Get-NetTCPConnection -State Listen -LocalPort 8081 -ErrorAction SilentlyContinue | Select-Object -First 1
        if($listener) {
            $running=Get-CimInstance Win32_Process -Filter ("ProcessId="+$listener.OwningProcess)
            $oldJar=Join-Path $projectRoot 'java-backend\target\tecnopc-admin-api-1.0.0.jar'
            if(!$running -or $running.Name -notin @('java.exe','javaw.exe') -or
                (!$running.CommandLine.Contains($oldJar) -and !$running.CommandLine.Contains($productsJar) -and !$running.CommandLine.Contains($advisorJar) -and !$running.CommandLine.Contains($receiptsJar) -and !$running.CommandLine.Contains($ordersJar) -and !$running.CommandLine.Contains($notificationsJar) -and !$running.CommandLine.Contains($securityJar) -and !$running.CommandLine.Contains($resilienceJar) -and !$running.CommandLine.Contains($jarPath))) {
                throw 'El puerto 8081 pertenece a otro programa. No se detuvo ese programa.'
            }
            Stop-Process -Id $listener.OwningProcess -ErrorAction Stop
            Wait-Process -Id $listener.OwningProcess -Timeout 15 -ErrorAction SilentlyContinue
        }
        $env:DB_HOST=$server;$env:DB_PORT=[string]$port;$env:DB_NAME=$database;$env:DB_USER=$user
        $env:DB_PASSWORD=$fields['password'].Text
        $env:ADMIN_USER=$admin;$env:ADMIN_PASSWORD=$fields['adminPassword'].Text
        $env:EMAIL_ENABLED='false';$env:WHATSAPP_ENABLED='false'
        $env:SPRING_PROFILES_ACTIVE='cloud'
        $resetPath=Join-Path $projectRoot '.local-secrets\account-reset.properties'
        $env:TECNOPC_ACCOUNT_RESET_FILE=if(Test-Path -LiteralPath $resetPath){$resetPath}else{''}
        Get-ChildItem -LiteralPath $projectRoot -Filter '*.log' -File | ForEach-Object {
            $destination=Join-Path $logDirectory $_.Name
            if(Test-Path -LiteralPath $destination){$destination=Join-Path $logDirectory ($_.BaseName+'-'+[Guid]::NewGuid().ToString('N')+$_.Extension)}
            Move-Item -LiteralPath $_.FullName -Destination $destination -ErrorAction Stop
        }
        $serverProcess=Start-Process -FilePath $Java -ArgumentList @('-jar',('"'+$jarPath+'"'),'--spring.profiles.active=cloud','--server.address=127.0.0.1','--server.port=8081','--debug=false','--logging.level.root=INFO','--logging.level.org.springframework=INFO') -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDirectory 'servidor-tidb.log') -RedirectStandardError (Join-Path $logDirectory 'servidor-tidb-error.log') -PassThru
        Save-ConnectionState 'iniciando' $serverProcess.Id
        # The Java process inherited its own environment. Clear secrets here immediately.
        $env:DB_PASSWORD=$null;$env:ADMIN_PASSWORD=$null;$env:TECNOPC_ACCOUNT_RESET_FILE=$null
        foreach($key in @('password','adminPassword','confirm')){$fields[$key].Clear()}
        $ready=$false
        $deadline=[DateTime]::UtcNow.AddMinutes(4)
        while([DateTime]::UtcNow -lt $deadline) {
            $serverProcess.Refresh()
            if($serverProcess.HasExited){throw 'El servidor no pudo iniciar. Revisa la clave, la base elegida, el permiso de tu IP y los registros locales.'}
            try {
                $health=Invoke-RestMethod -Uri 'http://127.0.0.1:8081/healthz' -TimeoutSec 2
                if($health.estado -eq 'ok'){$ready=$true;break}
            } catch {}
            [Windows.Forms.Application]::DoEvents()
            Start-Sleep -Milliseconds 250
        }
        if(!$ready){throw 'El inicio sigue pendiente. Consulta los registros locales antes de volver a iniciar otro servidor.'}
        Save-ConnectionState 'conectado' $serverProcess.Id
        $message.Text='Conexion correcta. La base y las tablas estan disponibles. Abre la tienda con el enlace. El servidor seguira funcionando al cerrar esta ventana. Correo y WhatsApp siguen desactivados.'
        $link.Visible=$true
        $connect.Text='Servidor conectado'
    } catch {
        $message.Text=$_.Exception.Message
        Save-ConnectionState 'error' $(if($serverProcess){$serverProcess.Id}else{0})
        $connect.Enabled=$true
    } finally {
        $env:DB_PASSWORD=$null;$env:ADMIN_PASSWORD=$null
        $script:starting=$false
    }
})
[void]$form.ShowDialog()
$form.Dispose()
