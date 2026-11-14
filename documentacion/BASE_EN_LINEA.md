# Base en linea gratuita: TiDB Cloud Starter

El backend Java puede conectarse por Internet a TiDB Cloud Starter en modo
compatible con MySQL. El servidor MySQL no se instala en este equipo. La web
y Spring Boot siguen ejecutandose aqui: mover la base no publica la tienda.

## Crear la cuenta y la instancia

1. Abrir https://tidbcloud.com/ y crear una cuenta propia.
2. Crear una instancia Starter, MySQL-compatible, con el plan gratuito.
   No habilitar facturacion por consumo ni agregar tarjeta para esta prueba.
3. Elegir una region cercana. Abrir Connect > Public y guardar host, puerto y
   nombre de usuario completo, incluido su prefijo. Crear la clave desde la consola.
4. Autorizar solo la IP publica de tu conexion en las reglas del proveedor.
   Si tu IP cambia tendras que actualizarla. No abrir acceso a todas las IP.
5. Elegir una base vacia (por ejemplo test si ya existe en tu instancia), o crear tecnopc.
   No importar tablas manualmente: Flyway las creara al primer arranque.

El servidor crea las tablas y aplica las migraciones automaticamente en la base indicada.
Los permisos del usuario de configuracion deben permitir crear esa base.
Para ejecutar la aplicacion, usar un usuario dedicado con permisos sobre las
tablas de tecnopc; no necesita privilegios administrativos globales.

## Conectar TecnoPC

Desde PowerShell, en la raiz del proyecto:

```powershell
$env:DB_HOST = '<host-de-la-consola>'
$env:DB_PORT = '4000' # Usar el puerto real que muestre el proveedor.
$env:DB_NAME = 'tecnopc'
$env:DB_USER = '<usuario-completo-con-prefijo>'
$secureDbPassword = Read-Host 'Clave de la base' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secureDbPassword).Password
$env:ADMIN_USER = 'administrador'
$secureAdminPassword = Read-Host 'Clave inicial del administrador (12+ caracteres)' -AsSecureString
$env:ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $secureAdminPassword).Password
./packaging/start-server.ps1 -Cloud
```

El script usa el JAR incluido; despues de cambiar fuentes, compilar con
`./packaging/start-server.ps1 -Cloud -Build`. Si java o mvn no estan en PATH,
usar -Java y -Maven con sus rutas completas. JAVA_HOME debe apuntar al JDK
para compilar. Las claves solo quedan en el entorno de esta sesion: no se
escriben en fuentes ni en el historial como texto de comando.

El perfil cloud exige TLS y valida el certificado y el nombre del servidor
(VERIFY_IDENTITY). Usa DB_HOST/DB_PORT/DB_NAME, no DB_URL. No desactivar la
verificacion de certificados si falla: revisar el host, IP autorizada y JDK.
El navegador y los escritorios hablan con Java; las credenciales de la base
no deben incluirse en JavaScript ni en los escritorios.

## Limites del plan y validacion

Segun la documentacion consultada el 12 de noviembre de 2026, las primeras
cinco instancias elegibles incluyen 5 GiB de almacenamiento por filas,
5 GiB columnar y 50 millones de unidades de solicitud al mes por instancia.
No hace falta tarjeta para comenzar. Al agotar la cuota se restringe el
servicio; no equivale a capacidad ilimitada ni a disponibilidad garantizada.

TiDB usa el protocolo MySQL, pero no es el mismo motor. Antes de usar datos
reales hay que comprobar el esquema, claves foraneas, ventas concurrentes,
reparaciones, pagos, conectividad y respaldos en la instancia elegida.
No se ha creado ninguna cuenta ni conectado una base remota en esta ronda.
Guarda respaldos independientes; revisa retencion y condiciones del plan.

Fuentes oficiales:
- https://docs.pingcap.com/tidbcloud/select-cluster-tier/?compatibility=mysql&plan=starter
- https://docs.pingcap.com/tidbcloud/connect-via-standard-connection-serverless/
- https://docs.pingcap.com/tidbcloud/foreign-key/
