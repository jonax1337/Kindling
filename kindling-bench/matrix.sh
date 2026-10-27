#!/usr/bin/env bash
# Runs benchmark variants against one server jar, twice each, one after another (never in parallel: they would share
# the CPU and skew each other). Usage: [SCENARIO=type=bases] matrix.sh <server.jar> <label prefix> [variant ...]
set -euo pipefail
cd "$(dirname "$0")"

jar=$1
prefix=$2
shift 2
plugin=build/libs/kindling-bench.jar
coh="-XX:+UseCompactObjectHeaders"
elastic="-Xms256M -XX:G1PeriodicGCInterval=15000 -XX:+UseStringDeduplication"
tight="-Xms256M -XX:G1PeriodicGCInterval=15000 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30"

# JVM arguments per variant; a variant not listed here runs with the stock JVM (-Xms = -Xmx).
declare -A jvm=(
    [coh]="$coh"
    [elastic]="$coh $elastic"
    [g1-tight]="$coh $tight"
    [zgc]="$coh -Xms256M -XX:+UseZGC -XX:SoftMaxHeapSize=2G -XX:ZUncommitDelay=30"
    [zgc-free]="$coh -Xms256M -XX:+UseZGC -XX:ZUncommitDelay=30"
    [shenandoah]="$coh -Xms256M -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational -XX:+UnlockExperimentalVMOptions -XX:ShenandoahUncommitDelay=30000"
    [g1-2g]="$coh -Xms2G -Xmx2G"
    [fork]="$coh -Xms2G -Xmx2G"
    [cand-a]="$coh -Xms2G -Xmx2G"
    [cand-b]="$coh -Xms2G -Xmx2G"
    [cand-b-throttle]="$coh -Xms2G -Xmx2G"
    [leaf-safe]="$coh $elastic"
    [leaf-aggressive]="$coh $elastic"
    [sleeping-be]="$coh -Xms2G -Xmx2G"
)

variants=("$@")
if [ ${#variants[@]} -eq 0 ]; then
    variants=(stock coh elastic g1-tight zgc zgc-free leaf-safe leaf-aggressive)
fi

for variant in "${variants[@]}"; do
    args=(--jar "$jar" --plugin "$plugin" --label "$prefix-$variant" --repeat 2 "--jvm=${jvm[$variant]:-}"
          "--scenario=${SCENARIO:-}")
    # Every folder under overlays/ is a Leaf configuration variant of the same name.
    if [ -d "overlays/$variant" ]; then
        args+=(--overlay "overlays/$variant")
    fi
    python -I run_bench.py "${args[@]}" > "runs/$prefix-$variant.log" 2>&1 || echo "FAILED $variant (see runs/$prefix-$variant.log)"
    echo "done $variant"
done
