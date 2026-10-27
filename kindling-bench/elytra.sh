#!/usr/bin/env bash
# Five players fly outward in five directions at elytra speed (1.7 blocks/tick, about 34 blocks/s) on a stand-in for the
# VPS (8 logical CPUs here, 8 vCPUs there); measures whether chunks stay ahead of them (chunk_lead) and the tick times.
# Usage: elytra.sh <fork.jar> <upstream.jar> <label prefix>
set -euo pipefail
cd "$(dirname "$0")"

fork=$1
upstream=$2
prefix=$3
plugin=build/libs/kindling-bench.jar
# Both builds run with the same JVM flags, so that only the server jar differs.
vps="--cpus 8"
jvm="-XX:ActiveProcessorCount=8 -XX:+UseCompactObjectHeaders -Xms2G -Xmx2G"
runs=${RUNS:-3}
flight="players=5,blocks-per-tick=1.7,warmup-seconds=20,travel-seconds=90,mobs-per-player=0,settle-seconds=2,steady-seconds=5,idle-seconds=3"
# The same five paths flown slowly (0.8 blocks/tick for as far) on every CPU, so each chunk along them gets generated.
pregen="players=5,blocks-per-tick=0.8,warmup-seconds=20,travel-seconds=200,mobs-per-player=0,settle-seconds=2,steady-seconds=5,idle-seconds=3"

run() {
    local label=$1
    shift
    python -I run_bench.py --plugin "$plugin" --label "$prefix-$label" --scenario "$flight" "$@" > "runs/$prefix-$label.log" 2>&1 \
        || echo "FAILED $label (see runs/$prefix-$label.log)"
    echo "done $label"
}

echo "JVM flags of every run: $jvm ($runs runs per variant, RUNS=n changes that)"

run fork --jar "$fork" $vps "--jvm=$jvm" --repeat "$runs"
run upstream --jar "$upstream" $vps "--jvm=$jvm" --repeat "$runs"
run workers-7 --jar "$fork" $vps "--jvm=$jvm" --overlay overlays/workers-7 --repeat "$runs"
run vd-8 --jar "$fork" $vps "--jvm=$jvm" --overlay overlays/vd-8 --repeat "$runs"

if [ ! -d "worlds/$prefix-pregen" ]; then
    python -I run_bench.py --plugin "$plugin" --label "$prefix-pregen-make" --scenario "$pregen" --jar "$fork" "--jvm=$jvm" \
        > "runs/$prefix-pregen-make.log" 2>&1 || echo "FAILED pregen-make"
    mkdir -p worlds && cp -r "runs/$prefix-pregen-make-1/world" "worlds/$prefix-pregen"
    echo "done pregen-make"
fi
run pregen --jar "$fork" $vps "--jvm=$jvm" --world "worlds/$prefix-pregen" --repeat "$runs"
