# 🧪 kindling-bench

**A reproducible load test.** A plugin plus a few scripts that put a server under the same load every time, so that every change (patch, setting, JVM flag) competes against a number instead of a feeling.

## What a run does

`run_bench.py` creates a fresh server under `runs/<label>-<n>/` (port 25999, bound to `127.0.0.1`, fixed seed, view distance 10, simulation distance 8), drops the plugin in and starts the given jar. The `KindlingBench` plugin waits until startup is done (`ServerLoadEvent`) and then plays the scenario from `plugins/KindlingBench/scenario.properties`:

| # | Phase | Key (default) | What happens | Measured |
| :-: | --- | --- | --- | --- |
| 1 | **Warm-up** | `warmup-seconds` (30) | `players` (8) fake players join on a circle of radius `circle-radius` (512) around the origin, in creative mode (`gamemode`), flying at height 160. The warm-up lasts at least `warmup-seconds`, then until no chunk was generated for `warmup-quiet-seconds` (5), at most `warmup-max-seconds` (120, or `warmup-seconds` if that is longer) | `join_full_view_ms`, warm-up length |
| 2 | **Flight** | `travel-seconds` (90) | All fly straight outward at `blocks-per-tick` (1.0, roughly an elytra). The server keeps generating, loading, sending and unloading chunks | tick times, chunk lead |
| 3 | **Landing** | | Every player lands; around each, `mobs-per-player` (150) animals, villagers, zombies and skeletons appear (fixed random seed). It stays night | |
| 4 | **Settle**, then **standing** | `settle-seconds` (15), `steady-seconds` (90) | The players stand between the mobs | tick times |
| 5 | **State under load** | | | loaded chunks, entities, heap before and after two `System.gc()`, GC count and time |
| 6 | **Idle** | `idle-seconds` (60) | All players leave, as at night on a small server | what the JVM still holds on its own (`idle`) |

Then the plugin writes `bench-result.json` and stops the server. The script adds what only the operating system sees: peak RSS, RSS at the end of the idle phase (`rss_idle_mb`, the moment the result file appears), final RSS and the CPU time of the process.

### Chunk lead

During the flight, `chunk_lead` samples every 5 ticks per player how many chunks along a line from the player have already reached the client (`Player#isChunkSent`). The fields of the first versions are kept, so old runs stay comparable: `samples`, `lead_avg`, `lead_min`, `lead_p5`, `hole_pct` (lead 0: the player flies into the void), `close_pct` (lead at most 2) and `new_chunks`. They follow one line straight ahead in 16-block steps, which can skip a chunk on a diagonal, and never look at the player's own chunk. Added next to them:

| Field | Meaning |
| --- | --- |
| `rays` | The same figures for five lines, at -90, -45, 0 (straight ahead), 45 and 90 degrees to the flight direction. Each line is followed chunk by chunk, so no chunk it crosses is skipped, up to the send distance |
| `own_chunk_missing_pct` | Share of samples in which the chunk the player stands in has not been sent; the lead is then 0 on every line |
| `worst_ray_lead_avg`, `worst_ray_hole_pct` | Mean of the lowest lead of the five lines per sample, and the share where one of the lines has lead 0 |
| `wall_seconds`, `new_chunks_per_second` | The measured wall time of the flight and the newly generated chunks per second of it (earlier versions divided by the nominal `travel-seconds`, which differs when the server falls behind) |
| `windows` | One entry per 10 seconds of the flight (`start_s`): `wall_seconds`, `samples`, `lead_avg` and `hole_pct` straight ahead, `worst_ray_lead_avg`, `worst_ray_hole_pct`, `own_chunk_missing_pct`, `new_chunks`, `new_chunks_per_second`; shows when the lead broke down |

### More figures per run

| Field | Meaning |
| --- | --- |
| `warmup_seconds`, `warmup_wall_seconds`, `warmup_settled` | How long the warm-up of a mixed run took (server time and wall time) and whether generation had calmed down by its end |
| `join_full_view_ms`, `join_full_view_ms_avg` | From the joins until every chunk within the send distance of a player had reached it: the slowest player (-1 if one never got its view during the warm-up) and the mean |
| `chunk_packets_warmup`, `chunk_packets_travel`, `chunk_batches_travel` | Chunk packets the fake players received during the warm-up and the flight, summed over the players, and the chunk batches of the flight (0 as long as the server does not send chunks in batches) |
| `travel`, `steady`, `bases`: `wall_seconds`, `gc_count`, `gc_pause_millis` | Wall time of the phase and the garbage collection pauses in it, as the change over the phase. Only stop-the-world pauses count: ZGC and Shenandoah report their concurrent cycles as collectors of their own ("... Cycles"), which are left out; G1's "Concurrent GC" bean counts the remark and cleanup pauses and is included |
| `travel`, `steady`, `bases`: `main_run_ms`, `main_wait_ms` | Linux only: how long the server main thread ran and how long it waited in the run queue for a CPU (`/proc/self/task/<tid>/schedstat`). A high wait is a machine that is too busy, not a slow server |
| `travel`, `steady`, `bases`: `cpu_steal_pct`, `cpu_idle_pct` | Linux only: the share of CPU time of the whole machine that was stolen by the hypervisor or idle during the phase (`/proc/stat`) |
| `gc_pause_millis` | Pause time over the whole run, next to the older `gc_count` and `gc_millis`, which add up every collector bean including the overlapping cycles of ZGC and Shenandoah |

