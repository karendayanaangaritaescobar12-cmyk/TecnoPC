#!/usr/bin/env bash
set -euo pipefail
# Run on the OS you are packaging for. Requires JDK 21 with jpackage and Maven.
APP="${1:-}"; TYPE="${2:-app-image}"
case "$APP" in
  admin) DIR=java-desktop; MAIN=com.tecnopc.desktop.Launcher; NAME=TecnoPC-Administrador ;;
  ventas) DIR=java-vendedor; MAIN=com.tecnopc.vendedor.Launcher; NAME=TecnoPC-Ventas ;;
  tecnico) DIR=java-tecnico; MAIN=com.tecnopc.tecnico.Launcher; NAME=TecnoPC-Tecnico ;;
  *) echo "Uso: bash packaging/build.sh admin|ventas|tecnico [app-image|exe|msi|deb|rpm|dmg|pkg]"; exit 2 ;;
esac
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mvn -f "$ROOT/desktop-common/pom.xml" -DskipTests install
cd "$ROOT/$DIR"
mvn -DskipTests package dependency:copy-dependencies -DoutputDirectory=target/package-input/lib
mkdir -p target/package-input
cp "target/$(find target -maxdepth 1 -name '*.jar' -printf '%f\n' | head -1)" target/package-input/app.jar
mkdir -p "$ROOT/dist"
# jpackage includes the runtime; the JavaFX platform dependencies must match this OS/CPU.
jpackage --type "$TYPE" --name "$NAME" --app-version 1.1.0 --input target/package-input --main-jar app.jar --main-class "$MAIN" --dest "$ROOT/dist" --java-options '-Dfile.encoding=UTF-8'
echo "Salida: $ROOT/dist"
