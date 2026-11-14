# Asesor de TecnoPC

El chat usa reglas de negocio y consultas al inventario público. No usa un
modelo externo ni se ha entrenado un modelo de inteligencia artificial.

Pregunta por uso y presupuesto, reconoce estudio/oficina, programación, juegos
y diseño/edición, distingue portátil y escritorio y orienta sobre componentes.
Acepta presupuestos como 2500000, 2.500.000, 2,5 millones y 500 mil.
Propone hasta tres productos disponibles de la categoría dentro del presupuesto,
con IVA. Para armado enumera candidatos de menor precio por componente e indica
faltantes, presupuesto excedido y revisión de compatibilidad necesaria. No
garantiza FPS, compatibilidad, financiación ni características no registradas.

También orienta sobre recepción y revisión técnica, seguimiento, garantía,
promociones, contacto, pagos e IVA. No inventa horarios, descuentos, diagnósticos,
precios de mano de obra ni plazos. No consulta ventas ni datos privados.

Conserva hasta seis mensajes previos del cliente en memoria de la página; se
envían con la consulta para interpretar respuestas como «para jugar» o «2 millones».
No crea un historial persistente ni guarda conversaciones en la base de datos.
Recargar la página reinicia ese contexto. No enviar contraseñas o datos bancarios.

La espera del chat es de hasta 35 segundos y sus errores aclaran que no se pudo
comprobar el catálogo. Se ajustó la validación y renovación de conexiones TiDB
para reducir problemas con conexiones inactivas; esto no garantiza disponibilidad.

Reiniciar mediante ABRIR_CONEXION_TIDB.cmd carga tecnopc-api-asesor.jar, que
contiene API y tienda actualizadas. No se modifica el servidor que ya está activo
hasta realizar ese reinicio con las credenciales de conexión.

Para mejores recomendaciones comerciales hay que cargar productos reales, stock,
precios y especificaciones claras. Faltan reglas estructuradas de compatibilidad
y atributos de rendimiento antes de automatizar recomendaciones técnicas finales.
