#!/bin/sh
# Build, verify and benchmark the noise-kernel experiment.
#   run.sh build
#   run.sh verify [harness options]
#   run.sh bench [--forks 3] [--threads N] [--out dir] [harness options]
# Env: JAVA (default java), JAVAC (default: javac next to JAVA), SERVER_JAR, BUNDLER_JAR, KN_LIB, NO_RUST=1 to skip cargo.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
JAVA=${JAVA:-java}
case $JAVA in
  */*) JAVAC=${JAVAC:-$(dirname "$JAVA")/javac} ;;
  *) JAVAC=${JAVAC:-javac} ;;
esac
BUILD=$HERE/java-build
RUST=$HERE/rust

case $(uname -s) in
  MINGW*|MSYS*|CYGWIN*) native_path() { cygpath -m "$1"; }; LIB_NAME=kindling_noise.dll ;;
  *) native_path() { printf '%s\n' "$1"; }; LIB_NAME=libkindling_noise.so ;;
esac
KN_LIB=${KN_LIB:-$(native_path "$RUST/target/release/$LIB_NAME")}
export KN_LIB

JVM_FLAGS="--enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector"

die() { echo "run.sh: $*" >&2; exit 1; }

build_rust() {
  if [ -n "$NO_RUST" ]; then
    echo "NO_RUST set: skipping cargo build"
  elif [ -f "$RUST/Cargo.toml" ]; then
    (cd "$RUST" && cargo build --release)
  else
    echo "no $RUST/Cargo.toml: skipping cargo build (native variants need it, or use --no-native)"
  fi
}

build_java() {
  CP=$(sh "$HERE/classpath.sh")
  mkdir -p "$BUILD"
  # shellcheck disable=SC2086
  "$JAVAC" -Xlint:-deprecation -Xlint:-incubating --add-modules jdk.incubator.vector \
    -cp "$CP" -d "$(native_path "$BUILD")" "$HERE"/java/noisekernel/*.java
}

run_harness() {
  CP=$(sh "$HERE/classpath.sh")
  SEP=$(case $(uname -s) in MINGW*|MSYS*|CYGWIN*) printf ';' ;; *) printf ':' ;; esac)
  # shellcheck disable=SC2086
  "$JAVA" $JVM_FLAGS -cp "$(native_path "$BUILD")$SEP$CP" noisekernel.Main "$@"
}

bench() {
  FORKS=3
  OUT=$HERE/results
  THREADS=1
  PASS=""
  while [ $# -gt 0 ]; do
    case $1 in
      --forks) FORKS=$2; shift 2 ;;
      --out) OUT=$2; shift 2 ;;
      --threads) THREADS=$2; shift 2 ;;
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
}

[ $# -ge 1 ] || die "usage: run.sh build | verify [options] | bench [--forks N] [--threads N] [--out dir] [options]"
COMMAND=$1
shift
case $COMMAND in
  build) build_rust; build_java ;;
  verify) run_harness --verify "$@" ;;
  bench) bench "$@" ;;
  *) die "unknown command $COMMAND" ;;
esac
