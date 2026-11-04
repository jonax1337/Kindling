#!/bin/sh
# Build, verify and benchmark the preview-chunks experiment.
#   run.sh build
#   run.sh verify [options]
#   run.sh bench [--forks 3] [--threads N] [--out-dir dir] [options]   writes <out-dir>/fork-N.json
#   run.sh report [--dir dir]
#   run.sh packets [--world <region dir> --world-seed S --limit N] [options]
#   run.sh plugin          builds plugin/build/preview-probe.jar (stage 2 probe)
#   run.sh server          runs a throwaway test server with the probe in ./server on 127.0.0.1:25999
# Options: --seeds a,b,c --chunks N (per seed) --radius R (chunks) --above N --below N --skin N --grow N
#          --threads N --rounds N --warmup-rounds N
# Env: JAVA (default java), JAVAC (default: javac next to JAVA), SERVER_JAR, BUNDLER_JAR.
# SERVER_JAR and BUNDLER_JAR default to the cubyx-leaf build when this worktree has no leaf-server/build of its own.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
JAVA=${JAVA:-java}
case $JAVA in
  */*) JAVAC=${JAVAC:-$(dirname "$JAVA")/javac} ;;
  *) JAVAC=${JAVAC:-javac} ;;
esac
BUILD=$HERE/java-build
LEAF_LIBS=leaf-server/build/libs
WORKTREE_ROOT=$(cd "$HERE/../.." && pwd)
FALLBACK_ROOT=E:/DEV/Minecraft/cubyx-leaf

case $(uname -s) in
  MINGW*|MSYS*|CYGWIN*) SEP=';'; native_path() { cygpath -m "$1"; } ;;
  *) SEP=':'; native_path() { printf '%s\n' "$1"; } ;;
esac

die() { echo "run.sh: $*" >&2; exit 1; }

default_jars() {
  if [ -z "$SERVER_JAR" ]; then
    SERVER_JAR=$WORKTREE_ROOT/$LEAF_LIBS/leaf-server-26.3.local-SNAPSHOT.jar
    if [ ! -f "$SERVER_JAR" ]; then
      SERVER_JAR=$FALLBACK_ROOT/$LEAF_LIBS/leaf-server-26.3.local-SNAPSHOT.jar
      echo "run.sh: no server jar in this worktree, using $SERVER_JAR" >&2
    fi
  fi
  if [ -z "$BUNDLER_JAR" ]; then
    BUNDLER_JAR=$WORKTREE_ROOT/$LEAF_LIBS/leaf-bundler-26.3.local-SNAPSHOT.jar
    [ -f "$BUNDLER_JAR" ] || BUNDLER_JAR=$FALLBACK_ROOT/$LEAF_LIBS/leaf-bundler-26.3.local-SNAPSHOT.jar
  fi
  export SERVER_JAR BUNDLER_JAR
}

build_java() {
  CP=$(sh "$HERE/classpath.sh")
  rm -rf "$BUILD"
  mkdir -p "$BUILD"
  # An argument file: classpath plus sources exceed the Windows command-line limit.
  ARGS=$BUILD/javac.args
  printf -- '-cp\n"%s"\n' "$CP" > "$ARGS"
  for source in "$HERE"/java/previewchunks/*.java; do
    printf '"%s"\n' "$(native_path "$source")" >> "$ARGS"
  done
  "$JAVAC" -Xlint:all -Xlint:-processing -d "$(native_path "$BUILD")" "@$(native_path "$ARGS")"
}

PLUGIN_BUILD=$HERE/plugin/build
PLUGIN_JAR=$PLUGIN_BUILD/preview-probe.jar

# The stage 2 probe: the harness's generator classes plus plugin/src, as a plugin compiled against Mojang names.
build_plugin() {
  CP=$(sh "$HERE/classpath.sh")
  rm -rf "$PLUGIN_BUILD"
  mkdir -p "$PLUGIN_BUILD/classes"
  ARGS=$PLUGIN_BUILD/javac.args
  printf -- '-cp\n"%s"\n' "$CP" > "$ARGS"
  for source in "$HERE"/java/previewchunks/*.java "$HERE"/plugin/src/previewchunks/*.java; do
    printf '"%s"\n' "$(native_path "$source")" >> "$ARGS"
  done
  "$JAVAC" -Xlint:all -Xlint:-processing -d "$(native_path "$PLUGIN_BUILD/classes")" "@$(native_path "$ARGS")"
  cp "$HERE"/plugin/resources/plugin.yml "$HERE"/plugin/resources/config.yml "$PLUGIN_BUILD/classes/"
  printf 'Manifest-Version: 1.0\npaperweight-mappings-namespace: mojang\n' > "$PLUGIN_BUILD/MANIFEST.MF"
  "${JAR:-jar}" cfm "$(native_path "$PLUGIN_JAR")" "$(native_path "$PLUGIN_BUILD/MANIFEST.MF")" -C "$(native_path "$PLUGIN_BUILD/classes")" .
  echo "built $PLUGIN_JAR"
}

# A throwaway test server in ./server on the bench address, with the probe. Console commands: append lines to server/console.txt.
run_server() {
  PAPERCLIP=${PAPERCLIP_JAR:-$FALLBACK_ROOT/$LEAF_LIBS/leaf-paperclip-26.3.local-SNAPSHOT.jar}
  DIR=$HERE/server
  [ -f "$PLUGIN_JAR" ] || die "build the probe first: run.sh plugin"
  mkdir -p "$DIR/plugins"
  cp "$PLUGIN_JAR" "$DIR/plugins/"
  echo "eula=true" > "$DIR/eula.txt"
  [ -f "$DIR/server.properties" ] || printf '%s\n' server-ip=127.0.0.1 server-port=25999 online-mode=true view-distance=12 \
    simulation-distance=8 level-seed=1 spawn-protection=0 'motd=Kindling preview probe' > "$DIR/server.properties"
  touch "$DIR/console.txt"
  cd "$DIR" && tail -f console.txt | "$JAVA" -Xms2g -Xmx4g -jar "$(native_path "$PAPERCLIP")" --nogui
}

run_harness() {
  CP=$(sh "$HERE/classpath.sh")
  "$JAVA" -Xmx3g -cp "$(native_path "$BUILD")$SEP$CP" previewchunks.Main "$@"
}

bench() {
  FORKS=3
  OUT=$HERE/results
  PASS=""
  while [ $# -gt 0 ]; do
    case $1 in
      --forks) FORKS=$2; shift 2 ;;
      --out-dir) OUT=$2; shift 2 ;;
      *) PASS="$PASS $1"; shift ;;
    esac
  done
  mkdir -p "$OUT"
  i=1
  while [ "$i" -le "$FORKS" ]; do
    echo "=== fork $i of $FORKS ==="
    # shellcheck disable=SC2086
    run_harness bench --out "$(native_path "$OUT")/fork-$i.json" $PASS
    i=$((i + 1))
  done
  run_harness report --dir "$(native_path "$OUT")"
}

[ $# -ge 1 ] || die "usage: run.sh build | verify [options] | bench [--forks N] [--out-dir dir] [options] | report [--dir dir] | packets [options]"
COMMAND=$1
shift
default_jars
case $COMMAND in
  build) build_java ;;
  plugin) build_plugin ;;
  server) run_server ;;
  verify|report|packets) run_harness "$COMMAND" "$@" ;;
  bench) bench "$@" ;;
  *) die "unknown command $COMMAND" ;;
esac
