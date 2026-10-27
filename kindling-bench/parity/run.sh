#!/usr/bin/env bash
# The parity check: runs the bases scenario in its CI size against a server jar and compares the counters with
# bases-ci.json. Usage: [PARITY_RUNS=n] run.sh <server.jar> [--update]
# Needs the bench plugin (./gradlew :kindling-bench:jar) and Python with psutil. --update rewrites the golden file.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
bench=$(dirname "$here")
jar=$1
shift

# One player hovering over two plots: every contraption type, a few chunks to generate. Brewing stands finish their
# brews after about 100 s, so warm-up plus measurement cover 110 s of server time. Counted in ticks, not seconds.
scenario="type=bases,players=1,plots-per-player=2,warmup-seconds=20,steady-seconds=90"
label=parity-ci

mkdir -p "$bench/runs"
python -I "$bench/run_bench.py" --jar "$jar" --plugin "$bench/build/libs/kindling-bench.jar" --label "$label" \
    --heap 2G --repeat "${PARITY_RUNS:-1}" --scenario "$scenario" > "$bench/runs/$label.log" 2>&1 \
    || { tail -n 40 "$bench/runs/$label.log"; exit 1; }
python -I "$here/check.py" "$bench/runs/$label.json" "$@"
