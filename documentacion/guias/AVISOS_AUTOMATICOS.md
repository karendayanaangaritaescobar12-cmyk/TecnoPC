# Aviso al quedar listo para entregar

El vendedor marca el pedido listo desde Preparar pedidos. El técnico cambia
la reparación a Listo desde el seguimiento. No tienen que redactar, copiar ni
enviar un mensaje por separado: el servidor guarda el aviso autorizado junto
con el cambio de estado en una transacción.

Después del commit se activa un proceso independiente que intenta enviar los
avisos pendientes por los canales configurados. No realiza llamadas al proveedor
mientras se modifica la venta o reparación. El proceso periódico de 20 segundos
permanece como recuperación y para los reintentos; no es una garantía de entrega
instantánea. Una cola acumulada, caída de red o demora del proveedor puede retrasar
la notificación. La deduplicación por evento y canal evita generar un aviso nuevo
por volver a guardar Listo en la misma orden.

El texto se genera automáticamente. Ejemplos:

- Pedido: «Tu pedido está listo para recoger en TecnoPC. Conserva esta referencia para la entrega.»
- Taller: «Tu equipo está listo para recoger en TecnoPC. Presenta tu código de ingreso para la entrega.»

La referencia se añade al mensaje. No se incluyen diagnóstico ni notas internas.
La solicitud del taller queda sincronizada: En proceso al recibir/trabajar,
Listo al terminar y Cerrado al entregar o cancelar. El comprobante web de una
compra aprobada indica Listo para recoger cuando el pedido esté marcado así,
al actualizar o consultar su estado.

## Estado actual de activación

El propietario confirmó que todavía no tiene proveedor de correo ni WhatsApp
Business. Los canales permanecen desactivados y no se hicieron envíos reales.
Los avisos con autorización y destino quedan pendientes sin consumir intentos.
Si el cliente no autorizó ningún canal o no dejó datos, no se genera un aviso.
El sistema no puede deducir que un equipo físico está listo: alguien debe registrar
el cambio de estado; lo automático es la creación y procesamiento del aviso.

Para recibir mensajes reales faltan cuenta/remitente y credenciales del correo,
y, para WhatsApp, número, token y plantilla aprobada según el proveedor. Configurar
solo por variables seguras del servidor y probar con un destinatario autorizado.
El asistente local de conexión mantiene ambos canales desactivados deliberadamente.
No habilitar canales sin esas configuraciones ni afirmar entregado cuando el
proveedor solamente acepta la petición. Administrador puede revisar los estados
en Avisos a clientes. No se requiere un trigger SQL ni mensajes manuales.

Reiniciar con ABRIR_CONEXION_TIDB.cmd carga tecnopc-api-avisos.jar y conserva el
resto de módulos. Las pruebas usan H2 y proveedores simulados: envío tras commit,
rollback sin aviso, canales apagados, deduplicación y sincronización de estados.
