# Publicar TecnoPC y activar avisos

## Estado real

La version local contiene Dockerfile, render.yaml, migraciones automaticas y
avisos por correo/WhatsApp. No hay aun una instancia TiDB creada por el usuario,
ni una conexion Render confirmada. No hay URL publicada ni envios reales.
Las pruebas locales usan H2 y proveedores simulados.

## Base y servidor

1. Crear cuenta propia en TiDB Cloud, instancia Starter compatible con MySQL,
   plan gratuito, sin habilitar consumo adicional ni tarjeta.
2. En Connect obtener host, puerto y usuario completo con prefijo. Elegir una
   base vacia disponible, como test, o crear tecnopc. Usar ese nombre en DB_NAME.
3. Conectar Render y subir las fuentes a un repositorio propio, preferiblemente
   privado. Mantener .m2, ZIP, logs, secretos y target fuera del repositorio.
   La plantilla render.yaml usa un unico Web Service Docker con plan free.
4. Configurar en Render DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD,
   ADMIN_USER y ADMIN_PASSWORD. El usuario inicial debe tener 3-60 caracteres
   alfanumericos, punto, guion o guion bajo; la clave 12+ caracteres y hasta
   72 bytes UTF-8. Guardar los secretos solo en el panel del proveedor.
5. Autorizar las direcciones de salida de ese servicio Render en el firewall
   de TiDB. No abrir acceso universal. Flyway necesita permiso DDL para crear
   tablas y su historial durante el despliegue.
6. Desplegar. Spring Boot aplica V1 y V2, crea el primer administrador y sirve
   la tienda desde el mismo JAR. /healthz confirma conectividad de la base.
7. Usar la URL HTTPS asignada por Render en los tres escritorios JavaFX.

No ejecutar previamente instalacion_nueva.sql ni los scripts SQL historicos.
En una base con tablas pero sin historial Flyway, el arranque se detiene para
evitar adoptar un esquema desconocido. Revisar y respaldar antes de registrar
una linea base: no activar baseline-on-migrate ni clean para saltar errores.

Render gratuito puede suspenderse por inactividad. La web puede tardar en
volver; el proceso de avisos no trabaja mientras el servidor esta suspendido.
La cola se conserva en TiDB y se retoma al arrancar. Esto no garantiza avisos
inmediatos ni disponibilidad permanente. No se han construido imagenes Docker
en este equipo porque no tiene Docker; si el build remoto falla, revisar logs.

## Que dispara los avisos

| Evento | Momento |
| --- | --- |
| solicitud_registrada | Se registra servicio o cotizacion |
| compra_registrada | Venta presencial registrada o pago online verificado |
| compra_lista | Vendedor marca un pedido listo para recoger |
| servicio_recibido | Tecnico recibe el equipo y asigna codigo |
| servicio_listo | La reparacion pasa de en_reparacion a listo |

La pantalla del vendedor incorpora Pedidos > Marcar listo y avisar. La del
tecnico conserva su cambio a listo; el servidor encola el aviso automaticamente.
El formulario web y el punto de venta recopilan correo, numero internacional
y autorizacion independiente para cada canal. Una solicitud no es una cuenta
de cliente: no se ha inventado un modulo de registro/login de clientes.

TiDB no admite triggers SQL. Los disparadores estan en Java y guardan los
avisos en la misma transaccion que el negocio. Cambios hechos directamente
en SQL no generan avisos; usar las aplicaciones o las APIs autorizadas.

## Correo con Resend

Crear la cuenta y verificar un remitente/dominio segun los requisitos del
proveedor. La direccion de pruebas no permite contactar clientes arbitrarios.
Configurar RESEND_API_KEY, EMAIL_FROM y luego EMAIL_ENABLED=true. El plan
gratuito tiene cuotas; verificar las actuales en el proveedor antes de activar.
Comprar un dominio no esta incluido en este proyecto ni en el plan gratuito.
Si no dispones de uno, decidir un proveedor/remitente compatible antes de activar.

El transporte usa HTTPS; Render free bloquea los puertos SMTP habituales.
Los reintentos de correo usan una clave de idempotencia estable por aviso.

## WhatsApp oficial de Meta

Configurar WhatsApp Business Platform/Cloud API con numero, token y plantilla
aprobada de utilidad. Variables: WHATSAPP_TOKEN, WHATSAPP_PHONE_NUMBER_ID,
WHATSAPP_TEMPLATE, WHATSAPP_LANGUAGE y WHATSAPP_API_VERSION (predeterminado
v27.0, configurable). Habilitar WHATSAPP_ENABLED=true solo tras confirmar
las condiciones y cargos del proveedor. No se han activado cargos ni cuentas.

Plantilla sugerida tecnopc_actualizacion, idioma es, cuerpo con dos variables:
"TecnoPC informa: {{1}} Referencia: {{2}}". Crear y obtener aprobacion en Meta;
el codigo no crea ni aprueba la plantilla. La primera variable es el texto del
evento y la segunda la referencia. No se incluyen diagnosticos ni notas privadas.
Usar numeros internacionales, por ejemplo +573001234567.

WhatsApp automatico no se puede prometer totalmente gratis: los mensajes
iniciados por el negocio y las plantillas pueden tener cargos de Meta. Los
numeros y tokens de prueba sirven para validacion con destinatarios autorizados.

## Cola, errores y revision

Correo y WhatsApp arrancan desactivados. Los avisos autorizados permanecen
pendientes sin gastar intentos hasta configurar y habilitar cada canal.
Se intenta procesar la cola despues de confirmar la transaccion. El proceso periodico de recuperacion toma hasta 10 por ciclo cada 20 segundos mientras el
servidor esta activo. No mantiene conexiones de base durante las llamadas HTTP.
Los fallos recuperables se reintentan hasta cinco veces con espera creciente.

La clave unica del evento evita duplicar avisos del mismo pedido/estado.
No equivale a entrega exactamente una vez: red y proveedores pueden tener
resultados ambiguos. Si WhatsApp pudo recibir el envio pero hubo un corte,
el aviso queda en revision en lugar de reenviarse a ciegas. Lo mismo ocurre
si se recupera un envio WhatsApp interrumpido por reinicio del servidor.

Estado aceptado significa que el proveedor acepto la peticion; no confirma
lectura ni entrega al destinatario. No se han implementado webhooks de entrega
de correo o WhatsApp. Nunca mostrar como entregado un simple HTTP 200.

El administrador puede consultar Avisos a clientes y Canales de avisos.
API: GET /api/admin/notificaciones y /api/admin/notificaciones/canales.
POST /api/admin/notificaciones/{id}/reintentar reencola un fallo; un aviso en
revision requiere {"confirmarPosibleDuplicado":true}. Revisar antes la consola
del proveedor: este reintento puede duplicar un mensaje que ya se envio.

Antes de usar datos reales: probar TiDB, altas y roles, venta y existencias,
reparacion, checkout sandbox, mensajes a destinatarios de prueba, errores de
proveedor, reintentos, limite gratuito y restauracion de respaldo.

## Referencias oficiales

- https://render.com/docs/free
- https://render.com/docs/blueprint-spec
- https://docs.pingcap.com/tidbcloud/mysql-compatibility/?compatibility=mysql&plan=starter
- https://resend.com/docs/api-reference/emails/send-email
- https://developers.facebook.com/documentation/business-messaging/whatsapp/pricing/
