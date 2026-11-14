# Revisión de la web y seguridad de TecnoPC

**Fecha:** 13 de noviembre de 2026 · Colombia.  
**Alcance:** tienda HTML/CSS/JavaScript, APIs Spring Boot que la soportan,
pagos, comprobantes, solicitudes, seguimiento, asistente y configuración de despliegue.

## Dictamen

**Actualización posterior de acceso y dependencias:** se añadió protección de
autenticación y se preparó una entrega con bibliotecas actualizadas. El detalle,
las pruebas y los avisos que permanecen abiertos se mantienen en
[SEGURIDAD_ACCESO_Y_DEPENDENCIAS.md](SEGURIDAD_ACCESO_Y_DEPENDENCIAS.md).
Las cifras y versiones de la auditoría original que siguen debajo son históricas;
esta actualización no cierra los demás pendientes de producción.

**Entrega posterior 1.5.0 — resiliencia:** límites de carga y JSON, caché del catálogo,
esperas acotadas de base de datos, manejo de caídas, CSP, diagnóstico administrativo
y paginación visual. Véanse [los resultados y límites](RESILIENCIA_BACKEND_FRONTEND.md):
127 pruebas backend y 33 comprobaciones de navegador aprobadas. Requiere activar el
JAR nuevo; no acredita capacidad de producción, restauración ni idempotencia completa.

**Entrega posterior 1.6.1 — idempotencia persistente:** se implementaron restricciones
SQL, recuperación del resultado de ventas y checkout, protección de versiones antiguas
y manejo conservador de preferencias inciertas. Véase
[IDEMPOTENCIA_VENTAS_CHECKOUT.md](IDEMPOTENCIA_VENTAS_CHECKOUT.md).
Hay 143 pruebas backend, 4 de Ventas, 39 de navegador y 15 comprobaciones gráficas
aprobadas. Siguen pendientes la activación y validación de V3 en TiDB real y la
conciliación completa con proveedores; los hallazgos originales son históricos.

La web es un **prototipo avanzado con funciones implementadas y pruebas útiles**.
Todavía no hay evidencia suficiente para presentarla como una tienda lista para
operar públicamente con pagos reales, ni para venderla como un producto terminado.
Los principales cierres son acceso seguro, dependencias, consistencia de pagos y
ventas, despliegue reproducible, respaldo y validación completa con proveedores.

Esta entrega es una revisión y un plan de mejoras. **No modifica las medidas de
seguridad, las contraseñas, los proveedores ni la configuración de producción.**
No es una certificación, un pentest completo ni una garantía de ausencia de fallos.

## Qué se comprobó ahora

| Comprobación | Resultado y límite de la evidencia |
| --- | --- |
| Backend | **96 pruebas aprobadas**, cero fallos y errores. Incluyen permisos, entradas, pagos, transacciones, avisos y recibos. |
| Tienda en Chrome | **17 comprobaciones aprobadas** de catálogo, filtros, carrito, configurador, solicitudes, seguimiento, asistente, móvil y escape de texto. |
| Recibo web en Chrome | **10 comprobaciones aprobadas** de marca, importes, estados, impresión PDF, móvil, recuperación, errores y texto malicioso. |
| Base de pruebas | H2 en modo MySQL y simulaciones. No equivalen a repetir todos los flujos en TiDB. |
| Operaciones externas | No se hicieron cobros, envíos a clientes ni emisiones DIAN. |
| Servidor local en 8081 | Catálogo y salud respondieron 200; resumen administrativo sin credenciales, 401. El recurso nuevo `/js/order-receipt.js` respondió 401. La consulta inicial de `/` no obtuvo respuesta en su plazo de 10 segundos. Revisar versión activa, logs y rutas de error antes de atribuir una causa. |
| Cabeceras del servidor local | En las respuestas API revisadas: `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` y control de caché. No se observaron CSP, Referrer-Policy ni Permissions-Policy. No se comprobó un dominio público HTTPS. |
| Dependencias | Se inspeccionaron las bibliotecas dentro de `tecnopc-api-avisos.jar`, no solo el contenido de la caché Maven. |

