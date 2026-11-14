# Comprobantes para clientes de la tienda

Al crear un pedido desde el carrito, la tienda muestra un comprobante con el
logo local y nombre TecnoPC, referencia privada, fecha en Colombia, cliente,
productos, cantidades, precios unitarios sin IVA, subtotal, IVA y total COP.
Los importes y nombres se obtienen de las líneas guardadas del pedido, no del
catálogo actual ni de precios enviados por el navegador.

El cliente puede imprimir o guardar como PDF desde el diálogo de impresión del
navegador. También puede continuar al checkout autorizado de Mercado Pago en
otra pestaña y actualizar el estado al regresar. No se envía el recibo por
correo o WhatsApp en esta entrega; esos proveedores siguen siendo independientes.

## Estado del pago

- Pendiente: comprobante de pedido; no acredita pago ni reserva existencias.
- Aprobado: recibo de compra, una vez consultado el estado confirmado del servidor.
- Rechazado, cancelado o revisión: aviso específico; no se presenta como pagado.

No se acepta como evidencia de pago un parámetro de retorno del navegador.
Los cambios de estado se basan en el registro que actualiza el webhook validado
con el proveedor. El documento no sustituye una factura electrónica DIAN.

La referencia aleatoria de 128 bits permite recuperar el comprobante y se debe
mantener privada. La consulta utiliza POST, límite de solicitudes y no-store;
no devuelve correo, WhatsApp, tokens ni identificadores privados del proveedor.
Se conserva solo la referencia y el enlace de checkout validado en sessionStorage
de esa pestaña. El comprobante puede retomarse tras recargar. Olvidar el pedido
quita ese acceso local, pero no cancela la compra ni devuelve un pago.

La tienda evita volver a crear el pedido al reabrir el comprobante. Esto no
resuelve la conciliación de un checkout inicial que termine con un error de red
antes de que el navegador reciba su referencia; no se añadieron reintentos
automáticos de creación ni garantías de idempotencia del checkout inicial.

## Activación

Reiniciar el backend con ABRIR_CONEXION_TIDB.cmd carga tecnopc-api-pedidos.jar.
Recargar la tienda con Ctrl+F5. La creación de pedidos sigue requiriendo la
configuración válida de Mercado Pago; no se generan pedidos ni recibos ficticios
cuando el checkout está deshabilitado. No hay publicación automática en Internet.

Validación: pruebas JDBC/API de referencias, importes históricos, privacidad y
estados; pruebas Chrome de pedido, logo, diseño móvil, reabrir sin duplicar,
impresión PDF, fallos de consulta, escape de HTML y recuperación por pestaña.
Los pedidos y pagos de las pruebas son simulados; no se cobraron compras reales.
Ejemplos en ui-previews/pedido-web-*-ejemplo.png y pedido-web-pagado-ejemplo.pdf.
