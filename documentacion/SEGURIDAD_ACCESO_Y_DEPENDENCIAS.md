# Acceso y dependencias — entrega local 1.4.0

Fecha de revisión: 13 de noviembre de 2026, Colombia.

Se implementó protección contra intentos de autenticación repetidos y se revisaron
las dependencias Maven de servidor y clientes. **Esto no cierra todos los hallazgos
de la auditoría ni acredita una puesta en producción.** No se cambiaron claves,
cuentas ni datos de TiDB.

## Protección implementada

`LoginAttemptFilter` actúa antes de verificar HTTP Basic, también cuando alguien
envía credenciales a rutas públicas para intentar eludir la protección.

| Situación | Respuesta |
| --- | --- |
| Cinco fallos de una misma cuenta y origen | Esperas de 2, 4, 8, 16, 32 y hasta 60 segundos al acumular nuevos fallos. |
| Veinte fallos de una cuenta desde varios orígenes | Espera progresiva de 2 hasta 30 segundos. La cuenta se compara sin distinguir mayúsculas. |
| Treinta fallos desde un origen contra distintas cuentas | Espera de 60 segundos para ese origen. |
| Autenticaciones simultáneas | Máximo 8 globales, 2 por origen y 1 por combinación cuenta/origen. Se libera el cupo al terminar de autenticar, antes de la operación comercial. |
| Solicitud limitada | HTTP 429, `Retry-After`, `Cache-Control: no-store` y mensaje genérico. Los clientes muestran el tiempo de espera sin repetir automáticamente la operación. |

Los contadores de cuenta y combinación caducan tras diez minutos sin nuevos fallos;
el contador de origen se reinicia después de sesenta segundos sin fallos. Un acceso
correcto reinicia únicamente su combinación cuenta/origen. Las solicitudes que ya
están bloqueadas no prolongan la espera ni crean registros para nombres nuevos.
No hay bloqueos permanentes de cuentas ni suspensión de hilos para imponer pausas.

El estado está acotado a 8192 registros. Al agotarse, se rechazan temporalmente
nuevas combinaciones y se recupera espacio con registros inactivos de diez minutos.
Se toma la dirección del socket; `X-Forwarded-For` y `Forwarded` no permiten
suplantarla. No se almacenan contraseñas en los contadores.

Crear o restablecer una clave por API ahora valida también el máximo de 72 bytes
UTF-8 de BCrypt. Se conservó el mínimo existente de ocho caracteres para no alterar
el contrato de clientes; esto **no significa que una clave corta o predecible sea
adecuada**. Antes de publicar, sustituir claves de desarrollo por claves únicas y
largas, preferiblemente de quince caracteres o más cuando no exista MFA.

## Dependencias revisadas

| Componente | Antes | Entrega nueva |
| --- | --- | --- |
| Spring Boot | 3.5.7 | 3.5.16 |
| Spring Framework | 6.2.12 | 6.2.19 |
| Spring Security | 6.5.6 | 6.5.11 |
| Tomcat embebido | 10.1.48 | 10.1.61 |
| Jackson Databind, servidor | 2.19.2 | 2.21.8 |
| Jackson Databind, clientes | 2.18.2 | 2.21.8 |
| Log4j API | 2.24.3 | 2.25.5 |
| MySQL Connector/J | 9.4.0 | 9.7.0 |
| JavaFX | 21.0.5 | 21.0.13 |

Se conservaron Flyway Core 11.7.2 y el módulo TiDB 10.26.2; su compatibilidad está
cubierta parcialmente por la regresión existente y requiere la prueba con TiDB real.
El Java incluido sigue siendo Temurin 21.0.13. Las imágenes Docker usan etiquetas
móviles y no se escanearon ni fijaron por digest en este trabajo.

Se generaron cuatro SBOM CycloneDX y se consultaron **56 combinaciones Maven de
nombre y versión en OSV**. La primera consulta encontró tres coincidencias.
Tras actualizar Log4j quedaron **dos avisos en Spring MVC 6.2.19**, conservados en el
informe con su evaluación. No se afirma ausencia de vulnerabilidades por pasar tests.

### Avisos que permanecen abiertos

| Aviso | Evaluación de esta aplicación | Tratamiento pendiente |
| --- | --- | --- |
| [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) / GHSA-pc63-qcmh-9cmg | Requiere uso de `XsltView` y determinadas rutas de renderizado. No se encontraron vistas XSLT; los controladores propios son REST. | Spring publica corrección OSS en 7.0.9 y corrección 6.2.20 con soporte empresarial. Revisar migración compatible o acceso a parches con soporte. |
| [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) / GHSA-j9f9-w8pj-32f8 | Requiere fragmentos de vistas enviados mediante SSE. No se encontraron emisores SSE ni rutas propias que anuncien ese formato. | Misma decisión de actualización; reevaluar antes de introducir SSE o renderizado de vistas. |