Evidencias de esta ejecución:

- [Pruebas backend](../logs/auditoria-backend-tests.log) y [reportes por clase](../java-backend/target/surefire-reports).
- [Pruebas de tienda](../logs/auditoria-tienda-tests.log).
- [Pruebas de recibos](../logs/auditoria-recibos-tests.log).
- [Consultas del servidor local](../logs/auditoria-runtime.json).
- Scripts reproducibles: [tienda](../packaging/test-store.cjs) y [recibos](../packaging/test-order-receipt.cjs).

Los scripts de navegador prueban fuentes servidas en 8082 con solicitudes de
escritura simuladas. **Un resultado aprobado allí no prueba que el proceso de 8081
esté ejecutando el último JAR.** No se revisaron por dentro las cuentas de TiDB,
Render, Meta, correo o Mercado Pago, ni se volvió a probar la GUI de escritorio.

## Revisión de todas las secciones de la tienda

| Área | Lo que existe | Qué falta o conviene mejorar |
| --- | --- | --- |
| Inicio | Marca, navegación, búsqueda, categorías y destacados. | Datos comerciales verificables, fotografías reales autorizadas, propuesta de valor y contacto claros. Mostrar mejor la recuperación cuando el backend está caído. |
| Catálogo | Consulta real, filtros, búsqueda, orden y disponibilidad. | Ficha individual, varias fotos, marca y atributos estructurados, paginación y actualización de stock. La consulta pública corta en 2000 registros. |
| Categorías | PC, portátil y tipos de componentes. | La interfaz anuncia periféricos, pero el esquema y el formulario de producto no tienen una categoría propia para monitor, teclado o ratón. Se intenta deducirla del texto: cerrar esta incoherencia. |
| Imágenes | Logo y gráficos SVG locales; no dependen de fotos externas. | No existe todavía un flujo completo de carga y asignación de fotos por producto. Si se añade: formatos permitidos, tamaño, decodificación segura y almacenamiento controlado. |
| Armar mi PC | Selección, estimación y advertencias básicas de socket y DDR. | Reglas estructuradas de BIOS, potencia, dimensiones, conectores y refrigeración. No afirmar compatibilidad completa. Guardar componentes, cantidades y desglose en la cotización del servidor. |
| Costos del configurador | Suma componentes y aplica IVA; muestra ensamble. | `ENSAMBLE = 80000` está fijado en JavaScript. La solicitud enviada conserva texto de componentes, no el desglose económico visto en pantalla. Llevar tarifas y cotización definitiva al servidor. |
| Carrito | Cantidades, disponibilidad consultada y preparación del checkout. | Mejor conciliación ante cambios de precio/stock, consistencia de decimales y recuperación de pedidos incompletos. El catálogo/carrito redondea a pesos enteros y el recibo muestra dos decimales. |
| Pago | Preferencia con proveedor, validación de enlace y webhook firmado. | Activar y probar sandbox extremo a extremo, recuperación de preferencias fallidas, pedidos abandonados, conciliación y tratamiento de devoluciones/contracargos. No están confirmados los pagos reales. |
| Comprobantes | Logo, detalle persistido, IVA, total, estados, PDF y móvil. | Recuperación si se pierde la pestaña o referencia; política de caducidad y revocación. El PDF se guarda desde la impresión del navegador: no es una factura electrónica. |
| Servicio técnico | Registro de solicitud y autorizaciones por canal. | Completar condiciones del servicio, manejo de duplicados y edición/cancelación controlada. Comprobar el recorrido real con el taller y TiDB. |
| Seguimiento | Código aleatorio y cronología pública limitada. | Recuperación segura de código perdido, política de acceso a órdenes antiguas y prueba con clientes reales. Conservar la exclusión de diagnósticos y datos privados. |
| Asistente | Reglas, contexto breve, presupuesto y consulta al catálogo. | Datos técnicos mejores, más casos de conversación y traspaso claro a una persona. Los candidatos de armado se eligen por precio: no son una recomendación técnica validada. No es un modelo entrenado. |
| Promociones | Sección que no inventa descuentos. | Modelo de descuentos, vigencias, stock, permisos y cálculo validado en servidor antes de anunciar ofertas reales. |
| Avisos | Cola transaccional, disparo tras guardar, reintentos y deduplicación. | Crear/configurar proveedores. Actualmente no hay correo ni WhatsApp activos. Falta confirmación de entrega por webhooks y prueba real de destinatarios autorizados. |
| Facturación electrónica | Borrador de demostración en la aplicación de ventas. | Emisor, habilitación, firma, numeración, conector, validación, documentos y notas. No existe emisión real DIAN ni una integración automática gratuita confirmada. |
| Accesibilidad | Etiquetas, foco, cierre del chat, mensajes y móvil comprobados parcialmente. | Auditoría con lector de pantalla, teclado, contraste, zoom 200–400 %, errores de formularios y recibos largos. No se afirma cumplimiento WCAG completo. |
| SEO y rendimiento | Página navegable y recursos locales. | Metadescripción, metadatos sociales, sitemap/robots cuando se publique, recursos versionados, compresión/caché selectiva y mediciones Lighthouse. Las vistas por hash limitan el SEO de fichas individuales. |
| Información al cliente | Consentimiento separado para avisos. | Identidad del responsable, política de tratamiento de datos, términos, garantía, devoluciones, recogida/entrega y atención verificables. Revisarlos con el responsable del negocio antes de publicar. |

