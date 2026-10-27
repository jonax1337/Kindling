# 🧪 kindling-bench

**A reproducible load test.** A plugin plus a few scripts that put a server under the same load every time, so that every change (patch, setting, JVM flag) competes against a number instead of a feeling.

## What a run does

`run_bench.py` creates a fresh server under `runs/<label>-<n>/` (port 25999, bound to `127.0.0.1`, fixed seed, view distance 10, simulation distance 8), drops the plugin in and starts the given jar. The `KindlingBench` plugin waits until startup is done (`ServerLoadEvent`) and then plays the scenario from `plugins/KindlingBench/scenario.properties`:

| # | Phase | Key (default) | What happens | Measured |
| :-: | --- | --- | --- | --- |
| 1 | **Warm-up** | `warmup-seconds` (30) | `players` (8) fake players join on a circle of radius `circle-radius` (512) around the origin, in creative mode, flying at height 160 | |
| 2 | **Flight** | `travel-seconds` (90) | All fly straight outward at `blocks-per-tick` (1.0, roughly an elytra). The server keeps generating, loading, sending and unloading chunks | tick times, chunk lead |
| 3 | **Landing** | | Every player lands; around each, `mobs-per-player` (150) animals, villagers, zombies and skeletons appear (fixed random seed). It stays night | |
| 4 | **Settle**, then **standing** | `settle-seconds` (15), `steady-seconds` (90) | The players stand between the mobs | tick times |
| 5 | **State under load** | | | loaded chunks, entities, heap before and after two `System.gc()`, GC count and time |
| 6 | **Idle** | `idle-seconds` (60) | All players leave, as at night on a small server | what the JVM still holds on its own (`idle`) |

Then the plugin writes `bench-result.json` and stops the server. The script adds what only the operating system sees: peak RSS, RSS at the end of the idle phase (`rss_idle_mb`, the moment the result file appears), final RSS and the CPU time of the process.

### Chunk lead

During the flight, `chunk_lead` samples every 5 ticks per player how many chunks in flight direction, gap-free from the player, have already reached the client (`Player#isChunkSent`): average, minimum, 5th percentile, the share with lead 0 (`hole_pct`, the player flies into the void) and with lead at most 2 (`close_pct`), plus newly generated chunks per second.

### Fake players

The fake players request view distance 12 like a vanilla client (`client-view-distance`); the server sends up to the smaller of that and its own. They join through `PlayerList.placeNewPlayer` and move through the same packets as a client (movement, tick end, teleport and keep-alive answers). Their connection discards all outgoing packets, so everything up to the network is measured, but not encoding and compressing packets on the Netty threads. `flown_blocks_avg` in the result shows whether the flight really happened (expected: `travel-seconds × 20 × blocks-per-tick`).

## The bases scenario

`--scenario "type=bases"` measures what a survival server spends most of its ticks on once players have built: block entities and redstone. Every fake player hovers among `plots-per-player` (4, at most 10) plots, each four chunks on a stone floor at y 199, high above the terrain:

- **Storage room**: three hopper chains carrying cobblestone from one barrel into another, a row of idle hoppers between empty barrels, and a row of hoppers stuck in front of full barrels.
- **Furnace row**: twelve furnaces fed by hoppers from a barrel of cobblestone above and a barrel of coal beside, the stone carried off by a hopper below; four cold furnaces.
- **Redstone board**: a clock (a redstone block the plugin switches every 10 ticks) feeds a trunk of dust with three branches; along each, five sticky pistons push planks, observers above the planks light lamps, and a dropper at the end passes one item per pulse into a barrel.
- **Workshop**: five hoppers catching an item dropped onto each at every clock step; two item sorters, where a comparator reads a filter hopper and only one extra cobblestone unlocks the hopper under it, with cobblestone and dirt dropped onto the line by turns; hoppers loading and unloading a hopper minecart and a chest minecart on rails, a composter fed pumpkin pies and a double chest; two brewing stands that a hopper hands five ingredients one after the other, each as soon as the brew before has ended; and a crafter on a clock turning sugar cane into sugar.

