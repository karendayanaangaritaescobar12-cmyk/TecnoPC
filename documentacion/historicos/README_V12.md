# TecnoPC V12 - Mercado Pago en Java

La integracion de pagos ahora forma parte de Spring Boot: no requiere PHP,
Apache, XAMPP ni extensiones curl. Ver [instalacion](packaging/INICIO_JAVA.md).

Configurar MP_ACCESS_TOKEN, MP_WEBHOOK_SECRET y MP_NOTIFICATION_URL en el
proceso Java. La URL de notificaciones debe ser HTTPS y terminar en
/api/public/pagos/webhook. Sin configurar esas variables, checkout queda
cerrado con respuesta 503, y la tienda y los servicios siguen disponibles.

POST /api/public/pagos valida cantidades y stock, toma precios de MySQL,
calcula IVA y crea la preferencia. No descuenta stock hasta confirmar el pago.
POST /api/public/pagos/webhook valida HMAC, consulta el ID firmado al proveedor
y comprueba moneda, importe y referencia. El descuento y la compra son
transaccionales; las notificaciones duplicadas de un pago aprobado no vuelven
a descontar stock. La falta de stock queda en revision manual.

No hay reservas, reembolsos automaticos ni conciliacion programada. Un fallo
al crear la preferencia puede dejar una orden pendiente sin enlace. Las
pruebas automatizadas no sustituyen probar credenciales y webhooks sandbox.
No usar datos reales hasta completar MySQL, sandbox y pruebas de instalacion.

La migracion de pagos es java-backend/sql/migration_v12.sql. Ejecutarla solo
si falta, despues de las migraciones previas. La base nueva se crea con
java-backend/sql/database.sql. No se ha borrado ninguna tabla existente.