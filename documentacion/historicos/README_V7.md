> Documento historico. Para instalar esta copia usa [INICIO_JAVA.md](packaging/INICIO_JAVA.md): la web y todas las APIs ahora se ejecutan en Spring Boot.

# TecnoPC V7 — Roles y aplicación de vendedor

## Cambios
- Spring Boot: cuentas de **ADMIN** y **VENDEDOR** configuradas mediante variables de entorno.
- `/api/admin/**`: solo administrador. `/api/vendedor/**`: vendedor o administrador.
- JavaFX: nueva aplicación `java-vendedor/`, separada de `java-desktop/` (administrador).
- Vendedor: consultar catálogo, crear carrito, registrar ventas con transacción MySQL y ver solo sus ventas.
- Cada venta guarda el usuario vendedor, valida stock con `SELECT ... FOR UPDATE`, registra detalles y descuenta inventario en una transacción.

## Requisitos y ejecución
1. Instala Java 21, Maven y MySQL 8. Importa `java-backend/sql/database.sql` si es una instalación nueva.
2. **Si actualizas una base V6**, ejecuta `java-backend/migration_v7.sql` **una sola vez**. Para instalaciones nuevas también debes ejecutar esta migración después de `database.sql`.
3. Configura las variables de entorno `ADMIN_USER`, `ADMIN_PASSWORD`, `VENDEDOR_USER`, `VENDEDOR_PASSWORD`, `DB_URL`, `DB_USER`, `DB_PASSWORD`. Las contraseñas deben tener 12 caracteres o más y los usuarios deben ser diferentes.
4. En `java-backend/`, ejecuta `mvn spring-boot:run`.
5. En `java-vendedor/`, ejecuta `mvn javafx:run`. Introduce la URL del servidor (por defecto `http://localhost:8081`) y las credenciales del vendedor.
6. El administrador puede seguir usando `java-desktop/` con `mvn javafx:run`.

## Límites de esta versión
- Las cuentas son **dos credenciales de demostración en memoria**, no un sistema de usuarios persistentes por empleado. Para uso real hay que agregar usuarios con contraseñas hasheadas, roles en MySQL, recuperación de credenciales, auditoría y desactivación.
- **No se ha ejecutado una prueba integral** con Java, Maven, PHP y MySQL en este entorno.
- HTTP Basic está pensado para desarrollo local. En red o producción se necesita HTTPS, sesiones/tokens, CSRF según el método de autenticación y controles de acceso en los endpoints PHP heredados (que todavía no están protegidos).
- La app de vendedor es un proyecto JavaFX ejecutable con Maven, todavía no un instalador `.exe`.
- Los importes son estimados en el cliente; el backend vuelve a calcularlos desde la base de datos.
- El estado `registrada` indica registro en el sistema, **no** pago confirmado.
