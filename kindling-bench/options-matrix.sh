#!/usr/bin/env bash
# Benchmarks each of Leaf's synchronous, vanilla-preserving options alone against the Kindling defaults in the mixed
# load (flight, then standing among 1 200 mobs), two runs per variant, one after another, and prints the comparison.
# Usage: options-matrix.sh <server.jar> <label prefix>      (about 85 minutes; start it alone, the machine must stay idle)
set -euo pipefail
cd "$(dirname "$0")"

jar=$1
prefix=$2
variants=(fork opt-cache-biome opt-mob-spawning opt-mob-despawn opt-entity-activation opt-waypoint opt-all)

bash matrix.sh "$jar" "$prefix" "${variants[@]}"
python -I compare.py "${variants[@]/#/$prefix-}"
