# TecnoPC Java

Servidor: java-backend (Spring Boot). Tienda: frontend, empaquetada y servida
por el mismo JAR. Escritorios: java-desktop (ADMIN), java-vendedor y java-tecnico.
Base de datos: MySQL 8. Requiere JDK 21+; Maven para compilar.

La instalacion, variables, migraciones y comandos vigentes estan en
[INICIO_JAVA.md](packaging/INICIO_JAVA.md). La API publica es /api/public y la
web abre en http://localhost:8081/. No se instala ningun interprete adicional.

Los endpoints internos requieren autenticacion y rol. Las apps nativas deben
usar HTTPS para servidores remotos. Las instrucciones README_V7 a README_V11
son historicas; seguir la guia vigente para instalar esta copia.