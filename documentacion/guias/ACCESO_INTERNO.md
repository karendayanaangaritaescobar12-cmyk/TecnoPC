# Acceso interno

Las tres aplicaciones muestran solamente el acceso hasta validar usuario,
contraseña y permisos con el backend. Una contraseña incorrecta mantiene
ocultos navegación y contenido. Salir o editar las credenciales bloquea de nuevo.
La tienda pública continúa disponible para los clientes.

Por petición del propietario, la longitud mínima de contraseña es de ocho
caracteres. Las contraseñas continúan almacenándose como hashes BCrypt.

Se preparó un cambio local de las tres claves en un archivo ignorado por Git
en `.local-secrets`. No se incorpora a ejecutables, fuentes distribuibles ni ZIP.
Al iniciar con `ABRIR_CONEXION_TIDB.cmd`, el servidor procesa los hashes en una
transacción y elimina el archivo al terminar. Conserva los nombres de cuentas
existentes. Si falta una cuenta, crea administrador, vendedor o tecnico.
Si existen varias cuentas del mismo rol, se detiene sin aplicar cambios para
evitar cambiar claves a personas distintas por error.

La actualización de TiDB está pendiente hasta iniciar el servidor con conexión.
Pruebas: 79 del backend y 24 comprobaciones JavaFX, incluyendo acceso rechazado,
panel oculto antes del acceso y desbloqueo con credenciales válidas simuladas.
