# Verificacion: despliegue Java y avisos

12 de noviembre de 2026. Servidor y tres escritorios compilados. Java con
destino 21; pruebas ejecutadas con el JDK incluido en IntelliJ.

| Suite | Pruebas | Fallos/errores |
| --- | ---: | ---: |
| CloudConfigurationTest | 1 | 0 |
| NotificationIntegrationTest | 16 | 0 |
| NotificationSenderTest | 5 | 0 |
| PaymentServiceTest | 10 | 0 |
| PaymentTransactionTest | 4 | 0 |
| PublicApiTest | 23 | 0 |
| SecurityConfigTest | 10 | 0 |
| StartupIntegrationTest | 3 | 0 |
| TiDBMigrationSupportTest | 1 | 0 |
| WebhookSignatureTest | 5 | 0 |
| Total | 78 | 0 |

Spring Boot arranca con H2 temporal: Flyway crea el esquema y el historial,
crea el primer administrador y permite servir la tienda y la API. Se prueban
solicitudes reales a MockMvc con JDBC real y dos avisos por registro autorizado.
La deteccion del modulo TiDB se verifica con metadatos/SELECT VERSION simulados.
Esto no sustituye una conexion real a TiDB, que aun no esta creada.

Las pruebas de avisos usan transacciones JDBC reales y transportes simulados:
rollback conjunto, consentimiento, deduplicacion, pago aprobado, reparacion
completa, compra lista, dos procesos concurrentes, reintentos limitados,
canales desactivados y revision ante envios ambiguos o idempotencia vencida.
No se enviaron correos ni mensajes WhatsApp a personas.

El JAR contiene frontend, migraciones y API. No contiene PHP. Dockerfile y
render.yaml estan preparados, pero no se construyo una imagen Docker ni se
publico una instancia Render. No hay URL publica ni credenciales reales.

Pendientes: crear TiDB y conectar Render, validar la base real y sus permisos,
configurar correo con remitente verificado y WhatsApp con plantilla aprobada,
probar destinatarios autorizados, pasarela sandbox, GUI e instaladores,
restauracion de respaldo y actualizacion de dependencias. WhatsApp puede tener
cargos; ambos canales permanecen desactivados de manera predeterminada.

Ver packaging/DESPLIEGUE_Y_AVISOS.md para los pasos de activacion.
## Conexion real a TiDB comprobada

12 de noviembre de 2026: Spring Boot conecto a la base tecnopc mediante TLS
VERIFY_IDENTITY y aplico correctamente V1 y V2 desde una base vacia. Los
registros de Flyway confirman el esquema en version 2. Se verificaron HTTP
200 en /healthz, / y /api/public/productos desde este equipo. El catalogo
esta vacio; no se cargaron productos ni ventas ficticias.

Esta comprobacion valida conexion, migraciones y consultas iniciales. Aun
faltan ventas, reparaciones, restauracion y pasarela sandbox contra esa base.
Render no esta publicado y correo/WhatsApp siguen desactivados. Flyway emite
una advertencia porque el adaptador TiDB solo declara verificacion hasta 5.0;
las dos migraciones se aplicaron en la instancia actual, pero siguen siendo
necesarias las pruebas de regresion para cambios futuros.