#!/bin/sh
# Prints the classpath for the noise-kernel harness: server jar + the libraries
# extracted once from the bundler jar. Env: SERVER_JAR, BUNDLER_JAR.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SERVER_JAR=${SERVER_JAR:-$ROOT/leaf-server/build/libs/leaf-server-26.3.local-SNAPSHOT.jar}
BUNDLER_JAR=${BUNDLER_JAR:-$ROOT/leaf-server/build/libs/leaf-bundler-26.3.local-SNAPSHOT.jar}
LIBS=$HERE/.cache/libs
STAMP=$LIBS/.extracted

die() { echo "classpath.sh: $*" >&2; exit 1; }

[ -f "$SERVER_JAR" ] || die "SERVER_JAR not found: $SERVER_JAR"
[ -f "$BUNDLER_JAR" ] || die "BUNDLER_JAR not found: $BUNDLER_JAR"

case $(uname -s) in
  MINGW*|MSYS*|CYGWIN*) SEP=';'; native_path() { cygpath -m "$1"; } ;;
  *) SEP=':'; native_path() { printf '%s\n' "$1"; } ;;
esac

extract_libraries() {
  mkdir -p "$LIBS"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$BUNDLER_JAR" -d "$LIBS" -x 'io/*' 'paperclip/*' 'META-INF/versions/*'
  else
    (cd "$LIBS" && jar xf "$BUNDLER_JAR" META-INF/libraries)
  fi
  wc -c < "$BUNDLER_JAR" > "$STAMP"
}

if [ ! -f "$STAMP" ] || [ "$(cat "$STAMP")" != "$(wc -c < "$BUNDLER_JAR")" ]; then
  extract_libraries
fi

CP=$(native_path "$SERVER_JAR")
for jar in $(find "$LIBS/META-INF/libraries" -name '*.jar' | sort); do
  CP=$CP$SEP$(native_path "$jar")
done
printf '%s\n' "$CP"
