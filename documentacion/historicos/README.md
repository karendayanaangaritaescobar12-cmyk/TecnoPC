# TecnoPC: Java + MySQL

La web y las APIs se ejecutan en Spring Boot. No necesitas PHP, Apache,
XAMPP ni phpMyAdmin. El navegador usa HTML/CSS/JavaScript; las aplicaciones
internas siguen usando JavaFX.

## Requisitos

JDK 21 o posterior, MySQL 8 con una cuenta dedicada y Maven 3.6.3 o posterior
para compilar desde fuentes.

## Base de datos nueva

En MySQL Workbench o el cliente mysql, ejecutar en este orden:

1. `java-backend/sql/database.sql`
2. `java-backend/migration_v7.sql`
3. `java-backend/migration_v8.sql`
4. `java-backend/migration_v9.sql`
5. `java-backend/migration_v10.sql`
6. `java-backend/sql/migration_v12.sql`

No repetir migraciones en una base existente: aplicar solo las que falten.
Las tablas existentes se conservan; quitar la capa anterior no borra datos.
Crear el usuario de ejecucion desde una cuenta administrativa de MySQL con
permisos minimos sobre las tablas necesarias. No usar root en la aplicacion.

## Arranque en Windows

Desde la raiz del proyecto, en PowerShell:

```powershell
$env:DB_USER = 'tecnopc_app'
$env:DB_PASSWORD = '<clave-del-usuario-MySQL>'
$env:ADMIN_USER = 'administrador'
$env:ADMIN_PASSWORD = '<clave-inicial-larga-y-unica>'
./packaging/start-server.ps1 -Build
```

Reemplaza los valores entre angulos. No guardes claves reales en documentos
ni en el frontend. ADMIN_USER y ADMIN_PASSWORD crean el primer administrador;
puedes retirarlas del entorno tras el primer arranque. La clave inicial debe
tener al menos 12 caracteres.

El script requiere java y mvn en PATH, o las opciones -Java y -Maven con sus
rutas absolutas. JAVA_HOME debe apuntar a un JDK al usar Maven. Despues de
compilar puedes iniciar con `./packaging/start-server.ps1`.

Abrir **http://localhost:8081/**. Spring Boot sirve la tienda desde el JAR.
Los escritorios de administrador, vendedor y tecnico usan esa misma URL.
DB_URL permite configurar otro servidor o base; el valor predeterminado
conecta a MySQL local, puerto 3306, base tecnopc.

En Linux/macOS: `mvn -f java-backend/pom.xml package` y despues
`java -jar java-backend/target/tecnopc-admin-api-1.0.0.jar`, exportando antes
las mismas variables de entorno.

## Pagos opcionales

Sin configurar la pasarela, crear pagos responde 503 sin guardar una orden;
el resto del sistema sigue disponible. Para habilitarla configura en Java:

- MP_ACCESS_TOKEN: token del comercio, inicialmente de pruebas.
- MP_WEBHOOK_SECRET: secreto de firma de Webhooks de Mercado Pago.
- MP_NOTIFICATION_URL: `https://tu-dominio/api/public/pagos/webhook`.

El webhook valida HMAC, consulta el pago al proveedor y verifica referencia,
ID, moneda e importe. Solo entonces registra la compra y descuenta stock en
transaccion. Si falta stock queda en revision manual. No se reservan
existencias durante checkout ni hay reembolsos automaticos. Probar sandbox
antes de cobrar: no se han probado aqui credenciales ni pagos reales.

## API publica

| Metodo | Ruta | Funcion |
| --- | --- | --- |
| GET | /api/public/productos | Catalogo |
| POST | /api/public/solicitudes | Servicio y cotizaciones |
| POST | /api/public/chatbot | Asistente local basado en reglas |
| POST | /api/public/seguimiento | Estado por codigo de comprobante |
| POST | /api/public/pagos | Checkout Mercado Pago |
| POST | /api/public/pagos/webhook | Notificaciones firmadas |

Los cuerpos conservan los nombres usados por la web, como cliente_nombre,
items[].producto_id e items[].cantidad para checkout. Las rutas internas
/api/admin, /api/vendedor y /api/tecnico requieren HTTP Basic y el rol
correspondiente. Las ventas anonimas y la gestion publica de empleados
siguen retiradas.

## Seguridad

Java escucha en 127.0.0.1 por defecto. Para Internet usar proxy HTTPS y
firewall; exponer solo web y /api/public, manteniendo privadas las rutas
administrativas y MySQL. Los escritorios exigen HTTPS para servidores remotos.
No publicar carpetas de fuentes, SQL, logs ni respaldos.

Los POST publicos exigen JSON (salvo webhook) y tienen limite de 64 KiB.
Limites por direccion de conexion/ruta/minuto: seguimiento 15, checkout y
solicitudes 10, chatbot/catalogo 60 y webhook 600. Son limites en memoria por
instancia. Tras un proxy los clientes pueden compartir direccion: configurar
limites en el proxy antes de publicar. No se confia en X-Forwarded-For recibido
del cliente. No hay CORS abierto.

El asistente no consulta compras ni guarda conversaciones. La tabla historica
chat_mensajes se conserva. El seguimiento omite datos de cliente y notas internas.

## Pendientes antes de usar datos reales

MySQL limpio, los tres roles, ventas y stock, reparacion, sandbox de pagos,
restauracion de respaldo e instaladores Windows. Las pruebas automatizadas
no sustituyen validar MySQL. Tambien falta actualizar dependencias y cerrar
la idempotencia de ventas presenciales al reintentar.
