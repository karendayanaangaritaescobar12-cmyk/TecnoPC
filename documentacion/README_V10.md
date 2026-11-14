> Documento historico. Para instalar esta copia usa [INICIO_JAVA.md](packaging/INICIO_JAVA.md): la web y todas las APIs ahora se ejecutan en Spring Boot.

# TecnoPC V10 — Consulta por código de ingreso

1. Importa `java-backend/sql/database.sql` en MySQL y aplica en orden `java-backend/migration_v7.sql`, `migration_v8.sql`, `migration_v9.sql` y `migration_v10.sql` (si partes de cero).
2. Configura las conexiones MySQL en `backend/config/database.php` y `java-backend/src/main/resources/application.properties`.
3. Ejecuta Spring Boot y la aplicación `java-tecnico` según README_JAVA.md.
4. Al recibir una solicitud desde la app del técnico, el backend genera un código aleatorio `TPC-AAAA-XXXXXXXXXXXX` y lo devuelve a JavaFX para entregar al cliente.
5. El cliente abre la vista **Seguimiento** en la web, introduce su código y consulta `backend/api/seguimiento.php`.
6. La API muestra únicamente equipo, estado, fechas e historial público. No devuelve nombres, teléfonos, diagnósticos, presupuestos ni notas internas.
7. El código `TPC-2026-A7K9M2` del ejemplo anterior tiene 6 caracteres finales y NO corresponde al formato seguro de 12 caracteres usado en V10. Un código válido de demostración sería `TPC-2026-A7K9M2Q4R8TX` (no existe necesariamente en la BD).

**Seguridad y despliegue:** usa HTTPS; la limitación de 15 consultas/minuto/IP es básica y local al servidor PHP. Para producción usa Redis o rate limiting en proxy, registros de abuso y permisos de API; audita también los endpoints PHP administrativos existentes. Las reparaciones previas quedan con código NULL y requieren asignación administrativa. No se han ejecutado pruebas de integración con MySQL, Maven ni XAMPP en este entorno.