**No es necesario obligar al cliente a crear una cuenta para navegar o cotizar.**
El acceso obligatorio corresponde a las operaciones internas; para pedidos y
seguimiento público hacen falta referencias privadas y controles adecuados.

## Controles de seguridad que sí existen

- BCrypt con costo 12 y permisos `ADMIN`, `VENDEDOR` y `TECNICO` en servidor.
- Denegación por defecto de rutas no autorizadas y API sin sesiones HTTP de usuario.
- Consultas parametrizadas. La selección dinámica de tablas en avisos está limitada
  por una lista permitida; no se identificó allí una concatenación libre del usuario.
- Validación y cuerpo máximo de 64 KiB en los POST públicos; cuotas por ruta e IP
  de conexión, acotadas en memoria y con respuesta 429.
- Cambios internos iniciados desde navegador rechazados por `DesktopRequestFilter`;
  se exige JSON para mutaciones. Los clientes nativos requieren HTTPS para servidores remotos.
- Precios de pago obtenidos de la base, transacciones y bloqueo de stock al aprobar.
- Webhook HMAC con comparación de tiempo constante y comprobación de ID, importe,
  moneda y referencia con el proveedor. Repetir una aprobación ya procesada no repite la venta.
- Referencia de pedido aleatoria de 128 bits; recibo con POST y `no-store`, sin
  divulgar correo, WhatsApp ni datos de contacto privados del pago.
- Escape de datos dinámicos revisados y pruebas de HTML malicioso en productos y recibos.
- TLS `VERIFY_IDENTITY` para TiDB en el perfil cloud; Flyway sin `clean` ni adopción
  automática de un esquema desconocido.
- Avisos en la transacción del negocio, envío después del commit, revisión de
  respuestas ambiguas y consentimiento independiente por canal.
- Docker ejecuta el servidor como usuario sin privilegios; secretos y herramientas
  locales están excluidos de las rutas habituales de publicación.

Estos controles reducen riesgo, pero no sustituyen los cierres siguientes.

## Hallazgos de seguridad y operación, ordenados por prioridad

P0 = bloquear una puesta en producción hasta verificarlo. P1 = alta prioridad
antes de operar con datos reales. P2 = mejora necesaria para operación sostenida.
Se distingue un control ausente confirmado de un riesgo que todavía debe reproducirse.

