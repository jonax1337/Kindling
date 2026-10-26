#!/usr/bin/env bash
# The built-in pregenerator on a stand-in for the VPS (8 logical CPUs): once alone with one player standing at the spawn
# (throughput, tick times), once while five players fly outward at elytra speed (does it get in their way?).
# Reads the progress from pregeneration.properties in the world folder of each run.
# Usage: pregen.sh <fork.jar> <label prefix>
set -euo pipefail
cd "$(dirname "$0")"

jar=$1
prefix=$2
plugin=build/libs/kindling-bench.jar
jvm="-XX:ActiveProcessorCount=8 -XX:+UseCompactObjectHeaders -Xms2G -Xmx2G"
alone="players=1,blocks-per-tick=0,warmup-seconds=20,travel-seconds=240,mobs-per-player=0,settle-seconds=2,steady-seconds=5,idle-seconds=3"
flight="players=5,blocks-per-tick=1.7,warmup-seconds=20,travel-seconds=90,mobs-per-player=0,settle-seconds=2,steady-seconds=5,idle-seconds=3"

run() {
    local label=$1 scenario=$2
    python -I run_bench.py --plugin "$plugin" --label "$prefix-$label" --scenario "$scenario" --jar "$jar" --cpus 8 \
        "--jvm=$jvm" --overlay overlays/pregen-w5 > "runs/$prefix-$label.log" 2>&1 || echo "FAILED $label (see runs/$prefix-$label.log)"
    grep -h "next\|state" "runs/$prefix-$label-1/world/pregeneration.properties" || echo "no progress file for $label"
    echo "done $label"
}

run alone "$alone"
run flight "$flight"
