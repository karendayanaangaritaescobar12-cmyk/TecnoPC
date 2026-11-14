# Resiliencia de backend y frontend — entrega 1.5.0

Fecha: 13 de noviembre de 2026, Colombia.

Actualización posterior: la entrega [1.6.1 incorpora idempotencia persistente](IDEMPOTENCIA_VENTAS_CHECKOUT.md).
Los resultados y pendientes de este documento corresponden a la entrega 1.5.0;
la conciliación con proveedores y las comprobaciones de producción siguen abiertas.

Se preparó una entrega que limita trabajo concurrente, reduce consultas repetidas,
acota esperas y conserva el estado del navegador ante errores. **Reduce el riesgo de
saturación; no garantiza disponibilidad ilimitada ni impide cualquier fallo de TiDB,
la red o el alojamiento.** La capacidad real debe medirse en el entorno final.

## Estado de activación

El servicio existente respondió HTTP 200 en `/healthz` durante esta revisión, pero
el proceso activo ejecutaba `tecnopc-api-avisos.jar`. Por tanto, esa respuesta no
prueba que estén activadas las mejoras de esta entrega.

La entrega nueva está en `java-backend/target/tecnopc-api-resiliencia-1.5.0.jar`.
Los scripts locales la seleccionan con prioridad sobre las anteriores. Para
activarla, cerrar la ventana de conexión y abrir otra vez `ABRIR_CONEXION_TIDB.cmd`,
introducir las credenciales localmente y conectar. Una ventana abierta antes de
crear la entrega conserva la selección anterior. Las aplicaciones de escritorio
1.4.0 son compatibles y no requieren reinstalación para estos cambios.

No se reinició el servicio conectado durante las pruebas ni se hicieron cobros o
escrituras de prueba contra la base real.

## Protección del servidor

| Recurso | Límite inicial |
| --- | --- |
| Solicitudes API en ejecución | 12 por proceso |
| API pública | 6 simultáneas |
| API interna | 4 simultáneas |
| Webhooks de pago | 2 simultáneos en un grupo separado |
| JSON de cualquier ruta API | 64 KiB, incluyendo cuerpos sin longitud declarada y rutas privadas |
| Tomcat | 48 hilos, 200 conexiones, cola de aceptación de 32 |
| Conexión HTTP / keep-alive | 10 s / 5 s; hasta 50 solicitudes por conexión persistente |

El exceso de trabajo se rechaza **antes de autenticación y SQL** con HTTP 503 y
`Retry-After: 2`, en lugar de mantener una cola de operaciones de negocio sin límite.
Las imágenes, CSS, JavaScript y comprobación de vida no consumen esos cupos API.
Los permisos se liberan también cuando hay excepciones. Los límites se aplican por
proceso y deben ajustarse con mediciones; no representan doce clientes máximos.

Se mantuvieron las cuotas públicas por origen, y se agruparon las rutas desconocidas
en una sola clave. Así, cambiar miles de nombres de ruta no agota el mapa de cuotas.
El catálogo admite 300 solicitudes por minuto y origen; el chat, 60; solicitudes y
checkout, 10; seguimiento y recibos, 15; webhooks, 600. Las cabeceras reenviadas del
cliente siguen sin utilizarse como identidad de origen.

## Protección de la base de datos

En el perfil cloud se conservan tres conexiones como máximo. La espera para obtener
una conexión es de tres segundos; la validación usa un segundo. Las consultas de
`JdbcTemplate` tienen límite de cinco segundos y el gestor de transacciones, un
tiempo predeterminado de diez segundos. El conector tiene esperas configuradas de
cinco segundos al conectar y diez al leer del socket, con TLS verificado.

Estos límites actúan en capas distintas; **no deben sumarse ni interpretarse como
un plazo absoluto para cualquier operación**. Su aplicación depende del controlador
JDBC y de la fase de ejecución. Los pagos externos tienen además sus propios límites.