Where `/proc` is missing (Windows), the Linux figures are left out; `compare.py` leaves out the rows of figures no run has.

### Fake players

The fake players request view distance 12 like a vanilla client (`client-view-distance`); the server sends up to the smaller of that and its own. They join through `PlayerList.placeNewPlayer` and move through the same packets as a client (movement, tick end, teleport and keep-alive answers). Their connection discards all outgoing packets, so everything up to the network is measured, but not encoding and compressing packets on the Netty threads. `flown_blocks_avg` in the result shows whether the flight really happened (expected: `travel-seconds × 20 × blocks-per-tick`).

- **`gamemode=survival`** (default `creative`, which keeps runs comparable): the players are survival players that mobs target and path to; the bench heals them and refills their food every 5 ticks, so they stay alive and in the game. Only the mixed scenario uses it; the bases players are always creative.
- **`chunk-acks=<chunks per tick>`** (default 0, off): the connection counts the chunk packets it is sent and, when the server sends a chunk batch (`ClientboundChunkBatchStart` and `Finished`), answers like a client that processes that many chunks per tick: after `batch size / chunk-acks` ticks it sends `ServerboundChunkBatchReceived` with `chunk-acks` as the desired rate. With the default the fake players never answer, as before. Use it to emulate fast (`chunk-acks=10`) and slow (`chunk-acks=0.5`) clients once the server paces its chunks by those answers.

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

## Parity check (CI)

`parity/run.sh <server.jar>` runs the bases scenario in a size a GitHub runner finishes in a few minutes (one player over two plots, 20 s warm-up, 90 s measured; every contraption type is in each plot) and compares its counters with [`parity/bases-ci.json`](parity/bases-ci.json). A counter that differs makes `parity/check.py` print `name: expected X, got Y` and exit 1. The workflows [`parity.yml`](../.github/workflows/parity.yml) (patch, Kindling code and bench changes) and [`leaf-upstream.yml`](../.github/workflows/leaf-upstream.yml) (after the weekly Leaf merge; it reports the result in the `upstream` issue) run it.

The counters do not depend on the speed of the machine: the whole scenario is counted in server ticks (clocks, warm-up, measurement and the hopper guard all use the tick counter, none the wall clock), so a slow runner takes longer but counts the same. The one thing that must hold is that world generation has settled before the bases are built; a run that did not (`settled: false`) fails the check instead of reporting counters.

```bash
./gradlew :kindling-bench:jar --no-configuration-cache
bash kindling-bench/parity/run.sh <server.jar>               # needs Python with psutil
PARITY_RUNS=2 bash kindling-bench/parity/run.sh <server.jar>   # every one of two runs must match
```

**Regenerating the golden file.** The counters are a property of the bench (what it builds and how often it steps it) together with vanilla behaviour. When the bench changes what it builds, steps or counts, or when behaviour is meant to change (a Leaf merge that fixes a vanilla bug), the file is stale. Build the new bench against a jar you trust, run it twice and write the file only if both agree:

```bash
PARITY_RUNS=2 bash kindling-bench/parity/run.sh <server.jar> --update
git diff kindling-bench/parity/bases-ci.json
```

`--update` refuses to write when a run did not settle or the runs disagree. Commit the new file together with the bench change, and say in the commit message why the counters moved. The scenario of the CI run is the `scenario` line in `parity/run.sh`; the golden file records the scenario it was made with, and the check fails on a mismatch.

## Building and running

```bash
./gradlew :kindling-bench:jar --no-configuration-cache      # kindling-bench/build/libs/kindling-bench.jar
python -I kindling-bench/run_bench.py --jar <server.jar> --plugin kindling-bench/build/libs/kindling-bench.jar \
    --label probe --jvm="-XX:+UseCompactObjectHeaders" --repeat 2
bash kindling-bench/matrix.sh <server.jar> <prefix> [variant ...]   # all variants in turn, three runs each (RUNS=n)
```

