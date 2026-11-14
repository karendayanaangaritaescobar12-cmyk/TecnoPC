> Documento historico. Para instalar esta copia usa [INICIO_JAVA.md](packaging/INICIO_JAVA.md): la web y todas las APIs ahora se ejecutan en Spring Boot.

# TecnoPC V11 — Empaquetado multiplataforma (fuentes)

Este paquete conserva TecnoPC V10 e incorpora scripts de empaquetado para **Administrador**, **Ventas** y **Técnico**. No contiene instaladores precompilados ni se ha validado su ejecución en los tres sistemas.

## Requisitos
- JDK 21 completo con `jpackage` (verificar `java -version` y `jpackage --version`).
- Maven 3.9+, conexión a internet para resolver dependencias en la primera compilación.
- Windows: herramientas WiX si el tipo de paquete elegido lo requiere; Linux: utilidades nativas `dpkg-deb`/`rpmbuild`; macOS: Xcode Command Line Tools según paquete.
- Spring Boot y MySQL instalados y configurados por separado. **No** exponer MySQL a las estaciones cliente; las apps hablan con Spring Boot.

## Generar aplicaciones
En **Windows PowerShell** desde la raíz:
```
./packaging/build.ps1 -App ventas -Type app-image
./packaging/build.ps1 -App admin -Type exe
./packaging/build.ps1 -App tecnico -Type msi
```
En **Linux**:
```
bash packaging/build.sh ventas app-image
bash packaging/build.sh admin deb
bash packaging/build.sh tecnico rpm
```
En **macOS**:
```
bash packaging/build.sh ventas app-image
bash packaging/build.sh admin dmg
bash packaging/build.sh tecnico pkg
```
`dist/` contiene las salidas. Cada instalador se construye **en su sistema operativo de destino**, con JDK/JavaFX acordes a la arquitectura (x64/aarch64).

## Servidor local o remoto
Las apps conservan el campo editable **Servidor**; su valor predeterminado es `http://localhost:8081`. Para cambiarlo al iniciar, establecer `TECNOPC_API_URL=http://192.168.1.20:8081` o pasar a Java `-Dtecnopc.api.url=...`. Para producción usar HTTPS con un certificado válido, reglas de firewall y autenticación segura. **No usar HTTP Basic sin TLS fuera de localhost.**

## Límites y pruebas pendientes
- Los scripts preparan paquetes, pero requieren validación real en Windows, Linux y macOS; revisar el contenido de `target/package-input/lib` y dependencias JavaFX nativas antes de distribuir.
- La arquitectura anterior usa Basic Auth y conserva endpoints PHP que necesitan revisión de permisos antes de uso real.
- macOS: firma/notarización y empaquetado separado para Intel/Apple Silicon; Windows: firma de código recomendada.
- Probar inicio de sesión por rol, ventas, recepción, seguimiento de códigos, conectividad y funcionamiento sin herramientas de desarrollo.
- Los scripts no instalan Spring Boot como servicio ni MySQL: configurar el servidor central por separado.
