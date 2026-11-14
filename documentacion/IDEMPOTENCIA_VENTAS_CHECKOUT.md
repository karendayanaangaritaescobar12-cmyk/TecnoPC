# Idempotencia de ventas y checkout — entrega 1.6.1

Fecha: 13 de noviembre de 2026, Colombia. Alcance: reintentos de venta interna y
creación de pedidos web, con persistencia SQL y recuperación de la misma operación.

## Lenguajes y responsabilidades

| Tecnología | Responsabilidad |
| --- | --- |
| Java / Spring Boot | Validar, identificar la solicitud, ejecutar transacciones, recuperar resultados y coordinar la pasarela. |
| SQL / TiDB-MySQL | Restricción única y persistencia compartida entre procesos. La operación comercial y su resultado se confirman juntos. |
| JavaScript | Generar y conservar la clave del intento en la pestaña, enviarla y recuperar el comprobante sin crear otro pedido. |
| Java / JavaFX | Conservar UUID y huella del intento de Ventas en el perfil del usuario y enviarlos al servidor. |
| PowerShell | Compilación, empaquetado, instalación y selección de la entrega local. |

## Comportamiento

Una operación usa una clave UUID enviada en `Idempotency-Key`. Repetir la clave con
el mismo contenido recupera la operación original. Reutilizarla con contenido distinto
devuelve 409. La clave se separa por ámbito y usuario: una venta no comparte identidad
con un checkout ni con la venta de otra cuenta.

Ejemplo: se registra una venta y se pierde la respuesta. Un reintento con la misma
clave devuelve su número original, sin volver a descontar existencias ni encolar el
mismo aviso. Si la transacción falló antes de confirmar, tanto la venta como su
reserva de idempotencia se revierten; la clave se puede volver a intentar.

**Dos claves diferentes identifican dos operaciones diferentes**, aunque tengan el
mismo cliente y productos. No se pretende deduplicar compras legítimas por parecido.

## Persistencia y transacciones

La migración aditiva `V3__idempotencia.sql` crea:

- `operaciones_idempotentes`: ámbito, actor, UUID, huella SHA-256 y resultado JSON.
  Su clave primaria compuesta impide dos confirmaciones de la misma identidad.
- `preferencias_checkout`: estado y enlace de la preparación del pago, vinculados
  a la referencia del pedido existente.

No se editaron las migraciones V1 y V2. Se probó aplicar V3 a una base H2 con esquema
V2 y registros existentes, conservando pedidos y stock. Esto no reemplaza una copia
de seguridad ni una prueba de migración en TiDB real.

`IdempotencyService` guarda la reserva, las escrituras de negocio y el resultado en
una sola transacción. Una colisión concurrente se resuelve leyendo el resultado
confirmado después de revertir la transacción perdedora. La restricción SQL, no un
mapa de memoria de Java, es la protección compartida entre servidores.

Las respuestas persistidas contienen referencias y valores del resultado, no una
copia del formulario. La huella verifica el contenido; **un hash no constituye por
sí solo anonimización de datos personales**.

## Coordinación de checkout

1. Confirmar localmente el pedido y su identidad en la misma transacción SQL.
2. Reclamar en una transacción corta la preparación de la preferencia.
3. Consultar a la pasarela fuera de la transacción y sin retener esa conexión SQL.
4. Guardar el enlace o un estado de revisión mediante otra transacción corta.

Solo una llamada puede pasar de `nueva` a `creando`. Un reintento concurrente obtiene
la misma referencia; puede recibir temporalmente un enlace vacío. El comprobante
recupera el enlace cuando la preparación termina, sin recrear checkout.

Si la llamada externa falla de forma ambigua, se persiste `revision` y no se crea
automáticamente otra preferencia. Si el proceso desaparece mientras estaba
`creando`, un reintento después de 90 segundos la pasa a revisión. Esa evaluación
se hace al consultar/reintentar, no mediante un trabajador periódico nuevo.

**La revisión requiere intervención y conciliación.** No se implementó conciliación
automática de preferencias, reembolsos o pagos múltiples. Tampoco se certificó una
garantía del proveedor por enviarle una cabecera: la defensa de esta entrega es el
estado persistente local y la ausencia de una segunda llamada automática ante
incertidumbre. Se conserva el adaptador de Checkout Pro basado en preferencias.

## Clientes y compatibilidad

Los clientes nuevos escriben en:

- `POST /api/public/v2/pagos` para checkout.
- `POST /api/vendedor/v2/ventas` para ventas autenticadas.

Ambos exigen un UUID válido. Las rutas anteriores siguen disponibles en el servidor
nuevo, pero también exigen la clave. Los webhooks conservan su ruta y autenticación
HMAC originales. Ambas rutas públicas de checkout comparten la cuota existente.

