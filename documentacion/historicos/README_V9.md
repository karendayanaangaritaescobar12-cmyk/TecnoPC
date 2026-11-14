> Documento historico. Para instalar esta copia usa [INICIO_JAVA.md](packaging/INICIO_JAVA.md): la web y todas las APIs ahora se ejecutan en Spring Boot.

# TecnoPC V9 — Aplicación de escritorio del técnico

Se conserva todo el proyecto V8 y se incorpora `java-tecnico/` con JavaFX y endpoints de Spring Boot.

## Funciones
- Consultar solicitudes de servicio técnico registradas desde la tienda.
- Recibir un equipo y registrar su marca, modelo, serie y accesorios.
- Registrar diagnóstico, presupuesto y autorización del cliente.
- Cambiar estado siguiendo: recibido → diagnóstico → esperando autorización → en reparación → listo → entregado. Se permite cancelación antes de la entrega.
- Registrar historial de cambios, usuario técnico y fecha de entrega.
- Impedir pasar a reparación sin diagnóstico, presupuesto y autorización registrada.

## Instalación
1. Importar `java-backend/sql/database.sql`, después `java-backend/migration_v7.sql`, `migration_v8.sql` y **`migration_v9.sql`**, en ese orden, para una base nueva. Para actualizar desde V8, ejecutar solo `migration_v9.sql`.
2. Configurar MySQL y arrancar Spring Boot desde `java-backend/` con `mvn spring-boot:run` (Java 21, Maven y MySQL).
3. Crear una cuenta de rol `TECNICO` desde el panel administrador V8.
4. Desde `java-tecnico/`, ejecutar `mvn javafx:run`.
5. Iniciar sesión con el usuario del técnico y pulsar «Conectar / actualizar».

## Seguridad y limitaciones
- Las rutas `/api/tecnico/**` están protegidas por los roles `TECNICO` o `ADMIN` de Spring Security.
- Se usa autenticación HTTP Basic: para uso remoto se necesita HTTPS; no se deben exponer estas APIs por HTTP público.
- La autorización del cliente se registra como una marca introducida por el técnico, **no constituye una firma ni una prueba documental de consentimiento**.
- Los técnicos pueden consultar y actualizar las reparaciones del taller; aún no hay asignación exclusiva por técnico.
- La aplicación es código fuente JavaFX, no un instalador EXE.
- Falta ejecutar pruebas de integración con MySQL y revisar seguridad del sistema PHP antes de producción.
