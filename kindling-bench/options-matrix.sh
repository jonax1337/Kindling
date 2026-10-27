#!/usr/bin/env bash
# Benchmarks each of Leaf's synchronous, vanilla-preserving options alone against the Kindling defaults in the mixed
# load (flight, then standing among 1 200 mobs), three runs per variant (RUNS=n changes that), one after another, and
# prints the comparison.
# Usage: options-matrix.sh <server.jar> <label prefix>
# Takes about 145 minutes (8 variants x 3 runs x about 6 minutes); start it alone, the machine must stay idle.
# Left out on purpose, although part of the original "synchronous optimisations" measurement:
#   reuse-random-ticking-blockpos  hands one shared mutable BlockPos to randomTick (plugin conflicts, not vanilla-safe)
#   faster-random-generator        changes the random sequence and was already measured slower
# opt-powered-rails is included: it keeps vanilla order but may still be the culprit of the flight regression.
set -euo pipefail
jar=$(realpath "$1")
prefix=$2
cd "$(dirname "$0")"

variants=(fork opt-cache-biome opt-mob-spawning opt-mob-despawn opt-entity-activation opt-waypoint opt-powered-rails opt-all)

bash matrix.sh "$jar" "$prefix" "${variants[@]}"
python -I compare.py "${variants[@]/#/$prefix-}"