Los clientes nuevos **no vuelven a intentar por la ruta antigua** cuando el servidor
no reconoce la ruta nueva. Así se evita que un servidor anterior ignore la cabecera
y procese una operación sin esta protección. Se deben actualizar servidor y clientes.

### Navegador

La pestaña conserva UUID y huella en `sessionStorage` antes del POST; no guarda allí
una copia del nombre, contacto o carrito del intento. Las líneas se ordenan por ID
para mantener estable el envío. Tras una respuesta perdida, la misma pestaña puede
recargar y reenviar el mismo contenido con la misma clave.

Un cambio de contenido no genera otra clave silenciosamente. El usuario debe
comprobar el resultado antes de resolver el intento anterior. Una respuesta 400/422
definitiva permite corregir el formulario. Al conservar el comprobante se retira el
intento pendiente; la referencia permanece en el mecanismo de recibos existente.

Si no se puede persistir la identidad, no se envía checkout. El catálogo y carrito
siguen funcionando en memoria. Cerrar la pestaña, borrar su almacenamiento, usar otra
pestaña o generar otra clave no permite reconstruir automáticamente un intento perdido.

### Ventas de escritorio

El perfil de usuario conserva UUID y huella, separados por servidor y cuenta. No
guarda contraseñas ni datos del cliente en esa entrada. Un fallo de almacenamiento
impide enviar la venta, incluso en el segundo intento.

La operación pendiente se conserva ante errores y reinicios. Para reenviarla se
necesita reconstruir el mismo contenido; no se restauran automáticamente los campos
del formulario. Si no se dispone de esos datos, consultar Mis ventas y recibos.
El botón de resolución exige reconocer que descartar la clave local no anula la
venta y podría permitir otra. La clave se retira cuando el servidor confirma la venta.

## Pruebas

- **143 pruebas backend aprobadas**, sin fallos, errores u omisiones.
- Dentro de ellas, 12 pruebas SQL de idempotencia y 3 pruebas HTTP de integración;
  también se comprobó que las dos rutas de checkout compartan la cuota.
- **4 pruebas de Ventas** sobre conservación de la clave, aislamiento por cuenta,
  cambios de datos y fallos de almacenamiento. Usan preferencias en memoria para
  no escribir datos de prueba en el perfil real.
- **39 comprobaciones de navegador**: 17 de tienda, 10 de recibos, 6 de resiliencia
  y 6 de idempotencia. Incluyen respuesta perdida, recarga, servidor antiguo,
  ausencia de almacenamiento y pedido en preparación/revisión.
- **15 comprobaciones gráficas** de los tres clientes con un servidor HTTP simulado,
  sin escrituras o envíos reales.

Se verificó una venta y un aviso ante dos reintentos concurrentes, rollback de una
escritura tardía, reutilización después de recrear servicios y una sola llamada
simulada a la pasarela. Las pruebas de persistencia usan H2 en modo MySQL; no son
una certificación de concurrencia o migración del TiDB real.

Evidencias: [resumen y hashes](idempotencia/evidencias.json),
[regresión backend](../logs/idempotencia-backend-1.6.1.log),
[pruebas de Ventas](../logs/idempotencia-ventas-1.6.1.log),
[navegador](../logs/idempotencia-test-idempotency-1.6.1.log),
[interfaz de escritorio](../logs/idempotencia-desktop-ui-1.6.1.log) y
[SBOM del servidor](idempotencia/backend-sbom.json).

## Activación y operación

La entrega final es `tecnopc-api-idempotencia-1.6.1.jar`; los paquetes finales están
en `dist/Idempotencia-20261113-1.6.1`. Los scripts de conexión e instalación seleccionan
esa entrega. La candidata 1.6.0 queda como artefacto de desarrollo, no como selección
final de los scripts.

Antes de activar V3 en TiDB, disponer de copia y procedimiento de recuperación.
Después, cerrar las aplicaciones antiguas, usar los accesos actualizados, cerrar y
reabrir la ventana de conexión e iniciar el servidor nuevo. Flyway aplicará V3 al
arrancar; comprobar el arranque y los tres roles antes de operar. Recargar la tienda.

No se aplicó V3 ni se hicieron ventas o pagos de prueba en la base conectada durante
este trabajo. La activación y verificación reales quedan pendientes.

No eliminar ni expirar registros de idempotencia de forma independiente de las ventas
y pedidos: se perdería la protección de reintentos antiguos. Las copias y restauraciones
deben conservar coherentemente esas tablas. La retención definitiva sigue pendiente
de la política de datos y las necesidades del negocio.

Referencias técnicas: [transacciones programáticas de Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html),
[manejo de errores InnoDB](https://dev.mysql.com/doc/refman/8.0/en/innodb-error-handling.html) y
[preferencias de Checkout Pro](https://www.mercadopago.com.co/developers/en/docs/checkout-pro-preferences/overview).
