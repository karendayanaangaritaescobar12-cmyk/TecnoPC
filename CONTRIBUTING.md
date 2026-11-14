# Mantener y modificar TecnoPC

## Preparar el equipo

Instala JDK 21, Maven 3.9 y Node.js 22 (22.13 o posterior).
`java -version`, `mvn -version` y `node --version` deben confirmar las versiones.
Si utilizas nvm, `.nvmrc` selecciona Node 22.

Desde la raíz:

```powershell
npm ci --ignore-scripts
npm run check
mvn -f java-backend/pom.xml test
```

`npm ci` instala las versiones exactas de `package-lock.json`. No editar ese
archivo manualmente. Las herramientas npm son para desarrollo y pruebas;
la tienda desplegada sigue funcionando con un único servidor Java.

## Dónde hacer cada cambio

| Necesidad | Archivo o carpeta |
| --- | --- |
| Estructura y textos de la tienda | `frontend/index.html` |
| Apariencia de la tienda | `frontend/css/styles.css` |
| Catálogo, filtros, carrito y formularios | `frontend/js/app.js` |
| Peticiones HTTP, tiempos de espera y Retry-After | `frontend/js/api.js` |
| Formato de precios y escape de HTML | `frontend/js/display.js` |
| Dominios de pago autorizados | `frontend/js/payment-url.js` |
| Comprobante y estado de pedidos | `frontend/js/order-receipt.js` |
| Reglas de acceso y cabeceras de seguridad | `java-backend/src/main/java/com/tecnopc/admin/SecurityConfig.java` |
| Cambios de base de datos | Nueva migración en `java-backend/src/main/resources/db/migration/` |
| Variables y perfiles | `java-backend/src/main/resources/application*.properties` |
| Dependencias Java | `pom.xml` del módulo correspondiente, ahora con formato legible |

El frontend usa módulos JavaScript nativos con `import` y `export`; cada archivo
declara lo que necesita. Los comprobantes comparten el mismo cliente HTTP que
la tienda. No volver a agregar funciones globales para compartir utilidades.
`window.TecnoPCOrderReceipt` se conserva como puente existente entre la tienda
y el comprobante; no se cambió su contrato en esta reorganización.

Java/Spring Boot mantiene transacciones, permisos, stock y pagos. Los precios
que muestra el navegador no sustituyen las validaciones del servidor.

## Verificar un cambio

```powershell
npm run check
```

Este comando ejecuta ESLint, pruebas de lógica JavaScript y Prettier.
ESLint detecta nombres inexistentes y errores habituales; Prettier evita tener
que discutir o arreglar el formato manualmente. Para corregir el formato:

```powershell
npm run format
```

Para la prueba completa en Windows, tras instalar las herramientas:

```powershell
./packaging/check.ps1
```

El script verifica primero el frontend y después todas las pruebas del backend.
Permite `-Maven`, `-Npm` y `-MavenRepo` cuando las herramientas o dependencias
están en rutas distintas. Se detiene si una comprobación falla.

## Pruebas reales de navegador con datos simulados

Instala una sola vez el navegador de pruebas:

```powershell
npx playwright install chromium
```

En Linux para integración continua: `npx playwright install --with-deps chromium`.

```powershell
npm run test:browser
```

El comando inicia una vista previa en un puerto local disponible, ejecuta las
cuatro suites existentes y detiene la vista previa al terminar. No requiere
TiDB ni un backend iniciado. Las peticiones se simulan; una API no simulada
responde 503 localmente en lugar de llegar al negocio real.

Comprueba tienda, filtros, carrito, diseño móvil, comprobantes, recuperación
de pagos, cuotas temporales y resistencia ante datos maliciosos. Las capturas
y PDF se guardan en `logs/browser-tests/`, fuera de Git. Para incluirlo todo
en Windows usa `./packaging/check.ps1 -Browser`.

Si ya tienes Chrome instalado y prefieres usarlo para estas pruebas:

```powershell
$env:TECNOPC_BROWSER_CHANNEL = 'chrome'
npm run test:browser
```

Después, `Remove-Item Env:TECNOPC_BROWSER_CHANNEL` recupera Chromium por defecto.
La vista previa es una herramienta local, no el servidor de producción.

## Publicar y actualizar dependencias

Antes de subir cambios, ejecutar las comprobaciones y revisar `git diff`.
Los commits deben describir el resultado en español, por ejemplo:
`Conservar el carrito cuando el catálogo pierde conexión`.

GitHub Actions comprueba Java y el frontend en cada push y pull request.
El workflow del frontend incluye las cuatro suites de navegador. Un resultado
verde no sustituye probar cambios de esquema con una copia de MySQL/TiDB.

Dependabot propone actualizaciones semanales para Maven, herramientas npm
y GitHub Actions. Agrupa cambios menores compatibles y limita las propuestas
abiertas. No hay fusión automática: revisar el cambio y las pruebas antes
de aceptarlo, especialmente en actualizaciones mayores.

No editar una migración Flyway aplicada ni actualizar dependencias de producción
solo porque existe una versión nueva. Primero revisar compatibilidad y probar.
La configuración de proxies de Render sigue pendiente de verificación real;
ver `documentacion/PROXIES_HOSTED.md`.
