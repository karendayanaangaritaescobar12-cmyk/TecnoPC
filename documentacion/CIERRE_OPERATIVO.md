# Cierre operativo: activación, respaldo y pagos

Fecha de comprobación: 13 de noviembre de 2026, Colombia.

## Resultado comprobado

- Los 14 artefactos del manifiesto de la entrega 1.6.1 coinciden con sus hashes SHA-256.
- El servicio existente respondió HTTP 200 en `/healthz`.
- El proceso activo sigue usando `tecnopc-api-avisos.jar`, no la entrega 1.6.1.
- `/livez` respondió 401 en ese proceso anterior.
- No se ha comprobado el respaldo de la cuenta de TiDB ni una restauración real.
- No se ha comprobado conciliación con credenciales reales de sandbox.

Una primera consulta de salud agotó cinco segundos; al repetir la comprobación con
un plazo de veinte segundos respondió 200. Ese timeout aislado no demuestra una
caída de la base. La aplicación anterior conserva límites distintos de la entrega
preparada.

El navegador conectado no estuvo disponible para inspeccionar la cuenta de TiDB Cloud.
No se detuvo el servidor ni se aplicó V3 sobre la base conectada durante esta revisión.

## Comprobación local reproducible

Abrir `VERIFICAR_OPERACION.cmd`. Ejecuta un script que compara los hashes de los
artefactos, consulta salud y vida y, si Windows permite identificarlo, muestra el
nombre del JAR del proceso. Guarda el resultado en `logs/operacion-estado.json`.

El verificador no pide contraseñas, no modifica datos de la base y no declara
listos los pagos por obtener HTTP 200. Puede consultarse con otro plazo mediante
`packaging/verificar-operacion.ps1 -TimeoutSec 20`.

## Primero: respaldo y recuperación

En TiDB Cloud, abrir la instancia y navegar a **Data → Backup**. Comprobar fecha,
estado y retención de una copia terminada. La documentación de PingCAP indica
respaldos automáticos diarios para Starter y un día de retención para Starter
MySQL gratuito; se debe confirmar el plan y las copias efectivas en la cuenta.
[Documentación oficial de respaldos](https://docs.pingcap.com/tidbcloud/backup-and-restore-serverless/).

No confundir tres evidencias distintas:

1. La copia aparece terminada en el proveedor.
2. Se cuenta con una copia/exportación adicional cuando la estrategia lo requiere.
3. Una copia fue restaurada en un entorno separado y los datos se verificaron.

La tercera acredita recuperación; las dos primeras por sí solas no lo hacen.
Starter tiene limitaciones de recuperación que deben revisarse en el plan real.
No restaurar encima de la base en uso como prueba.

La exportación nativa de Starter permite obtener una copia consistente; la descarga
local utiliza la CLI de TiDB Cloud según el procedimiento vigente del proveedor.
No es necesario inventar un exportador propio de tablas para comenzar.
[Documentación oficial de exportación](https://docs.pingcap.com/tidbcloud/serverless-export/).

Al restaurar, verificar coherentemente productos y existencias, compras y detalles,
pedidos y pagos, reparaciones e historial, usuarios y notificaciones. Después de V3,
incluir también `operaciones_idempotentes` y `preferencias_checkout`; no eliminarlas
o restaurarlas de forma independiente de sus operaciones de negocio.

Los respaldos pueden contener datos personales y hashes de acceso. No adjuntarlos al
chat ni incorporarlos a los paquetes de distribución. Definir almacenamiento protegido,
acceso y retención antes de exportar información real.

## Después: activar la entrega preparada

Con respaldo y procedimiento de recuperación disponibles, validar primero V3 en un
entorno separado. Utilizar los clientes actualizados y abrir una nueva ventana desde
`ABRIR_CONEXION_TIDB.cmd`; las ventanas antiguas conservan su selección previa de JAR.
Introducir las credenciales localmente, sin escribirlas en el chat.

Comprobar en el arranque la entrega 1.6.1 y la migración V3, `/healthz` y `/livez`, los
tres roles y los recursos de la tienda. El verificador local ayuda con artefactos y
proceso, pero no sustituye la comprobación de esquema y recorridos reales.

Los clientes nuevos usan rutas que un servidor antiguo no reconoce. Hasta actualizar
el servidor, no interpretar un rechazo de esas rutas como una venta fallida que deba
reenviarse por una ruta antigua. No existe ese fallback automático.

## Conciliación de pagos y sandbox

Para la verificación real se requieren credenciales y usuarios de prueba de Mercado
Pago y un destino HTTPS adecuado para sus notificaciones. No usar dinero o tarjetas
reales para comprobar esta fase.

Probar compra aprobada y rechazada, respuesta perdida, webhook repetido y eventos
fuera de orden. Comparar referencia, identificador del pago, importe, moneda, estado
del proveedor, venta y stock. Registrar también los casos de pago adicional,
devolución y contracargo: no restituir existencias automáticamente sin comprobar qué
ocurrió con la entrega física.

La idempotencia de la entrega 1.6.1 resuelve la repetición local de una intención;
**no equivale a una conciliación financiera completa**. Las preferencias inciertas
quedan en revisión y requieren intervención. Falta implementar/verificar el cierre
de esas discrepancias con el proveedor; no hay evidencia para declararlo aprobado.

## Datos que faltan para continuar

- Fecha y estado de la copia más reciente visible en la cuenta de TiDB Cloud, o
  ubicación de una copia ya disponible, sin exponer su contenido.
- Confirmar disponibilidad de credenciales de prueba de Mercado Pago, sin compartir
  sus valores en el chat.

Evidencia local: [estado de operación](../logs/operacion-estado.json).
Entrega preparada: [idempotencia de ventas y checkout](IDEMPOTENCIA_VENTAS_CHECKOUT.md).
