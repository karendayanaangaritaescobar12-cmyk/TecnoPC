# Recibos y preparación de facturación electrónica

## Entrega actual

El recibo usa un diseño A4 con cabecera, cliente, vendedor, tabla de productos,
totales y paginación. Vista previa e impresión se generan con el mismo motor.
Microsoft Print to PDF permite guardar el resultado donde esté instalado.
No se ha comprobado la salida en una impresora física del usuario.

La sección Facturación del vendedor y el botón Preparar factura electrónica
del recibo abren un formulario de emisor y comprador. Permiten guardar un JSON
de revisión con la venta y sus importes persistidos. El borrador es una copia
independiente, está marcado demostración, no emitido y no validado por la DIAN.
No es XML UBL, factura fiscal ni plantilla importable en la DIAN. No inventa CUFE,
QR fiscal, prefijos autorizados ni respuestas de validación. No transmite datos.
No constituye integración de emisión automática con el servicio gratuito.

El formulario incluye acceso a la información oficial del servicio gratuito.
Los datos del formulario no se guardan en la base: se usan en el borrador que el
operador decide exportar. Cada emisor debe completar y revisar sus datos reales.
No guardar certificados, claves DIAN o tokens dentro de borradores o del código.

## Paso a producción o venta del producto

El propietario indicó que desarrolla el sistema como demostración para usarlo
o venderlo más adelante. Su registro como persona natural no demuestra que
este software esté habilitado para emitir. Tampoco habilita a futuros compradores.

Antes de activar una emisión real hacen falta:

1. Definir el emisor real y sus datos tributarios según RUT.
2. Elegir modalidad: servicio gratuito DIAN, proveedor tecnológico autorizado
   o software propio. La disponibilidad gratuita del portal no significa que
   exista un conector API automático gratuito para esta aplicación.
3. Completar habilitación, firma digital, pruebas, numeración y asociación de prefijos
   según la modalidad y las condiciones del emisor.
4. Implementar el conector correspondiente y registrar configuración por negocio,
   separada de las ventas y sin credenciales compartidas entre compradores.
5. Completar datos fiscales de compradores, identificación y unidades de productos,
   impuestos, descuentos, medios de pago y demás campos aplicables. Los importes
   históricos por sí solos no garantizan que una factura cumpla requisitos fiscales.
6. Probar emisión, validación, rechazo, contingencia, conciliación, idempotencia,
   notas crédito/débito y entrega de documentos sin duplicar ventas ni numeración.
7. Persistir XML, respuestas, CUFE y estado real; generar representación gráfica
   desde los datos validados, con auditoría, permisos y respaldo.

Para una primera operación sin proveedor contratado se puede usar el servicio
gratuito DIAN por separado una vez habilitado, revisando los datos de la venta.
Esta entrega no automatiza ese procedimiento ni certifica cumplimiento tributario.

## Fuentes oficiales consultadas el 13 de noviembre de 2026

- [Facturación gratuita DIAN](https://micrositios.dian.gov.co/sistema-de-facturacion-electronica/facturacion-gratuita-dian/)
- [Registro y habilitación](https://micrositios.dian.gov.co/sistema-de-facturacion-electronica/requerimientos-para-ser-facturador-electronico/)
- [Modalidades de facturación](https://micrositios.dian.gov.co/sistema-de-facturacion-electronica/como-puedes-facturar-electronicamente/)

Comprobaciones automáticas de esta entrega: paginación de recibos cortos y largos,
importes conservados, independencia del borrador y ausencia de validación o CUFE
ficticios. Estas comprobaciones no equivalen al set de pruebas DIAN.