Every clock steps one tick after the previous one, as clocks on a server are not in step. The workshop holds the cases where a server that lets idle block entities sleep (Leaf's `performance.sleeping-block-entity`) has to wake them exactly when vanilla acts: an item landing on a hopper, a minecart's inventory, a comparator reading a hopper, a hopper unlocked by redstone, a brew that ends.

The run waits until no chunk has been generated for 5 s and every plot ticks, removes the animals and items world generation left, builds all plots in one tick, lets them run for `warmup-seconds` and then measures for `steady-seconds`: tick times (`bases`) and `counters`, how much every contraption did in the measured ticks. Natural spawning, time and weather are off, and each contraption keeps to its own chunk, so nothing depends on chance or on the order in which chunks tick: **the counters must be identical in every run and in every server build that keeps vanilla behaviour.** `compare.py` marks a counter that differs with `!` and warns about runs that built before generation settled (`settled: false`). The idle and stuck hoppers must report 0. The supplies in the barrels and droppers last for about nine minutes of warm-up and measurement together; the brewing stands finish their five brews after about 100 seconds, within the default warm-up and measurement, and then stand idle.

Hopper events are not used for counting: Paper skips them when no plugin listens, and a listener would change the code path being measured. The counters read the barrels instead; only piston extensions are counted through their event, which Paper creates either way.

`hopper-guard-seconds=10` adds a stand-in protection plugin that holds every hopper back for the first 10 seconds after the build (it hides their sources and cancels transfers and pickups, as a claim plugin does until its claims are loaded) and then lets everything through. Its counters compare only with other guarded runs: a server whose hoppers stay stuck after the release reports far lower counters than one without sleeping hoppers.

## Building and running

```bash
./gradlew :kindling-bench:jar --no-configuration-cache      # kindling-bench/build/libs/kindling-bench.jar
python -I kindling-bench/run_bench.py --jar <server.jar> --plugin kindling-bench/build/libs/kindling-bench.jar \
    --label probe --jvm="-XX:+UseCompactObjectHeaders" --repeat 2
bash kindling-bench/matrix.sh <server.jar> <prefix> [variant ...]   # all variants in turn, two runs each
```

| Option of `run_bench.py` | Effect |
| --- | --- |
| `--heap` | Sets `-Xmx` (default 3G) and, unless `--jvm` names its own `-Xms`, also `-Xms` |
| `--overlay <folder>` | Copies files over the server folder before the start, for example `overlays/leaf-safe/config/leaf-global.yml`. Leaf fills in missing keys, so an overlay only needs the keys it changes |
| `--world <folder>` | Copies a world in as `world` before the start |
| `--scenario "players=4,travel-seconds=30"` | Overrides single scenario keys |
| `--cpus N` | Pins the server to the first N logical cores as a stand-in for a smaller machine (pair it with `-XX:ActiveProcessorCount=N` in `--jvm`, so the JVM sizes its thread pools accordingly) |

| Script | Measures |
| --- | --- |
| `bash kindling-bench/elytra.sh <kindling.jar> <leaf.jar> <prefix>` | The elytra test from [docs/MEASUREMENTS.md](../docs/MEASUREMENTS.md), including the pregenerated world (`worlds/`, not checked in) |
| `bash kindling-bench/pregen.sh <kindling.jar> <prefix>` | The built-in pregenerator alone and during an elytra flight |
| `python -I kindling-bench/compare.py <label> …` | Not a run: puts finished runs side by side |

> [!WARNING]
> Never run two benchmarks at once, and do not build anything heavy during a measurement: the runs would share the CPU and the numbers would be worthless. Run every variant twice and only trust differences larger than the gap between the two runs.

> [!TIP]
> Every run leaves a full server with its world under `runs/`. Delete the run folders once the results are read; they fill a disk quickly.

## Variants

`bash kindling-bench/options-matrix.sh <server.jar> <prefix>` runs `fork` and the `opt-*` variants in the mixed load, two runs each (about 85 minutes), and prints the `compare.py` table.

`SCENARIO=type=bases bash kindling-bench/matrix.sh <server.jar> <prefix> fork sleeping-be` runs the variants in the bases scenario instead; `sleeping-be` switches on Leaf's sleeping block entities.

A variant is a name. Its JVM arguments come from the `jvm` table in `matrix.sh` (without an entry it runs with `-Xms3G -Xmx3G` and G1); if a folder `overlays/<name>` exists, it is copied over the server before the start.

| Kind | Variants |
| --- | --- |
| JVM only (`matrix.sh`) | `coh` (compact object headers), `g1-2g`, `elastic`, `g1-tight`, `zgc`, `zgc-free`, `shenandoah` |
| One Leaf feature each | `only-tracker`, `only-pathfinding`, `only-cheap` (Leaf's synchronous optimisations), `only-dab`, `only-throttle` |
| One synchronous Leaf option each (`options-matrix.sh`) | `opt-cache-biome`, `opt-mob-spawning`, `opt-mob-despawn`, `opt-entity-activation`, `opt-waypoint`, and `opt-all` (the five together) |
| Candidate sets for Kindling's defaults | `cand-a`, `cand-b`, `cand-b-throttle`, `leaf-safe`, `leaf-aggressive` |
| The Kindling jar without an overlay | `fork` |
| Used by `elytra.sh` and `pregen.sh` | `workers-5`, `workers-7`, `vd-8` (view distance 8), `pregen-w5` (automatic pregeneration, 5 workers) |

What each variant showed is in [docs/MEASUREMENTS.md](../docs/MEASUREMENTS.md). Results land in `runs/<prefix>-<variant>.json` (a list of runs). `runs/`, `jars/` and `worlds/` are not checked in.
