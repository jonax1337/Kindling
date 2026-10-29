#!/usr/bin/env bash
# The parity checks. Usage: [PARITY_RUNS=n] run.sh <server.jar> [bases|sleeping|villagers|all] [--update]
#
#   bases      (default) the bases scenario in its CI size against the counters in bases-ci.json; --update rewrites it
#   sleeping   hoppers, brewing stands and furnaces with plugin stand-ins, run with Leaf's sleeping-block-entity on and
#              off, once with default hopper rates, once with non-default spigot.yml rates and once as the bases
#              contraptions with a plugin listening to InventoryMoveItemEvent; each pair must agree
#   villagers  halls of villagers claiming beds and job sites, run with async pathfinding on and off; the pair must agree
#              and no bed or job site may be claimed twice
#   all        the three in turn
#
# Needs the bench plugin (./gradlew :kindling-bench:jar) and Python with psutil.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
bench=$(dirname "$here")
jar=$1
shift

which=bases
case "${1:-}" in
    bases | sleeping | villagers | all)
        which=$1
        shift
        ;;
esac
if [ "$which" != bases ] && [ "${1:-}" = --update ]; then
    echo "--update only writes the golden file of the bases scenario"
    exit 2
fi

mkdir -p "$bench/runs"

# Runs the bench once per entry of PARITY_RUNS under the label; overlay folders (under overlays/) are given after it.
run_bench() {
    local label=$1 scenario=$2
    shift 2
    local overlays=()
    local overlay
    for overlay in "$@"; do
        overlays+=(--overlay "$bench/overlays/$overlay")
    done
    python -I "$bench/run_bench.py" --jar "$jar" --plugin "$bench/build/libs/kindling-bench.jar" --label "$label" \
        --heap 2G --repeat "${PARITY_RUNS:-1}" --scenario "$scenario" ${overlays[@]+"${overlays[@]}"} > "$bench/runs/$label.log" 2>&1 \
        || { tail -n 40 "$bench/runs/$label.log"; exit 1; }
}

# One player hovering over two plots: every contraption type, a few chunks to generate. Brewing stands finish their
# brews after about 100 s, so warm-up plus measurement cover 110 s of server time. Counted in ticks, not seconds.
check_bases() {
    run_bench parity-ci "type=bases,players=1,plots-per-player=2,warmup-seconds=20,steady-seconds=90"
    python -I "$here/check.py" "$bench/runs/parity-ci.json" "$@"
}

# Runs the scenario with the option on and off and requires the two to agree. $1 label, $2 scenario, $3 on overlay,
# $4 off overlay, the rest are overlays both runs share. The plugin stand-ins act within the first 10 s and the slowest
# counter (the furnaces) needs another 55 s, so the measurement starts with the build (warm-up 0) and lasts 65 s.
check_pair() {
    local label=$1 scenario=$2 on=$3 off=$4
    shift 4
    run_bench "$label-on" "$scenario" "$@" "$on"
    run_bench "$label-off" "$scenario" "$@" "$off"
    python -I "$here/check.py" --pair "$bench/runs/$label-on.json" "$bench/runs/$label-off.json"
}

check_sleeping() {
    local scenario="type=sleeping,players=1,plots-per-player=1,warmup-seconds=0,steady-seconds=65"
    local status=0
    check_pair parity-sleeping "$scenario" parity-sleeping-on parity-sleeping-off || status=1
    check_pair parity-sleeping-rates "$scenario" parity-sleeping-on parity-sleeping-off parity-hopper-rates || status=1
    check_bases_listener || status=1
    return $status
}

# The bases contraptions with a plugin listening to InventoryMoveItemEvent, so the hoppers take Paper's event path, with
# sleeping block entities on and off. Both runs must count exactly what bases-ci.json holds.
check_bases_listener() {
    local scenario="type=bases,players=1,plots-per-player=2,warmup-seconds=20,steady-seconds=90"
    local status=0
    local mode
    for mode in on off; do
        run_bench "parity-bases-listener-$mode" "$scenario" "parity-sleeping-listener-$mode"
        python -I "$here/check.py" "$bench/runs/parity-bases-listener-$mode.json" --ignore moveEventListener || status=1
    done
    return $status
}

# Four halls of twelve villagers in one plot. Two minutes are enough for the last villager to find its bed and job site.
check_villagers() {
    check_pair parity-villagers "type=villagers,players=1,plots-per-player=1,warmup-seconds=0,steady-seconds=120" \
        parity-pathfinding-on parity-pathfinding-off villagers-active
}

status=0
case "$which" in
    bases) check_bases "$@" || status=1 ;;
    sleeping) check_sleeping || status=1 ;;
    villagers) check_villagers || status=1 ;;
    all)
        check_bases || status=1
        check_sleeping || status=1
        check_villagers || status=1
        ;;
esac
exit $status