Esta evaluación indica que no se observaron los requisitos de explotación en la
configuración revisada; **no elimina los avisos ni sustituye el parche de la biblioteca**.
Una prueba de arranque comprueba la ausencia de resolutores XSLT y de rutas propias
de emisión SSE para detectar una futura ampliación que invalide esta evaluación.

El aviso de Log4j [CVE-2026-49844](https://logging.apache.org/security.html#CVE-2026-49844)
ya no apareció en la segunda consulta tras aplicar 2.25.5. La elección de Tomcat
10.1.61 se contrastó con los [avisos de Apache](https://tomcat.apache.org/security-10).

## Validación y evidencias

- **113 pruebas backend aprobadas**, cero errores, fallos u omisiones, en la regresión final.
- Incluyen ataques distribuidos, rotación de cuentas, concurrencia, memoria acotada,
  cabeceras falsas, expiración, credenciales malformadas, límite previo a la consulta
  de usuarios y validación del máximo de BCrypt.
- Administrador, Ventas y Técnico compilados y abiertos desde los nuevos EXE; se
  generaron capturas sin iniciar sesión ni efectuar operaciones reales.
- Se comprobó el contenido del JAR final: filtro de acceso y versiones nuevas de
  Spring Security, Tomcat, Jackson y Log4j.
- Los clientes se empaquetaron en carpetas nuevas, sin conservar bibliotecas antiguas
  duplicadas. Incluyen icono, interfaz y soporte para la respuesta 429.

Evidencias locales:

- [Resumen de pruebas](../logs/seguridad-pruebas-resumen.json).
- [Regresión completa](../logs/seguridad-backend-final.log).
- [Informe final OSV](seguridad/dependencias-osv.json).
- SBOM de [servidor](seguridad/java-backend-sbom.json), [administrador](seguridad/java-desktop-sbom.json), [ventas](seguridad/java-vendedor-sbom.json) y [técnico](seguridad/java-tecnico-sbom.json).
- [Inventario anterior del servidor](../logs/seguridad-dependencias-antes.json) y [posterior](../logs/seguridad-dependencias-despues.json).
- [Artefactos y hashes de esta entrega](seguridad/artefactos.json).

El alcance del análisis OSV comprende dependencias Maven identificadas por los SBOM;
no incluye un análisis de binarios nativos de JavaFX/JDK, imágenes de contenedor,
dependencias de herramientas de desarrollo, un pentest ni prueba de explotación.

## Instalación y activación

Los paquetes nuevos están en `dist/Seguridad-20261113`. El instalador local
`packaging/instalar-aplicaciones.ps1` los selecciona por defecto y actualiza los
accesos del escritorio y del menú Inicio. Las instalaciones previas se conservan.
Cierra las ventanas antiguas y abre las aplicaciones desde los accesos actualizados.

El servidor se conserva como `java-backend/target/tecnopc-api-seguridad-1.4.0.jar`.
`ABRIR_CONEXION_TIDB.cmd` lo selecciona a través del script de conexión, y
`start-server.ps1` lo elige cuando no se solicita recompilar. **La protección no se
activa en un proceso antiguo que ya estuviera ejecutándose.** Es necesario iniciar
esta entrega con las credenciales de conexión introducidas localmente.

No se verificó un acceso real con TiDB en esta entrega, ni se arrancó un servidor
remoto con credenciales de producción.

## Límites y trabajo pendiente

- Los contadores viven en memoria, se reinician con el proceso y no se comparten
  entre réplicas. Para varias instancias hacen falta límites coordinados.
- Detrás de un proxy o NAT, varias personas pueden compartir origen. Antes de
  publicar, configurar TLS, proxies confiables y cuotas de entrada acordes al tráfico.
- Un ataque distribuido puede provocar esperas temporales en una cuenta; no se
  cambia su estado activo en base ni se establece un bloqueo permanente.
- MFA, recuperación segura de cuentas, rotación de claves conocidas y auditoría de
  accesos requieren trabajo adicional. No se implementó un restablecimiento público.
- Spring Boot 3.5.16 es un avance de parches dentro de la línea actual; no se afirma
  que equivalga a adoptar una línea con mantenimiento OSS vigente. La decisión de
  migración o soporte queda abierta por los dos avisos indicados.
- Continúan pendientes el despliegue real, revisión de imágenes/JDK nativo, TiDB,
  respaldos y los demás hallazgos del informe original.

Referencias: [autenticación HTTP Basic de Spring](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passwords/basic.html),
[guía de autenticación OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html),
[API OSV](https://google.github.io/osv.dev/api/) y
[generación de SBOM CycloneDX](https://cyclonedx.github.io/cyclonedx-maven-plugin/).