| Option of `run_bench.py` | Effect |
| --- | --- |
| `--heap` | Sets `-Xmx` (default 3G) and, unless `--jvm` names its own `-Xms`, also `-Xms` |
| `--overlay <folder>` | Copies files over the server folder before the start, for example `overlays/leaf-safe/config/leaf-global.yml`. Leaf fills in missing keys, so an overlay only needs the keys it changes |
| `--world <folder>` | Copies a world in as `world` before the start |
| `--scenario "players=4,travel-seconds=30"` | Overrides single scenario keys |
| `--cpus N` | Pins the server to the first N logical cores as a stand-in for a smaller machine (pair it with `-XX:ActiveProcessorCount=N` in `--jvm`, so the JVM sizes its thread pools accordingly). On Linux the server starts under `taskset -c 0-(N-1)`, so every thread inherits the mask; on Windows the affinity is set after the start |

| Script | Measures |
| --- | --- |
| `bash kindling-bench/elytra.sh <kindling.jar> <leaf.jar> <prefix>` | The elytra test from [docs/MEASUREMENTS.md](../docs/MEASUREMENTS.md), including the pregenerated world (`worlds/`, not checked in). Both jars run with the same JVM flags (2G heap, compact object headers), which the script prints; three runs per variant (`RUNS=n`) |
| `bash kindling-bench/pregen.sh <kindling.jar> <prefix>` | The built-in pregenerator alone and during an elytra flight |
| `python -I kindling-bench/compare.py <label> …` | Not a run: puts finished runs side by side |

### Comparing runs

`compare.py` prints per figure the mean, the half range (half the gap between the highest and the lowest run) and the number of runs, as `12.3 ±0.4 n3`. Every label after the first also gets the p-value of Welch's t-test against the first label. **A difference counts as real only if it is marked `*`: p < 0.05 and the means more than twice the larger half range apart.** Both conditions are needed; a small p from two runs that happen to agree closely is not enough, and a big gap with a wide spread is not either. Zero spread in both labels makes any difference significant, so read a gap of a few MB with that in mind. With a single run in a label there is no p-value (`p=n/a`) and nothing is significant. That is why the matrices run three runs per variant.

> [!WARNING]
> Never run two benchmarks at once, and do not build anything heavy during a measurement: the runs would share the CPU and the numbers would be worthless. Run every variant three times and only trust differences that `compare.py` marks with `*`.

> [!TIP]
> Every run leaves a full server with its world under `runs/`. Delete the run folders once the results are read; they fill a disk quickly.

## Variants

`bash kindling-bench/options-matrix.sh <server.jar> <prefix>` runs `fork` and the `opt-*` variants in the mixed load, three runs each (8 variants x 3 runs x about 6 minutes, so about 145 minutes), and prints the `compare.py` table.

`SCENARIO=type=bases bash kindling-bench/matrix.sh <server.jar> <prefix> fork sleeping-be` runs the variants in the bases scenario instead; `sleeping-be` switches on Leaf's sleeping block entities.

A variant is a name. Its JVM arguments come from the `jvm` table in `matrix.sh` (without an entry it runs with `-Xms3G -Xmx3G` and G1); if a folder `overlays/<name>` exists, it is copied over the server before the start.

| Kind | Variants |
| --- | --- |
| JVM only (`matrix.sh`) | `coh` (compact object headers), `g1-2g`, `elastic`, `g1-tight`, `zgc`, `zgc-free`, `shenandoah` |
| One Leaf feature each | `only-tracker`, `only-pathfinding`, `only-cheap` (Leaf's synchronous optimisations), `only-dab`, `only-throttle` |
| One synchronous Leaf option each (`options-matrix.sh`) | `opt-cache-biome`, `opt-mob-spawning`, `opt-mob-despawn`, `opt-entity-activation`, `opt-waypoint`, `opt-powered-rails`, and `opt-all` (the five without `opt-powered-rails`). Left out: `reuse-random-ticking-blockpos` (shares one mutable position across calls) and `faster-random-generator` (changes the random sequence, already measured slower) |
| Candidate sets for Kindling's defaults | `cand-a`, `cand-b`, `cand-b-throttle`, `leaf-safe`, `leaf-aggressive` |
| The Kindling jar without an overlay | `fork` |
| Used by `elytra.sh` and `pregen.sh` | `workers-5`, `workers-7`, `vd-8` (view distance 8), `pregen-w5` (automatic pregeneration, 5 workers) |

What each variant showed is in [docs/MEASUREMENTS.md](../docs/MEASUREMENTS.md). Results land in `runs/<prefix>-<variant>.json` (a list of runs). `runs/`, `jars/` and `worlds/` are not checked in.
