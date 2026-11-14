# HSTS detrás del proxy

`SecurityConfig` configura explícitamente `Strict-Transport-Security` con
`max-age=31536000` (un año), únicamente para conexiones que Tomcat reconoce
como seguras. No incluye subdominios ni solicita preload: esas decisiones
requieren garantizar HTTPS también en otros servicios del dominio.

Spring Security ya ofrece HSTS por defecto en HTTPS. Una cabecera ausente
detrás de Render puede deberse a que Java recibe HTTP desde el proxy y no
reconoce que el visitante utilizó HTTPS. Agregar la configuración explícita
no resuelve por sí solo ese reconocimiento.

En Render queda pendiente verificar y autorizar la cadena de proxies del
punto 1 y activar `cloud,hosted,hosted-proxy` con ambas variables requeridas.
Ver `PROXIES_HOSTED.md`. No se debe forzar HSTS para todas las solicitudes ni
confiar directamente en una cabecera enviada por el visitante.

Las pruebas locales son:

```powershell
mvn -f java-backend/pom.xml "-Dtest=SecurityConfigTest,TrustedProxyHttpTest" test
```

`SecurityConfigTest` verifica HSTS en la tienda por HTTPS y en una respuesta
401 por HTTPS. También verifica que HTTP local no emita HSTS aunque lleve
una cabecera `X-Forwarded-Proto: https` sin procesar.
`TrustedProxyHttpTest` verifica con Tomcat real que solo los proxies
autorizados puedan convertir el protocolo reenviado en `isSecure()=true`.
Estos dos conjuntos de pruebas cubren las partes del mecanismo local;
no certifican la respuesta real de Render.

Cuando el despliegue esté listo, comprobar desde PowerShell:

```powershell
curl.exe -sS -D - -o NUL https://TU-SERVICIO.onrender.com/index.html
```

La respuesta HTTPS debe incluir `Strict-Transport-Security: max-age=31536000`.
HSTS indica al navegador que utilice HTTPS en visitas posteriores; no
sustituye un certificado válido ni la redirección inicial de HTTP a HTTPS.
Después de desactivarlo, los navegadores que ya lo recibieron pueden seguir
aplicándolo hasta que expire; para retirarlo se debe servir `max-age=0`
sobre HTTPS.

Referencia: https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html