### P0 — Versión activa y despliegue sin una referencia única

**Evidencia:** conviven varios JAR y carpetas `dist`; los scripts eligen nombres
concretos. Las respuestas locales del recurso de recibos no coinciden con lo que
el JAR y las pruebas de fuentes incluyen. Hubo resultados inconclusos en la portada.

**Riesgo:** un usuario puede probar una versión y operar otra; actualizar un JAR
abierto puede dejar un proceso con código o recursos de distintas entregas.

**Mejora:** una versión identificable por entrega, compilación reproducible,
artefacto inmutable, parada/inicio controlados, comprobación de recursos y plan
de reversión. Agregar identificación no sensible de versión al diagnóstico de salud.

**Aceptación:** instalación limpia, misma versión en servidor y clientes, todas
las vistas y recursos 200 donde corresponda y flujos reales aprobados en esa entrega.

### P1 — Credenciales y protección contra intentos de acceso

**Evidencia:** se admiten claves de ocho caracteres y se solicitaron claves
predecibles durante el desarrollo. `SecurityConfig` usa HTTP Basic;
`PublicRequestFilter` limita `/api/public/**`, no los intentos de autenticación
de las APIs privadas. No se observó MFA ni un mecanismo equivalente de contención.

**Riesgo:** adivinación de credenciales, reutilización y consumo de recursos al
verificar repetidamente BCrypt. Cambiar el nombre visible del formulario no lo resuelve.

**Mejora:** reemplazar las claves conocidas antes de publicar, cuentas individuales,
límites por cuenta y origen con espera progresiva, recuperación segura y MFA para
operaciones sensibles según el modelo de autenticación elegido. No copiar las
claves actuales a este README, al ZIP o a las fuentes.

**Aceptación:** pruebas de intentos fallidos, recuperación y desactivación; tráfico
privado remoto exclusivamente cifrado. Evitar bloqueos permanentes que permitan
denegar acceso a otra persona. [Guía OWASP de autenticación](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).

### P1 — Dependencias con parches pendientes de evaluar

**Evidencia del JAR:** Spring Boot 3.5.7, Spring MVC 6.2.12, Spring Security 6.5.6,
Tomcat 10.1.48, Jackson Databind 2.19.2, MySQL Connector/J 9.4.0 y Flyway Core 11.7.2.
No se ejecutó un análisis SCA completo de todas las dependencias o de la imagen.

