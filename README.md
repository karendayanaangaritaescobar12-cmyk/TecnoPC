# TecnoPC

Tienda de computadores y gestión del negocio: catálogo, cotizaciones,
solicitudes de servicio, ventas, reparaciones y seguimiento de equipos.
El backend sirve tanto la web pública como las API de las aplicaciones internas.

## Tecnología y carpetas

- Java 21, Spring Boot 3.5, Spring Security y Maven: `java-backend/`.
- MySQL 8 o TiDB; migraciones Flyway: `java-backend/src/main/resources/db/migration/`.
- HTML, CSS y JavaScript sin framework: `frontend/`.
- Aplicaciones JavaFX: `java-desktop/` (administración), `java-vendedor/`
  y `java-tecnico/`; componentes compartidos en `desktop-common/`.
- Construcción y utilidades: `packaging/`; guías en `documentacion/`.
- Despliegue: `Dockerfile` y `render.yaml`.

## Requisitos

Para desarrollo: JDK 21, Maven 3.9 y una base MySQL 8 o TiDB accesible.
`java -version` debe mostrar Java 21 y `mvn -version` debe utilizar ese JDK.
Docker es opcional si se compila con Maven. No hace falta instalar Node.js
ni ejecutar un servidor separado para el frontend.

## Instalación local

```powershell
git clone https://github.com/karendayanaangaritaescobar12-cmyk/TecnoPC.git
cd TecnoPC
```

Si ya tienes una copia, entra a su carpeta; no necesitas volver a clonarla.

1. Crea una base vacía llamada `tecnopc` en MySQL y una cuenta dedicada con
   permisos para las operaciones del negocio y para crear/modificar el esquema.
   Evita utilizar la cuenta `root` para la aplicación.
2. Configura las variables siguientes en PowerShell, reemplazando los ejemplos:

```powershell
$env:DB_USER = 'tecnopc_app'
$env:DB_PASSWORD = '<clave-de-la-base>'
$env:ADMIN_USER = 'administrador'
$env:ADMIN_PASSWORD = '<clave-inicial-larga-y-unica>'
mvn -f java-backend/pom.xml package
java -jar java-backend/target/tecnopc-admin-api-1.0.0.jar
```

3. Abre **http://localhost:8081/**. Mantén Java en ejecución.
   Para detenerlo, pulsa `Ctrl+C` en su terminal.

El frontend se incluye dentro del JAR. Después de modificar HTML, CSS o
JavaScript, vuelve a compilar y reinicia el JAR para servir la nueva versión.
Las API internas requieren HTTP Basic y el rol correspondiente; la tienda
no es una pantalla de administración.

En Windows también puedes usar `./packaging/start-server.ps1 -Build` con
Java y Maven en PATH, tras configurar las mismas variables. En Linux/macOS,
usa `export NOMBRE='valor'` en lugar de `$env:NOMBRE = 'valor'`.

## Variables de entorno

No guardes valores reales en Git, HTML o JavaScript. Spring Boot no carga
automáticamente un archivo `.env`: configura el entorno del proceso o
las variables del servicio en Render.

| Variable | Uso y valor predeterminado |
| --- | --- |
| `DB_USER`, `DB_PASSWORD` | Credenciales de la base; obligatorias |
| `DB_URL` | Sin perfil cloud: URL JDBC; por defecto MySQL local, puerto 3306, base `tecnopc` |
| `DB_HOST` | Servidor de la base, obligatorio con perfil `cloud` |
| `DB_PORT`, `DB_NAME` | En `cloud`: `4000` y `tecnopc`, respectivamente |
| `ADMIN_USER`, `ADMIN_PASSWORD` | Crear el primer administrador; necesarias si todavía no existe uno |
| `SPRING_PROFILES_ACTIVE` | Sin valor para local; `cloud` para TiDB; `cloud,hosted` para Render |
| `SERVER_ADDRESS` | Sin perfil hosted: `127.0.0.1` por defecto |
| `PORT` | En hosted: puerto HTTP del servicio; por defecto `10000` |
| `RENDER_INTERNAL_PROXIES_REGEX`, `RENDER_TRUSTED_PROXIES_REGEX` | Solo con el perfil adicional `hosted-proxy`; proxies verificados, expresados como regex Java |

