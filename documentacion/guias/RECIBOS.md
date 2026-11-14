# Imprimir recibos de venta

Abrir TecnoPC-Ventas desde el acceso directo actualizado. Después de registrar
una venta, pulsar Imprimir recibo. También se puede entrar en Recibos e indicar
un número del historial Mis ventas para consultar y reimprimir.

El recibo incluye número, fecha, cliente, documento si existe, vendedor, estado,
productos, cantidades, precios unitarios sin IVA, subtotal, IVA y total guardados.
No recalcula con precios actuales ni registra otra venta al imprimir.
Cada vendedor consulta sus ventas; el administrador puede consultar todas.

La vista previa permite Imprimir y abre el selector de impresoras del sistema.
En Windows puede elegirse Microsoft Print to PDF si está instalado. La impresión
divide automáticamente los recibos largos en páginas. Cancelar no altera la venta.
Es un recibo de venta, no una factura electrónica.

Reiniciar el backend con ABRIR_CONEXION_TIDB.cmd para cargar la ruta de recibos
desde tecnopc-api-recibos.jar. Cerrar y abrir Ventas para cargar la interfaz nueva.
La salida a una impresora física requiere una comprobación en el equipo del usuario.