Las conexiones ociosas se retiran después de tres minutos en cloud. Flyway puede
reintentar hasta tres veces la conexión inicial, con intervalo máximo de dos segundos.
No se añadieron reintentos automáticos de ventas, migraciones parcialmente ejecutadas
o actualizaciones de stock.

Los fallos temporales de acceso, espera, conexión o transacción se presentan como
HTTP 503 con mensaje genérico, `no-store` y `Retry-After`. Los conflictos de integridad
se presentan como 409. Una caída de la base durante la autenticación devuelve 503 y
no se contabiliza como contraseña incorrecta. No se exponen URL JDBC ni detalles SQL
en esas respuestas.

Se configuró apagado gradual con hasta 25 segundos. Su efectividad depende de que el
alojamiento envíe una señal compatible y conceda ese tiempo; una terminación forzada
de Windows o un corte eléctrico no equivale a un apagado gradual.

## Catálogo y comprobaciones de salud

`PublicCatalog` comparte una instantánea inmutable durante cinco segundos. Solo una
solicitud actualiza una instantánea vencida; otras reciben un rechazo temporal en
lugar de lanzar consultas idénticas. Tras un fallo, espera cinco segundos antes de
probar nuevamente. No devuelve datos vencidos como si fueran actuales tras ese fallo.

La respuesta pública permite además caché HTTP de cinco segundos. La antigüedad
combinada puede aproximarse a diez segundos. **La caché no autoriza compras:** precios
y existencias de ventas y pagos se siguen comprobando en la base. Un pedido pendiente
no constituye una reserva de stock. Se mantiene el límite existente de 2000 productos;
catálogos mayores necesitan paginación de servidor.

`/livez` indica que el proceso responde, sin consultar la base. `/healthz` comprueba
disponibilidad de datos y comparte el resultado cinco segundos; no ejecuta una consulta
por cada visitante. Una comprobación en curso no genera consultas simultáneas nuevas.
El archivo de Render usa ahora `/livez` para no reiniciar un proceso sano únicamente
por una interrupción de la base. Es una configuración preparada, **no un despliegue
verificado en Render**. La indisponibilidad de datos sigue comunicándose como 503.

## Navegador

- Las lecturas GET coincidentes comparten una petición en curso. El catálogo impide
  cargas simultáneas duplicadas.
- Se respeta `Retry-After` de 429 y 503 durante 1–60 segundos por operación. No se
  programa un bucle de reintentos ni se repiten escrituras automáticamente.
- Las lecturas esperan hasta doce segundos; las escrituras, hasta treinta y cinco
  por defecto. Un timeout de escritura advierte que hay que comprobar el resultado
  antes de repetirlo: no equivale a una cancelación o reversión de la operación.
- Los envíos simultáneos desde un formulario se descartan mientras está ocupado.
  El carrito permanece guardado cuando falla la carga, y funciona en memoria cuando
  el navegador impide utilizar almacenamiento.
- Se muestran sesenta productos por página para acotar el DOM. El chat mantiene
  como máximo ochenta mensajes visuales; el contexto enviado continúa limitado.
- Se aplicó CSP para scripts propios y sin objetos o marcos, además de
  `Referrer-Policy: no-referrer` y restricciones de cámara, micrófono y geolocalización.
  Se permite estilo inline porque la interfaz existente aún lo utiliza; no se
  presenta esa parte como una política CSP completamente estricta.

## Diagnóstico operativo

`GET /api/admin/diagnostico` requiere rol ADMIN y devuelve `no-store`. Incluye entrega,
peticiones activas, pico, completadas, rechazos por capacidad, errores, cuerpos grandes,
latencia media, memoria JVM y conexiones Hikari activas, libres y en espera. No incluye
contraseñas, URL de conexión, contenido de formularios ni referencias de clientes.