El nombre inicial de administrador acepta de 3 a 60 letras, números, puntos,
guiones o guiones bajos. La contraseña debe tener al menos ocho caracteres
y no superar 72 bytes UTF-8; utiliza una clave larga y única. Las variables
de creación no cambian la contraseña de una cuenta que ya existe y pueden
retirarse después del primer arranque correcto.

Para TiDB desde tu PC, además de las credenciales:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'cloud'
$env:DB_HOST = '<servidor-TiDB>'
$env:DB_PORT = '4000'
$env:DB_NAME = 'tecnopc'
java -jar java-backend/target/tecnopc-admin-api-1.0.0.jar
```

El perfil cloud exige verificar la identidad TLS del servidor de base de datos.
`hosted` hace que Java escuche en `0.0.0.0` y utilice `PORT`.

## Base de datos y Flyway

Flyway aplica V1, V2 y V3 al arrancar, antes de servir la aplicación.
No importes manualmente los scripts históricos v7 a v10: están incluidos
en V1. No edites migraciones ya aplicadas; los cambios futuros deben ir en
una migración nueva.

Si una base ya tiene tablas pero no historial Flyway, el arranque se detiene.
Antes de adoptarla, respalda y revisa su esquema. No habilites `clean` ni
`baseline-on-migrate` para saltar el error. Las pruebas usan bases H2
temporales y no sustituyen comprobar una copia de MySQL/TiDB.

## Despliegue Docker y Render

El Dockerfile compila el backend y empaqueta el frontend. Ejecuta el resultado
con Java 21 y un usuario sin privilegios.

```powershell
docker build -t tecnopc .
docker run --rm -p 10000:10000 -e SPRING_PROFILES_ACTIVE=cloud,hosted -e DB_HOST -e DB_PORT -e DB_NAME -e DB_USER -e DB_PASSWORD -e ADMIN_USER -e ADMIN_PASSWORD tecnopc
```

Ese comando toma las credenciales de las variables de tu terminal. Para
probar Docker con TiDB, configúralas antes y abre http://localhost:10000/.
La base debe aceptar la conexión desde el contenedor.

Para Render:

1. Conecta este repositorio y crea un servicio mediante Blueprint usando
   `render.yaml`, o crea un Web Service con runtime Docker y el Dockerfile
   de la raíz. No se necesita un comando de arranque adicional.
2. Configura `SPRING_PROFILES_ACTIVE=cloud,hosted`, `DB_HOST`, `DB_PORT`,
   `DB_NAME`, `DB_USER`, `DB_PASSWORD` y las variables del primer administrador.
   Mantén correo y WhatsApp deshabilitados hasta configurar sus cuentas.
3. Usa `/livez` como comprobación de salud. `/healthz` también comprueba
   la conexión a la base. Verifica los logs de migraciones y arranque.
4. Abre la URL HTTPS del servicio y comprueba catálogo, una solicitud de
   prueba y acceso desde los escritorios con el rol correspondiente.

Render puede suspender el servicio gratuito después de 15 minutos sin
tráfico; la siguiente visita puede tardar mientras vuelve a arrancar.
[Limitaciones del plan gratuito](https://render.com/docs/free).

**Pendiente de verificar en el despliegue real:** la cadena de proxies de
Render. El perfil `hosted-proxy` está preparado y probado localmente, pero
no está activado en `render.yaml`. Para activarlo, confirmar los proxies,
configurar ambas variables regex y usar `cloud,hosted,hosted-proxy`.
Sin ese paso, los límites por IP pueden agrupar clientes detrás del proxy
y Java puede no reconocer HTTPS para emitir HSTS. Consulta
[proxies autorizados](documentacion/PROXIES_HOSTED.md) y
[HSTS](documentacion/HSTS.md). No configurar confianza para cualquier IP.

## Pagos, correo y WhatsApp opcionales

| Variables | Función |
| --- | --- |
| `MP_ACCESS_TOKEN` | Token de Mercado Pago del comercio |
| `MP_NOTIFICATION_URL` | URL HTTPS pública terminada en `/api/public/pagos/webhook` |
| `MP_WEBHOOK_SECRET` | Secreto para validar firmas de notificaciones de pago |
| `EMAIL_ENABLED` | `false` por defecto; activar tras configurar correo |
| `RESEND_API_KEY`, `EMAIL_FROM` | Credenciales y remitente del correo |
| `WHATSAPP_ENABLED` | `false` por defecto |
| `WHATSAPP_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID` | Token y número del proveedor |
| `WHATSAPP_API_VERSION` | Por defecto `v27.0`; comprobar la versión admitida por tu cuenta |
| `WHATSAPP_TEMPLATE`, `WHATSAPP_LANGUAGE` | Por defecto `tecnopc_actualizacion` y `es` |

Prueba los pagos con credenciales de prueba antes de cobrar. Si no se configura
la pasarela, checkout responde 503; la tienda y las solicitudes siguen
disponibles. El backend valida la notificación antes de registrar la venta
y descontar stock. No se reservan existencias durante checkout.
Consulta [despliegue y avisos](documentacion/guias/DESPLIEGUE_Y_AVISOS.md).

## Pruebas

Desde la raíz, con Java 21 y Maven:

```powershell
mvn -f java-backend/pom.xml test
```

No requiere credenciales reales de producción. Para verificar los cambios
de estilos, caché, seguridad y arranque:

```powershell
mvn -f java-backend/pom.xml "-Dtest=SecurityConfigTest,StartupIntegrationTest,PublicApiTest,ProductAdminTest" test
```

Se espera `BUILD SUCCESS`, sin fallos ni errores.

### Pruebas automáticas en GitHub

[El workflow del backend](.github/workflows/backend-tests.yml) ejecuta toda
la batería con Java 21 en cada push y pull request. También se puede iniciar
desde la pestaña **Actions** del repositorio con **Run workflow** cuando el
archivo esté publicado en la rama predeterminada.

Utiliza una caché de dependencias Maven y no necesita claves de Render,
TiDB ni pagos. Si una prueba falla, la ejecución aparece en rojo y muestra
el error en el paso de pruebas. Este workflow no despliega la aplicación ni
impide por sí solo que Render despliegue un commit; esa coordinación requiere
configurar las reglas del repositorio y del despliegue por separado.

## Seguridad y caché de la web

El CSP permite estilos y scripts del mismo origen; los mensajes vacíos usan
clases CSS en lugar de estilos en línea. HTML y respuestas privadas conservan
su política de no almacenamiento.

`/css/**`, `/js/**` e `/images/**` permiten guardar archivos con
`Cache-Control: max-age=0, must-revalidate, public`. El navegador debe
revalidarlos antes de reutilizarlos; si no cambian, recibe 304 sin descargar
su cuerpo. Esta política evita servir durante horas archivos antiguos sin
versionar. No se aplica a las API, que conservan sus políticas específicas.

Los límites de solicitudes son por proceso y ventana de un minuto:
catálogo 300, seguimiento/recibo 15, pagos/solicitudes 10 y webhook 600.
Las ventanas ocupan como máximo 8192 claves. Consulta
[límites y saturación](documentacion/LIMITE_SOLICITUDES_PUBLICAS.md).

## Aplicaciones de escritorio

Para construir el administrador en Windows, con un JDK 21 completo:

```powershell
./packaging/build.ps1 -App admin -Type app-image -JdkHome $env:JAVA_HOME
```

Usa `-App ventas` o `-App tecnico` para los otros escritorios. Para un backend
remoto agrega `-ApiUrl https://tu-servicio.onrender.com`; el valor local por
defecto es `http://localhost:8081`. La salida se genera en `dist/`.
Guía: [ejecutables Windows](documentacion/guias/EJECUTABLES_WINDOWS.md).

## Problemas frecuentes

- **Java o Maven no se encuentran:** configura PATH y JAVA_HOME para JDK 21.
- **No conecta a la base:** comprueba servidor, puerto, credenciales, permisos
  y acceso de red. En cloud también debe ser válido el certificado TLS.
- **Primer inicio falla:** verifica las variables del administrador y que la
  base esté vacía o ya tenga un historial Flyway compatible.
- **429:** espera la ventana indicada por `Retry-After`; si afecta a clientes
  distintos, revisa primero la configuración del proxy.
- **Cambios web no aparecen:** recompila y reinicia el JAR; comprueba que
  Render desplegó el commit correcto.
- **Historial de versiones:** los README antiguos describen instalaciones
  anteriores; utiliza esta guía para el flujo actual.

Logs, credenciales locales, SBOM y otras salidas generadas quedan fuera de
Git. [Detalle de limpieza](documentacion/LIMPIEZA_GIT.md).
