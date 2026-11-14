# Seguridad y migracion a Java - 12 de noviembre de 2026

La web y las APIs ahora usan Spring Boot + MySQL. Se eliminaron los archivos
PHP y se migraron catalogo, solicitudes, chatbot, seguimiento y pagos a
/api/public. La tienda se incluye dentro del JAR. Ver INICIO_JAVA.md.

Se conservan BCrypt, autorizacion por rol, rechazo de cambios administrativos
iniciados desde navegador y HTTPS para escritorios remotos. Los accesos
publicos antiguos de empleados/compras no se restauraron. El bot no consulta
compras ni guarda conversaciones; la tabla historica se conserva.

Los POST publicos validan campos, limitan cuerpos a 64 KiB y tienen limites
por direccion de conexion y ruta en memoria. El webhook valida HMAC en tiempo
constante, consulta el ID firmado al proveedor y verifica importe, moneda y
referencia. La aprobacion bloquea orden y productos en transaccion. La falta
de stock deja revision manual sin descuentos parciales.

Para publicar hacen falta HTTPS, firewall y limites en el proxy. El limitador
local no sustituye proteccion distribuida ni controles de login. Spring Boot
permanece en 3.5.7: falta evaluar y actualizar dependencias con regresion.
No se afirma ausencia de CVE ni se ha realizado un pentest.

Pendientes: MySQL real, sandbox de pagos, GUI, instaladores Windows y
restauracion de respaldo. Las ventas presenciales requieren idempotencia al
reintentar. El checkout no ofrece una clave de reintento al cliente: un fallo
del proveedor puede dejar una orden pendiente sin enlace. No activar
reintentos automaticos sin conciliacion.

Las pruebas de esta migracion estan en java-backend/src/test/java y los
resultados en java-backend/target/surefire-reports. No se usa como evidencia
la suite anterior de 87 pruebas HTTP, ausente de la copia recibida.

Referencias:
- https://www.mercadopago.com.co/developers/en/docs/checkout-pro-preferences/additional-settings/optional-notifications?scope=prod
- https://spring.io/security/
## Actualizacion: despliegue y avisos

Se incorporan Flyway, cola transaccional, autorizacion por canal, HTTPS a los proveedores, reintentos y revision de resultados ambiguos. Ambos canales estan desactivados. No se ha publicado un servicio ni probado TiDB real. Ver DESPLIEGUE_Y_AVISOS.md y VERIFICACION_JAVA.md.