Los contadores se reinician con el proceso y no constituyen almacenamiento de métricas
ni alertas. La autenticación administrativa también necesita la base: en una caída
total deben consultarse `/livez`, `/healthz`, los registros y el proveedor, no depender
exclusivamente del diagnóstico privado.

## Evidencia y límites de las pruebas

- **127 pruebas backend aprobadas**, sin fallos, errores u omisiones.
- **33 comprobaciones de navegador aprobadas:** 17 de tienda, 10 de recibos y 6 nuevas
  de resiliencia. Se probaron bajo CSP y sin realizar pagos o envíos reales.
- En una ráfaga local de **32 solicitudes HTTP**, con respuestas deliberadamente
  demoradas: 6 atendidas y 26 rechazadas con 503; pico de ejecución de 6. La portada
  y `/livez` siguieron respondiendo 200. No hubo respuestas 500 en esa prueba.
- Agotar un pool Hikari/H2 de dos conexiones produjo 503 y el servicio volvió a
  responder al liberarlas. La prueba usa espera de 300 ms para acelerar el fallo;
  el perfil cloud usa 3000 ms. No demuestra recuperación de una desconexión física
  del TiDB real.
- Cien lecturas del catálogo dentro del periodo válido generaron una sola consulta.
  Se comprobaron también coalescencia en frío, fallo, pausa y recuperación.
- Dos pagos distintos compitiendo por la última unidad produjeron una venta y un
  pedido en revisión, sin stock negativo. Se conserva la prueba de rollback cuando
  falla una escritura y de notificaciones duplicadas.

Estos son ensayos de mecanismos de protección con H2 y HTTP local. **No certifican
un número de usuarios reales, rendimiento sostenido, límites del plan de TiDB ni SLA.**

Evidencias: [resumen y hashes](resiliencia/evidencias.json),
[regresión backend](../logs/resiliencia-backend-entrega.log),
[tienda](../logs/resiliencia-test-store-final.log),
[recibos](../logs/resiliencia-test-order-receipt-final.log),
[resiliencia de navegador](../logs/resiliencia-test-resilience-final.log),
[SBOM del servidor](resiliencia/backend-sbom.json).
Los scripts reproducibles están en `packaging/test-store.cjs`,
`packaging/test-order-receipt.cjs` y `packaging/test-resilience.cjs`; las pruebas HTTP
del servidor están en `ResilienceHttpTest`.

## Pendientes para una operación pública exigente

1. Medir tráfico sostenido, latencias y recuperación con un entorno separado de TiDB
   y el alojamiento final. Definir objetivos de usuarios, disponibilidad y respuesta.
2. Configurar TLS y proxies confiables, protección de entrada/WAF y defensa frente a
   conexiones lentas y ataques volumétricos. Los límites dentro de Java no sustituyen
   la protección de red ni resuelven por sí solos el problema de NAT o varias réplicas.
3. Acreditar backups cifrados, retención, objetivos de recuperación y una restauración
   real. No se crearon ni se verificaron respaldos de la base actual.
4. Completar idempotencia persistida y conciliación de checkout/ventas: bloquear doble
   envío en el navegador no cubre una respuesta perdida, dos equipos o todos los
   reintentos manuales. No habilitar cobros reales dando ese cierre por demostrado.
5. Revisar consultas sin paginación en módulos internos, planes de ejecución e índices
   con volumen representativo, límites del proveedor y permisos mínimos de usuarios.
6. Persistir métricas, configurar alertas de errores, conexiones y cola de avisos, y
   diseñar alta disponibilidad si el objetivo de servicio la exige.
7. Mantener abiertos los dos avisos de Spring y demás pendientes de
   [la revisión de acceso y dependencias](SEGURIDAD_ACCESO_Y_DEPENDENCIAS.md).

Referencias técnicas: [propiedades de Spring Boot](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html),
[configuración de HikariCP](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby) y
[conector HTTP de Tomcat](https://tomcat.apache.org/tomcat-10.1-doc/config/http.html).
