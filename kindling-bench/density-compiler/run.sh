#!/bin/sh
# Build, verify and benchmark the density-compiler experiment.
#   run.sh build
#   run.sh verify [harness options]
#   run.sh selftest
#   run.sh dump [harness options]
#   run.sh mutants        (builds deliberately broken copies; each must fail "verify --synthetic-only")
#   run.sh bench [--forks 3] [--threads N] [--out dir] [--paper-solo] [harness options]
#     --paper-solo adds forks that run PAPER alone (paper-solo-fork-N.json), to compare with PAPER in the mixed forks
# Env: JAVA (default java), JAVAC (default: javac next to JAVA), SERVER_JAR, BUNDLER_JAR,
#      SOURCE_DIR and CLASSES_DIR (build another source tree into another class directory; used by mutants).
# SERVER_JAR and BUNDLER_JAR default to the cubyx-leaf build when this worktree has no leaf-server/build of its own.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
JAVA=${JAVA:-java}
case $JAVA in
  */*) JAVAC=${JAVAC:-$(dirname "$JAVA")/javac} ;;
  *) JAVAC=${JAVAC:-javac} ;;
esac
BUILD=${CLASSES_DIR:-$HERE/java-build}
SOURCES=${SOURCE_DIR:-$HERE/java}
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
  # An argument file: classpath plus sources exceed the Windows command-line limit.
  ARGS=$BUILD/javac.args
  printf -- '-cp\n"%s"\n' "$CP" > "$ARGS"
  for source in "$SOURCES"/densitycompiler/*.java "$SOURCES"/densitycompiler/eval/*.java; do
    printf '"%s"\n' "$(native_path "$source")" >> "$ARGS"
  done
  "$JAVAC" -Xlint:all -Xlint:-processing -d "$(native_path "$BUILD")" "@$(native_path "$ARGS")"
}

run_harness() {
  CP=$(sh "$HERE/classpath.sh")
  "$JAVA" -Xmx2g -cp "$(native_path "$BUILD")$SEP$CP" densitycompiler.Main "$@"
}

bench() {
  FORKS=3
  OUT=$HERE/results
  THREADS=1
  SOLO=0
  PASS=""
  while [ $# -gt 0 ]; do
    case $1 in
      --forks) FORKS=$2; shift 2 ;;
      --out) OUT=$2; shift 2 ;;
      --threads) THREADS=$2; shift 2 ;;
      --paper-solo) SOLO=1; shift ;;
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
  if [ "$SOLO" = 1 ]; then
    i=1
    while [ "$i" -le "$FORKS" ]; do
      echo "=== paper-solo fork $i of $FORKS ==="
      # shellcheck disable=SC2086
      run_harness --bench --threads "$THREADS" --variants PAPER --out "$(native_path "$OUT")/paper-solo-fork-$i.json" $PASS
      i=$((i + 1))
    done
  fi
}

[ $# -ge 1 ] || die "usage: run.sh build | verify [options] | selftest | dump [options] | mutants | bench [--forks N] [--threads N] [--out dir] [--paper-solo] [options]"
COMMAND=$1
shift
default_jars
case $COMMAND in
  build) build_java ;;
  verify) run_harness --verify "$@" ;;
  selftest) run_harness --self-test "$@" ;;
  dump) run_harness --dump "$@" ;;
  mutants) sh "$HERE/mutants.sh" ;;
  bench) bench "$@" ;;
  *) die "unknown command $COMMAND" ;;
esac