Apache incluye Tomcat 10.1.48 en el rango de CVE-2026-24880, relacionado con
peticiones interpretadas de forma distinta por un proxy que permita secuencias
CRLF en extensiones chunked. **No se demostró explotación en este despliegue**;
la configuración del proxy condiciona ese caso. Hay que actualizar y revisar
los avisos aplicables, no asumir que un salto mínimo cubre todos los parches.
[Avisos oficiales de Tomcat](https://tomcat.apache.org/security-10.html).

**Mejora:** generar inventario/SBOM, ejecutar SCA con base actualizada, actualizar
un BOM soportado y revisar JDK, JavaFX, conectores, imágenes Docker y dependencias
de herramientas. Repetir regresión de seguridad y negocio después de actualizar.

**Aceptación:** informe guardado con versiones y fecha, tratamiento de cada alerta
y evidencia de las condiciones de explotabilidad. No afirmar «sin CVE» por pasar tests.

### P1 — Reintentos y consistencia de dinero

**Evidencia:** `PaymentService.start` crea una referencia nueva en cada llamada;
el pedido se guarda antes de crear la preferencia externa. `VendedorController.venta`
tampoco recibe una clave de idempotencia. Deshabilitar un botón y reabrir un recibo
sin crear pedidos ayuda, pero no cubre la pérdida de respuesta del primer POST.

**Riesgo:** duplicar ventas al reintentar o dejar pedidos pendientes sin enlace.
No se observó un flujo completo de reembolso/contracargo y conciliación posterior.

**Mejora:** idempotencia persistida con contenido asociado, restricciones únicas,
recuperación por estado, conciliación y transacciones. Separar el bloqueo final de
stock de una política explícita de reservas; hoy un pedido pendiente no reserva.

**Aceptación:** simular respuesta perdida, doble clic, dos equipos, fallos del
proveedor y reintentos: una operación comercial y un movimiento de stock por intención.

### P1 — HTTPS, proxy y cuotas de la API

**Evidencia:** local escucha en loopback; hosted escucha en `0.0.0.0`.
`server.forward-headers-strategy=none`. El limitador usa la dirección del socket,
con estado por proceso. `/healthz` consulta la base y queda fuera de esa cuota.

**Riesgo condicionado al despliegue:** un proxy puede agrupar a todos los clientes
bajo una IP y agotar su cuota; varias instancias no comparten límites. Una ruta
de salud sin protección de carga también puede consumir conexiones.

**Mejora:** TLS en la entrada pública, acceso directo al backend restringido,
cabeceras reenviadas solo de proxies confiables, cuotas coordinadas y protección
de carga en el perímetro. Conservar validación HMAC del webhook y dimensionar su
cuota para no bloquear reintentos legítimos del proveedor.

**Aceptación:** verificar dominio real, HTTP→HTTPS, certificado, IP de cliente,
cabeceras y acceso desde dos orígenes. HSTS se comprueba sobre HTTPS real, no
se considera demostrado ausente en producción por no aparecer en loopback HTTP.

### P1 — Respaldo y recuperación no acreditados en esta auditoría

No se realizó una restauración real ni se encontró aquí un procedimiento
automatizado con evidencia vigente. Definir copias cifradas, permisos, retención,
ubicación separada y objetivos de recuperación. Restaurar una copia en un entorno
limpio y comprobar ventas, productos, referencias, contactos y cola de avisos.

### P2 — CSP y endurecimiento del navegador

**Evidencia:** falta configuración explícita de CSP, Referrer-Policy y
Permissions-Policy. Sí existen cabeceras predeterminadas de Spring, por lo que
no corresponde decir que todas las cabeceras están ausentes.

Aplicar primero CSP en modo reporte y después hacerla cumplir, limitando scripts,
objetos, bases y marcos. Refactorizar estilos inline antes de exigir una política
estricta de estilos. Mantener escape y acciones permitidas del chat; CSP no
reemplaza esas defensas. Comprobar logo, SVG, impresión y checkout bajo la política.
[Guía OWASP sobre CSP](https://cheatsheetseries.owasp.org/cheatsheets/Content_Security_Policy_Cheat_Sheet.html).

`csrf.disable()` no se clasifica por sí solo como fallo demostrado: la API es
stateless y existe un filtro contra mutaciones de navegador. Hay que mantener
pruebas de este diseño y revisarlo antes de incorporar cookies o login web interno.

### P2 — Autorización fina y auditoría

Los recibos de ventas sí comprueban propietario o administrador. En cambio,
las rutas de reparaciones y pedidos permiten acceso global dentro de su rol.
Definir si esto es intencional para la sucursal o si deben restringirse por
técnico asignado, vendedor y negocio. No se afirma una fuga sin definir esa política.

La tabla histórica de auditoría no equivale a una auditoría general activa: no se
observó registro uniforme de cambios en usuarios, precios, ajustes y exportaciones.
Agregar actor, momento, operación, referencia y valores pertinentes sin secretos.
Probar acceso a objetos de otros responsables y separación de negocios si se vende
el sistema para múltiples empresas.

### P2 — Validación y concurrencia de APIs internas

El límite de 64 KiB está en el filtro público; la propiedad de tamaño de formulario
de Tomcat no limita por sí sola todos los JSON internos. Existen DTO internos sin
límites uniformes para texto y documento; ciertas entradas nulas o cadenas demasiado
largas pueden acabar en errores 500 o errores de base en lugar de rechazo claro.
Unificar validación, límites de cuerpos y respuesta de error sin detalles privados.

El control de contraseñas del escritorio limita 72 bytes BCrypt, pero los endpoints
de usuarios no aplican uniformemente ese máximo. Alinear ambas capas. La protección
del último administrador y la edición de inventario merecen pruebas concurrentes:
la primera usa comprobaciones separadas; la segunda compara stock, no una versión
general del registro. Son riesgos de revisión estática, no incidentes demostrados.

### P2 — Privacidad de referencias, consentimiento y archivos

El recibo público usa una referencia secreta, sin cuenta de cliente. Quien la
conozca puede consultar ese documento. No se observó expiración o revocación del
acceso; definir su duración y recuperación, especialmente en equipos compartidos.
El seguimiento usa otro código privado aleatorio. No mostrar esas referencias en
analítica, URLs públicas o capturas de clientes reales.

Completar política de datos, finalidad, fecha y versión de consentimiento,
retención y eliminación. Los borradores fiscales y PDF exportados también contienen
datos personales. Establecer permisos y almacenamiento; no incorporar claves o
certificados a paquetes de demostración.

### P2 — Webhook, supervisión y avisos

La firma comprueba HMAC, pero no se observó una política temporal para `ts` ni
una caché específica de eventos recibidos. Revisar repetición y consumo de recursos
sin romper reintentos válidos. La idempotencia de una aprobación ya existente es
una defensa presente y debe conservarse.

Implementar alertas de cola, pagos en revisión, errores, consumo de conexiones,
disco y disponibilidad; rotación de logs y criterios de retención. El envío
inmediato tras commit no asegura entrega inmediata. Probar caídas/reinicio,
backlog y respuestas ambiguas; «aceptado por proveedor» no significa entregado.

## Orden recomendado para cerrar una versión estable

1. **Una entrega reproducible:** seleccionar versión, desplegarla limpia y comprobar
   portada, recursos, API y tres roles con esa misma versión.
2. **Seguridad de entrada:** cambiar credenciales de desarrollo, proteger intentos,
   revisar dependencias y configurar TLS/proxy/cuotas.
3. **Consistencia comercial:** idempotencia de ventas y checkout, conciliación,
   precios/IVA/ensamble en servidor y cotización recuperable.
4. **Contenido real y operación:** productos con fotos y especificaciones,
   categorías coherentes, datos comerciales y atención de errores.
5. **Prueba completa:** solicitud→recepción→diagnóstico→autorización→listo→entrega;
   compra→pago sandbox→stock→pedido listo→recibo y aviso a un destinatario autorizado.
6. **Recuperación y publicación:** restauración verificada, monitorización, revisión
   de privacidad/accesibilidad y aceptación del responsable del negocio.
7. **Producto vendible:** configuración por empresa, aislamiento de datos, actualización
   y soporte, instalación en Windows limpio y habilitación fiscal de cada emisor
   cuando se ofrezca emisión electrónica real.

No hace falta sumar más módulos para empezar ese cierre. La prioridad es demostrar
que lo existente funciona en conjunto y que la entrega desplegada coincide con la probada.

## Pendientes de comprobación externa

- Firewall, permisos mínimos, cuenta de ejecución y respaldos del TiDB real.
- Cuenta de migraciones separada o reducción controlada de permisos DDL.
- Despliegue público, certificado, cuotas, cabeceras y escaneo de imagen/dependencias.
- Sandbox y estados completos de Mercado Pago, reembolsos y conciliación.
- Proveedores de correo/WhatsApp: el propietario indicó que todavía no tiene ninguno.
- Emisión y set de pruebas DIAN: sigue siendo preparación de demostración.
- Rendimiento/carga, accesibilidad completa y prueba de restauración en equipo limpio.

**Criterio de cierre:** cada pendiente debe tener responsable, prueba reproducible
y evidencia de aprobación. Los tests actuales son una base de regresión; no son
por sí solos autorización para operar cobros reales o prometer cumplimiento fiscal.
