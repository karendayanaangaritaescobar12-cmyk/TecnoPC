# Interfaces renovadas

Se aplicó la referencia visual a la tienda y las tres aplicaciones JavaFX.
Administrador usa azul oscuro, ventas verde azulado y taller violeta. Comparten
navegación lateral, formularios legibles, búsquedas, tablas y mensajes de conexión.

La tienda incluye ilustraciones locales, catálogo con filtros, carrito,
configurador, seguimiento y asistente de navegación. Los datos comerciales se
obtienen del backend; las promociones sin datos no inventan descuentos.

Administrador muestra resumen, ventas, usuarios, reparaciones y avisos.
Ventas permite preparar el carrito, calcular IVA, registrar ventas y preparar
pedidos. Taller permite recibir equipos, consultar códigos y gestionar las
transiciones permitidas de reparación. Al salir se limpian claves y datos visibles.

## Abrir en Windows

Los accesos directos del escritorio apuntan a las nuevas aplicaciones en
`dist/Interfaz-Mejorada`. Cada carpeta contiene su EXE y Java incluido.
Conservar la carpeta completa al copiarla a otro equipo. Son aplicaciones
portables, no instaladores.

El backend debe estar iniciado. Ejecutar `ABRIR_CONEXION_TIDB.cmd` desde la raíz,
introducir las credenciales en la ventana y abrir http://localhost:8081/.
El JAR del backend ya contiene la tienda renovada. Para acceso desde otro equipo,
configurar la dirección HTTPS de un backend publicado.

## Validación

- 78 pruebas del backend aprobadas.
- 16 comprobaciones web aprobadas, incluidas vistas móviles y escape de texto.
- 15 comprobaciones JavaFX aprobadas: navegación, carrito, estados y salida.

Las pruebas gráficas usan datos simulados; no registraron ventas ni enviaron
avisos reales. JavaFX se renderizó sin pantalla con Monocle; queda la comprobación
manual desde la sesión habitual de Windows. Las capturas en `ui-previews` que
incluyen `pruebas` en su nombre muestran datos simulados.

Esta actualización no publica el backend en Internet ni activa proveedores de
correo o WhatsApp. El servidor estaba detenido al terminar la verificación.
