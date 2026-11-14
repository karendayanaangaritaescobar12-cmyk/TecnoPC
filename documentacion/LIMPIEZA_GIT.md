# Archivos locales fuera del repositorio

Las reglas de `.gitignore` excluyen logs, el estado local de TiDB, PDF de
previsualización, SBOM generados y cuatro copias antiguas de migraciones.
`git rm --cached` los retira del próximo commit sin borrar las copias locales.
Los commits anteriores conservan su contenido; esto no borra el historial.

Desde la raíz del proyecto:

```powershell
git rm -r --cached --ignore-unmatch -- logs/ conexion-tidb-estado.json 'ui-previews/*.pdf' ':(glob)documentacion/**/*sbom*' java-backend/migration_v7.sql java-backend/migration_v8.sql java-backend/migration_v9.sql java-backend/migration_v10.sql
```

Para comprobar que ya no están en el índice, ejecutar el siguiente comando.
No debe imprimir archivos:

```powershell
git ls-files -- logs/ conexion-tidb-estado.json 'ui-previews/*.pdf' ':(glob)documentacion/**/*sbom*' java-backend/migration_v7.sql java-backend/migration_v8.sql java-backend/migration_v9.sql java-backend/migration_v10.sql
```

`git check-ignore -v -- ruta/al/archivo` identifica la regla que impide volver
a agregarlo. Los archivos de `src/main/resources/db/migration/` siguen en Git.

## Confirmación de las migraciones antiguas

Los scripts sueltos v7 a v10 están incluidos en
`java-backend/src/main/resources/db/migration/V1__base.sql`:

| Copia antigua | Operaciones cubiertas en V1 |
| --- | --- |
| migration_v7.sql | Columna compras.vendedor_usuario e índice idx_compras_vendedor |
| migration_v8.sql | Tabla usuarios, sus columnas y restricciones |
| migration_v9.sql | Tablas reparaciones y reparacion_eventos, índices y relaciones |
| migration_v10.sql | Columna reparaciones.codigo_ingreso e índice único |

Se comparó el SQL completo de cada copia con V1, omitiendo únicamente
comentarios, espacios repetidos y `USE tecnopc;`. Los cuatro bloques están
incluidos. Docker copia `java-backend/src`, no estos scripts sueltos, y no se
encontraron referencias operativas a ellos en los scripts de packaging.

Los README de versiones antiguas aún describen importaciones manuales:
son instrucciones históricas. Para la instalación actual se utiliza Flyway;
no ejecutar esas importaciones sobre una base que ya administra Flyway.
No modificar V1, V2 o V3 para esta limpieza ni crear migraciones que repitan
su SQL. La numeración antigua v7-v10 no corresponde a versiones Flyway 7-10.

Las copias locales pueden conservarse; no hace falta borrarlas físicamente.
Antes de eliminarlas, se puede repetir la comparación y ejecutar:

```powershell
mvn -f java-backend/pom.xml "-Dtest=StartupIntegrationTest,TiDBMigrationSupportTest" test
```

`StartupIntegrationTest` arranca Spring con H2 temporal y ejecuta Flyway desde
cero; también prueba la tienda, el administrador y solicitudes de clientes.
`TiDBMigrationSupportTest` comprueba el descubrimiento del soporte de TiDB.
Estas pruebas no se conectan a la base real ni certifican su estado.

Para comprobar una base desplegada, consultar su historial Flyway en modo de
solo lectura y confirmar migraciones exitosas 1, 2 y 3 (no 7, 8, 9 y 10), además
de las tablas/columnas/índices de la tabla anterior. No activar baseline ni
reparar el historial para ocultar una discrepancia.
