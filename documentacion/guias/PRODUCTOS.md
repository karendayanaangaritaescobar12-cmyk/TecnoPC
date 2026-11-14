# Gestionar productos

Abrir el ejecutable actualizado de Administrador, iniciar sesión y entrar en
Inventario. Allí aparecen Agregar producto, Editar producto y Actualizar inventario.

El formulario guarda nombre, categoría, SKU opcional, especificaciones, stock,
stock mínimo, costo y precio sin IVA en COP. Usar punto para decimales, sin
separadores de miles. Las categorías de componentes aparecen en Armar mi PC
si tienen existencias. Actualizar la tienda para consultar el catálogo guardado.

La API permite estas operaciones solo al administrador. Rechaza SKU duplicado,
datos inválidos y modificaciones de stock si las existencias cambiaron desde
que se abrió el formulario. En ese caso actualizar el inventario antes de editar.
No borra productos vinculados a ventas. Esta entrega no incluye subida de fotos.

Reiniciar el backend con ABRIR_CONEXION_TIDB.cmd para cargar las nuevas rutas.
El asistente utiliza target/tecnopc-api-productos.jar si existe; es el backend
actualizado empaquetado por separado para evitar un bloqueo del archivo anterior.

Validación: 82 pruebas backend y 29 comprobaciones JavaFX aprobadas. Los datos
usados en pruebas son simulados o de H2; no se crearon productos ficticios en TiDB.
