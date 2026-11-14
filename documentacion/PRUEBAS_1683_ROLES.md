# Pruebas adversas por rol — 13 de noviembre de 2026

Resultado final: **1826 pruebas de backend, 0 fallos, 0 errores y 0 omitidas**. Incluye **1683 casos nuevos identificados individualmente** y 143 pruebas anteriores. Ejecución Maven final con código de salida 0.

| Grupo nuevo | Casos | Qué comprueba |
|---|---:|---|
| RBAC | 192 | Lecturas con administrador, vendedor, técnico y anónimo; encabezados de navegador y acceso a datos privados |
| Productos | 432 | Nueve categorías, creación y edición, campos nulos, longitudes, límites de stock y precisión monetaria |
| Ventas | 200 | Cantidades, falta de stock, nombres Unicode, comillas, texto SQL/HTML, límites y espacios vacíos |
| Taller | 147 | Siete estados por siete destinos, con datos completos, sin autorización o sin diagnóstico/trabajo |
| Reintentos | 120 | Misma clave y mismo contenido, contenido cambiado, venta única, importe y descuento de stock únicos |
| Identificadores | 400 | Identificadores desconocidos, negativos, desbordados, hexadecimales, Unicode, signos y cadenas largas, cruzados con permisos |
| Cuentas inválidas | 64 | Roles, nombres y contraseñas inválidos; límite de 72 bytes UTF-8 |
| Autenticación real | 12 | BCrypt y consulta de usuarios: contraseña correcta, incorrecta, cuenta desactivada y falta de credenciales para cada rol |
| Datos manipulados | 20 | Intentos de imponer precio, IVA, total, stock, vendedor, rol, número y precio de detalle desde el cliente |
| Escrituras y navegador | 96 | Productos, cuentas, ventas y taller; permisos cruzados, Origin, Sec-Fetch-Site y tipo de contenido incorrecto |
| **Total nuevo** | **1683** | Casos distintos; las aserciones y las repeticiones de ejecución no se suman como casos adicionales |

## Fallo encontrado y corregido

`UsuariosController.rolValido` llamaba a `Set.of(...).contains(null)`. Una solicitud del administrador para crear una cuenta con `rol: null` provocaba `NullPointerException` en vez de una respuesta de validación. Se agregó la comprobación de nulo; ahora el caso devuelve HTTP 422 sin insertar cuentas.

Primera matriz de 1587 casos: 1584 correctos y tres fallidos. Uno reveló el defecto anterior; los otros dos eran expectativas incorrectas de la prueba: Spring admite `0x01` como identificador numérico válido. Se corrigió el criterio de prueba para usar la conversión numérica del framework. Después se añadieron 96 casos de escritura y se ejecutó toda la regresión.

La corrección está en el código fuente. **No se reinició ni actualizó el servicio instalado**, que continúa con su versión anterior.

## Alcance y aislamiento

La matriz nueva usa MockMvc con la cadena real de filtros, controladores, SQL y transacciones contra una base H2 exclusiva en memoria. Los casos son secuenciales; las condiciones de negocio y los contadores de admisión se restablecen entre casos para evitar interferencias. La mayoría de los casos inyecta una identidad autenticada de prueba para evaluar autorización; los 12 casos de autenticación usan credenciales y BCrypt reales. Mercado Pago está sustituido por un mock y las notificaciones externas están desactivadas.

Las 143 pruebas anteriores se volvieron a ejecutar en esta misma regresión. Incluyen ráfaga de 32 peticiones sobre servidor HTTP de prueba, agotamiento y recuperación del pool SQL, intentos de contraseña y límites de concurrencia, reintentos simultáneos, notificaciones de pago duplicadas, venta de la última unidad y rollback ante fallos de base de datos. Esas pruebas mantienen sus propios escenarios de concurrencia y contadores.

No se hicieron escrituras ni carga sobre TiDB o el servicio instalado. Los 1683 casos nuevos son pruebas del backend; no son 1683 recorridos visuales ni 1683 usuarios concurrentes. H2 no demuestra por sí sola el comportamiento del motor TiDB, sus bloqueos, red o capacidad real.

Pendientes para producción: respaldo y restauración comprobados, migración y pruebas controladas sobre TiDB, credenciales de Mercado Pago de prueba, conciliación real y mediciones de carga en un entorno equivalente. Pasar estas pruebas no garantiza ausencia de errores ni disponibilidad absoluta.

## Evidencia

- Código: `java-backend/src/test/java/com/tecnopc/admin/AdversarialRolesTest.java`.
- Catálogo final con identificadores y escenarios: `logs/adversarial-catalog-final.json`.
- Resultado individual final, con duración y error: `logs/adversarial-final.jsonl`.
- JUnit de la matriz: `logs/adversarial-final-junit.xml`.
- Recuento por clase de toda la regresión: `logs/adversarial-regresion-resumen.json`.
- Ejecución completa: `logs/pruebas-1683-regresion.log`.
- Evidencia inicial conservada: `logs/adversarial-inicial.jsonl`, `logs/adversarial-catalog-inicial.json`, `logs/adversarial-inicial-junit.txt`.

Para repetir desde la carpeta raíz en PowerShell, con las herramientas actuales del proyecto:

```powershell
$env:JAVA_HOME = Join-Path (Get-Location) '.tools/temurin21/jdk-21.0.13+9'
$repoPath = Join-Path (Get-Location) '.m2'
& 'C:/Program Files/JetBrains/IntelliJ IDEA 2026.2.3/plugins/maven-plugin/lib/maven3/bin/mvn.cmd' -B -q -o -f java-backend/pom.xml "-Dmaven.repo.local=$repoPath" test
```

Los archivos regenerados de la matriz quedan en `java-backend/target/adversarial-*`; los informes JUnit de todas las clases, en `java-backend/target/surefire-reports`. Las copias de evidencia en `logs` corresponden a la ejecución documentada aquí.
