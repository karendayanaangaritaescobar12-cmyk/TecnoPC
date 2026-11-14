# Ejecutables Windows

Se generaron las tres aplicaciones portables x64 en dist, cada una con su
runtime Java y dependencias incluidas. Hay accesos directos en el escritorio.

- dist/Interfaz-Mejorada/TecnoPC-Administrador/TecnoPC-Administrador.exe
- dist/Interfaz-Mejorada/TecnoPC-Ventas/TecnoPC-Ventas.exe
- dist/Interfaz-Mejorada/TecnoPC-Tecnico/TecnoPC-Tecnico.exe

No son instaladores: conservar las carpetas app y runtime junto al EXE.
Para trasladar una aplicacion, copiar su carpeta completa. La base TiDB no
requiere instalar MySQL en cada equipo y las claves de la base no se incluyen.

Las aplicaciones apuntan a http://localhost:8081. Para otro equipo, configurar
la URL HTTPS del backend publicado en el campo Servidor. No usar localhost
para conectarse desde un equipo diferente al que ejecuta el backend.

El empaquetado usa una clase Launcher que no hereda de JavaFX Application,
evitando el error del lanzador Java sobre componentes JavaFX ausentes. El
runtime incluye java.desktop, java.net.http, java.sql y soporte TLS EC.

Reproducir con JDK 21 completo y Maven:

```powershell
./packaging/build.ps1 -App admin -Type app-image -JdkHome '<ruta-JDK>'
./packaging/build.ps1 -App ventas -Type app-image -JdkHome '<ruta-JDK>'
./packaging/build.ps1 -App tecnico -Type app-image -JdkHome '<ruta-JDK>'
```

El script acepta -Maven, -MavenRepo, -ApiUrl y -Destination. No sobrescribe una
carpeta app-image existente; elegir otra salida para conservar versiones previas.
Para generar instaladores con -Type exe o msi hace falta WiX compatible con el
JDK utilizado. No se instalaron herramientas WiX ni se generaron instaladores.

Validado: compilacion Maven de los tres proyectos, jpackage terminado con exito,
EXE nativos creados, JAR/dependencias y runtime incluidos. Temurin se descargo
desde la API oficial de Adoptium y se verifico contra su SHA256 publicado.

Las escenas JavaFX se renderizaron con Monocle en modo sin pantalla: pasaron
15 comprobaciones de interfaz usando un servidor simulado. Hace falta comprobar
la apertura desde la sesion normal de Windows. No se introdujeron credenciales
reales ni se modificaron datos del negocio en estas pruebas.
