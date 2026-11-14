> Documento historico. Para instalar esta copia usa [INICIO_JAVA.md](packaging/INICIO_JAVA.md): la web y todas las APIs ahora se ejecutan en Spring Boot.

# TecnoPC V8 — Cuentas individuales y roles

## Novedades
- Tabla `usuarios` con cuenta individual, rol, estado activo/inactivo y vínculo opcional a `empleados`.
- Spring Security consulta las credenciales en MySQL y verifica contraseñas con BCrypt.
- Administrador: puede listar y crear cuentas, cambiar rol, activar/desactivar y restablecer contraseñas desde la app JavaFX administrativa.
- Vendedor: utiliza sus credenciales individuales en la app de escritorio JavaFX, y cada venta registra su usuario.
- Técnico: puede consultar solicitudes de servicio técnico mediante `GET /api/tecnico/solicitudes` (sin acceso a ventas).
- No se permite desactivar al último administrador activo ni quitarse el propio rol administrador.

## Instalación
1. Importar `java-backend/sql/database.sql` en MySQL si es una instalación nueva.
2. Ejecutar `java-backend/migration_v7.sql` y después `java-backend/migration_v8.sql` (para bases existentes ejecutar solo las migraciones pendientes).
3. Configurar `DB_URL`, `DB_USER`, `DB_PASSWORD` y, **solo para el primer inicio**, `ADMIN_USER` y `ADMIN_PASSWORD` (12+ caracteres).
4. Ejecutar `mvn spring-boot:run` desde `java-backend/`.
5. Ejecutar `mvn javafx:run` desde `java-desktop/` para crear cuentas; `java-vendedor/` para iniciar sesión como vendedor.
6. Crear una cuenta VENDEDOR para cada empleado. El vendedor inicia sesión con su propia cuenta y las ventas se atribuyen a su usuario.

## Consideraciones importantes
- Esta versión usa HTTP Basic sobre `localhost`; en red o producción requiere HTTPS y controles adicionales de sesión, auditoría, rate limiting y protección de credenciales.
- El backend PHP previo sigue teniendo endpoints administrativos sin protección: **no publicarlos** hasta aplicar autenticación y autorización allí también.
- Las cuentas no deben compartirse. La app de administración usa un diálogo de texto básico para crear/restablecer claves: mejorar a un campo enmascarado antes de usar con personal real.
- La comprobación de último administrador no está protegida contra cambios concurrentes; reforzarla con transacciones antes de producción.
- No se han ejecutado pruebas integradas contra un servidor MySQL real en esta entrega.
