#!/bin/sh
# Build, verify and benchmark the aquifer experiment.
#   run.sh build
#   run.sh verify [harness options]
#   run.sh selftest [harness options]
#   run.sh bench [--forks N] [--threads N] [--out dir] [--solo] [harness options]
#     --solo adds one extra fork per benchmarked variant that runs that variant alone (solo-<VARIANT>-fork-N.json),
#     to rule out bias from several Aquifer classes sharing one call site.
# Env: JAVA (default java), JAVAC (default: javac next to JAVA), SERVER_JAR, BUNDLER_JAR.
# SERVER_JAR and BUNDLER_JAR default to the cubyx-leaf build when this worktree has no leaf-server/build of its own.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)

# The server jar is Java 25 bytecode: take JAVA_HOME if it has a java, else an installed JDK 25, else java on PATH.
find_java() {
  if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    printf '%s\n' "$JAVA_HOME/bin/java"
    return
  fi
  for dir in "/c/Program Files/Eclipse Adoptium"/jdk-25* /usr/lib/jvm/*25*; do
    if [ -x "$dir/bin/java" ]; then
      printf '%s\n' "$dir/bin/java"
      return
    fi
  done
  printf '%s\n' java
}
JAVA=${JAVA:-$(find_java)}
case $JAVA in
  */*) JAVAC=${JAVAC:-$(dirname "$JAVA")/javac} ;;
  *) JAVAC=${JAVAC:-javac} ;;
esac
BUILD=$HERE/java-build
SOURCES=$HERE/java
LEAF_LIBS=leaf-server/build/libs
WORKTREE_ROOT=$(cd "$HERE/../.." && pwd)
FALLBACK_ROOT=E:/DEV/Minecraft/cubyx-leaf

case $(uname -s) in
  MINGW*|MSYS*|CYGWIN*) SEP=';'; native_path() { cygpath -m "$1"; } ;;
  *) SEP=':'; native_path() { printf '%s\n' "$1"; } ;;
esac

die() { echo "run.sh: $*" >&2; exit 1; }

default_jars() {
  if [ -z "$SERVER_JAR" ] && [ ! -f "$WORKTREE_ROOT/$LEAF_LIBS/leaf-server-26.3.local-SNAPSHOT.jar" ]; then
    SERVER_JAR=$FALLBACK_ROOT/$LEAF_LIBS/leaf-server-26.3.local-SNAPSHOT.jar
  fi
  if [ -z "$BUNDLER_JAR" ] && [ ! -f "$WORKTREE_ROOT/$LEAF_LIBS/leaf-bundler-26.3.local-SNAPSHOT.jar" ]; then
    BUNDLER_JAR=$FALLBACK_ROOT/$LEAF_LIBS/leaf-bundler-26.3.local-SNAPSHOT.jar
  fi
  [ -z "$SERVER_JAR" ] || export SERVER_JAR
  [ -z "$BUNDLER_JAR" ] || export BUNDLER_JAR
}

build_java() {
  CP=$(sh "$HERE/classpath.sh")
  mkdir -p "$BUILD"
  # Stale classes of deleted sources must not survive a rebuild.
  find "$BUILD" -name '*.class' -delete
  # An argument file: classpath plus sources exceed the Windows command-line limit.
  ARGS=$BUILD/javac.args
  printf -- '-cp\n"%s"\n' "$CP" > "$ARGS"
  for source in "$SOURCES"/aquifer/*.java "$SOURCES"/aquifer/candidate/*.java "$SOURCES"/aquifer/broken/*.java; do
    [ -f "$source" ] || continue
    printf '"%s"\n' "$(native_path "$source")" >> "$ARGS"
  done
  "$JAVAC" -Xlint:all -Xlint:-processing -d "$(native_path "$BUILD")" "@$(native_path "$ARGS")"
}

run_harness() {
  CP=$(sh "$HERE/classpath.sh")
  "$JAVA" -Xmx2g -cp "$(native_path "$BUILD")$SEP$CP" aquifer.Main "$@"
}

bench() {
  FORKS=1
  OUT=$HERE/results
  THREADS=1
  SOLO=0
  PASS=""
  while [ $# -gt 0 ]; do
    case $1 in
      --forks) FORKS=$2; shift 2 ;;
      --out) OUT=$2; shift 2 ;;
      --threads) THREADS=$2; shift 2 ;;
      --solo) SOLO=1; shift ;;
      *) PASS="$PASS $1"; shift ;;
    esac
  done
  mkdir -p "$OUT"
  i=1
  while [ "$i" -le "$FORKS" ]; do
    echo "=== fork $i of $FORKS ==="
    # shellcheck disable=SC2086
    run_harness --bench --threads "$THREADS" --out "$(native_path "$OUT")/fork-$i.json" $PASS
    i=$((i + 1))
  done
  [ "$SOLO" = 1 ] || return 0
  for name in $(run_harness --list-benchmarked); do
    i=1
    while [ "$i" -le "$FORKS" ]; do
      echo "=== solo $name fork $i of $FORKS ==="
      # shellcheck disable=SC2086
      run_harness --bench --threads "$THREADS" --variants "$name" --out "$(native_path "$OUT")/solo-$name-fork-$i.json" $PASS
      i=$((i + 1))
    done
  done
}

[ $# -ge 1 ] || die "usage: run.sh build | verify [options] | selftest | bench [--forks N] [--threads N] [--out dir] [--solo] [options]"
COMMAND=$1
shift
default_jars
case $COMMAND in
  build) build_java ;;
  verify) run_harness --verify "$@" ;;
  selftest) run_harness --self-test "$@" ;;
  bench) bench "$@" ;;
  *) die "unknown command $COMMAND" ;;
esac
