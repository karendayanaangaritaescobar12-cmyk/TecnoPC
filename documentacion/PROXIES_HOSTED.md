# IP del cliente detrás de un proxy

La configuración local y los perfiles actuales `cloud,hosted` conservan su
comportamiento. El perfil adicional `hosted-proxy` prepara el uso de
`RemoteIpValve` de Tomcat, sin leer cabeceras directamente en los filtros.
No se ha activado en Render: la corrección en producción sigue pendiente.

## Activación después de verificar la infraestructura

1. Confirmar con el proveedor las direcciones/redes de los proxies de entrada,
   todos los intermediarios de la cadena y cómo agregan o reemplazan
   `X-Forwarded-For` y `X-Forwarded-Proto`. Las IP de salida del servicio no
   sustituyen esa información.
2. Configurar `RENDER_INTERNAL_PROXIES_REGEX` y
   `RENDER_TRUSTED_PROXIES_REGEX` con expresiones regulares Java que coincidan
   únicamente con esos proxies. No son listas CIDR. No usar `.*`, valores
   vacíos ni todas las redes privadas como sustituto de los datos verificados.
   Si no hay proxies de una categoría, usar `(?!)` para no aceptar ninguno.
3. Garantizar que clientes externos no puedan llegar directamente al puerto
   de Java eludiendo la cadena autorizada.
4. Activar `SPRING_PROFILES_ACTIVE=cloud,hosted,hosted-proxy` y comprobar la
   aplicación antes de dar por corregido el problema en producción.

El perfil requiere explícitamente ambas variables. Tomcat toma la conexión
inmediata como punto de partida y recorre las direcciones reenviadas desde la
derecha hasta la primera que no sea un proxy autorizado. Los filtros de
solicitudes públicas y de login siguen usando `request.getRemoteAddr()`;
Tomcat entrega la dirección resuelta y también adapta `isSecure()`.
Usuarios que comparten una IP pública (por ejemplo, una misma red Wi-Fi)
siguen compartiendo el presupuesto por IP.

## Pruebas locales sin Render ni base de datos

Desde la raíz del proyecto, con Java 21 y Maven disponibles:

```powershell
mvn -f java-backend/pom.xml "-Dtest=TrustedProxyHttpTest,PublicRequestFilterTest,CloudConfigurationTest" test
```

`TrustedProxyHttpTest` inicia un Tomcat real en un puerto local aleatorio.
Usa IP de documentación y un servlet de prueba que no se incluye en la
aplicación de producción. No crea pagos ni consulta una base de datos.

- Un proxy autorizado permite distinguir dos clientes y reconocer HTTPS.
- Cambiar un prefijo falso de `X-Forwarded-For` no restablece la cuota.
- Una conexión no autorizada no puede modificar la IP ni simular HTTPS.
- La solicitud número 11 recibe 429; un segundo cliente conserva su cuota.

Estas pruebas validan el mecanismo de Tomcat y el filtro, pero no verifican
las direcciones ni el comportamiento del proxy real de Render.

Referencias:
- https://docs.spring.io/spring-boot/3.5/how-to/webserver.html
- https://tomcat.apache.org/tomcat-10.1-doc/config/valve.html#Remote_IP_Valve
- https://render.com/articles/how-render-handles-ddos-attacks
