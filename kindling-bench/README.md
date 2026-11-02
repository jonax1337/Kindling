# kindling-bench

The load test of Kindling. A plugin and a few scripts put a server under the same load every time, so every change (patch, setting, JVM flag) is judged by a number and not by a feeling. Why each change exists and what it measured: [MEASUREMENTS](../docs/MEASUREMENTS.md).

## Run a comparison

Needs Java 25, Python with `psutil`, and bash (Linux, WSL or Git Bash). The server binds to `127.0.0.1:25999` only.

1. Build the bench plugin. It lands in `kindling-bench/build/libs/kindling-bench.jar`.

   ```bash
   ./gradlew :kindling-bench:jar --no-configuration-cache
   ```

2. Run every variant three times, one after another. `matrix.sh` writes `runs/<prefix>-<variant>.json` and a log next to it.

   ```bash
   bash kindling-bench/matrix.sh <server.jar> <prefix> [variant ...]
   ```

   `RUNS=n` changes the number of runs, `SCENARIO="key=value,..."` changes the scenario. For a single run with your own flags use `run_bench.py` directly:

   ```bash
   python -I kindling-bench/run_bench.py --jar <server.jar> --plugin kindling-bench/build/libs/kindling-bench.jar \
       --label probe --jvm="-XX:+UseCompactObjectHeaders" --repeat 2
   ```

3. Put the finished runs side by side. The first label is the baseline.

   ```bash
   python -I kindling-bench/compare.py <prefix>-fork <prefix>-other
   ```

4. Delete the run folders under `runs/` once you have read the results. Every run leaves a full server with its world.

> [!WARNING]
> Never run two benchmarks at once and build nothing heavy during a measurement. The runs would share the CPU and the numbers would be worthless.

## Read the results

`compare.py` prints, for every figure, the mean, the half range and the number of runs, for example `12.3 ±0.4 n3`. The half range is half the gap between the highest and the lowest run. Every label after the first also shows the p-value of Welch's t-test against the first label.

A difference counts only if `compare.py` marks it `*`. That needs both:

- p < 0.05, and
- the means are more than twice the larger half range apart.

Run every variant three times: with one run there is no p-value (`p=n/a`) and nothing is marked. Zero spread in both labels makes any difference significant, so treat a gap of a few MB with care.

Other marks and rows:

- Rows that no run has are left out, so mixed and bases runs use the same script.
- In a bases run, a counter row marked `!` differs between runs. That means behaviour changed.
- A warning says that a bases run built before world generation settled (`settled: false`).
- `chart.py` draws Kindling against plain Leaf from four labels (mixed and bases runs of both) as an SVG pair for light and dark backgrounds: `python -I kindling-bench/chart.py <leaf-mixed> <kindling-mixed> <leaf-bases> <kindling-bases> docs/media/kindling-vs-leaf`.

## What a run does

`run_bench.py` creates a fresh server under `runs/<label>-<n>/` (port 25999 on `127.0.0.1`, fixed seed, view distance 10, simulation distance 8), drops the plugin in and starts the jar. The `KindlingBench` plugin waits for `ServerLoadEvent` and plays the scenario from `plugins/KindlingBench/scenario.properties`.

| # | Phase | Key (default) | What happens | Measured |
| :-: | --- | --- | --- | --- |
| 1 | Warm-up | `warmup-seconds` (30) | `players` (8) fake players join on a circle of radius `circle-radius` (512) around the origin, in creative mode, flying at height 160. The warm-up lasts at least `warmup-seconds`, then until no chunk was generated for `warmup-quiet-seconds` (5), at most `warmup-max-seconds` (120, or `warmup-seconds` if that is longer) | `join_full_view_ms`, warm-up length |
| 2 | Flight | `travel-seconds` (90) | All fly straight outward at `blocks-per-tick` (1.0, roughly an elytra). The server keeps generating, loading, sending and unloading chunks | tick times, chunk lead |
| 3 | Landing | | Every player lands. Around each, `mobs-per-player` (150) animals, villagers, zombies and skeletons appear (fixed random seed). It stays night | |
| 4 | Settle, then standing | `settle-seconds` (15), `steady-seconds` (90) | The players stand between the mobs | tick times |
| 5 | State under load | | | loaded chunks, entities, heap before and after two `System.gc()`, GC count and time |
| 6 | Idle | `idle-seconds` (60) | All players leave, as at night on a small server | what the JVM still holds on its own (`idle`) |

Then the plugin writes `bench-result.json` and stops the server. The script adds what only the operating system sees: peak RSS, RSS at the end of the idle phase (`rss_idle_mb`, taken when the result file appears), final RSS and the CPU time of the process.

### Options of `run_bench.py`

| Option | Effect |
| --- | --- |
| `--jar`, `--plugin`, `--label` | The server jar, the bench plugin jar and the name of the runs (all required) |
| `--repeat N` | Runs the variant N times (default 1) |
| `--heap` | Sets `-Xmx` (default `3G`). Also sets `-Xms`, unless `--jvm` names its own |
| `--jvm` | Extra JVM arguments as one string |
| `--overlay <folder>` | Copies files over the server folder before the start, for example `overlays/leaf-safe/config/leaf-global.yml`. Leaf fills in missing keys, so an overlay needs only the keys it changes. May be given more than once; later folders overwrite earlier ones |
| `--world <folder>` | Copies a world in as `world` before the start |
| `--scenario "players=4,travel-seconds=30"` | Overrides single scenario keys. An overlay may also ship `plugins/KindlingBench/scenario.properties`; its keys are written first, so the command line wins when both set a key |
| `--cpus N` | Pins the server to the first N logical cores as a stand-in for a smaller machine. Pair it with `-XX:ActiveProcessorCount=N` in `--jvm`. On Linux the server starts under `taskset`; elsewhere the affinity is set after the start |

### Scripts

| Script | What it does |
| --- | --- |
| `matrix.sh <jar> <prefix> [variant ...]` | All variants in turn, three runs each (`RUNS=n`). Without variants it runs `stock coh elastic g1-tight zgc zgc-free leaf-safe leaf-aggressive` |
| `options-matrix.sh <jar> <prefix>` | `fork` and the `opt-*` variants in the mixed load, three runs each (8 variants x 3 runs x about 6 minutes), then the `compare.py` table |
| `elytra.sh <kindling.jar> <leaf.jar> <prefix>` | The elytra test from [MEASUREMENTS](../docs/MEASUREMENTS.md), including the pregenerated world (`worlds/`, not checked in). Both jars run with the same JVM flags (2G heap, compact object headers), which the script prints |
| `pregen.sh <kindling.jar> <prefix>` | The built-in pregenerator alone and during an elytra flight |
| `compare.py <label> ...` | Puts finished runs side by side. Not a run |
| `chart.py` | Draws the Kindling-against-Leaf chart from finished runs |

`runs/`, `jars/` and `worlds/` are not checked in.

### Figures per run

The result file holds many figures. `compare.py` leaves out the rows no run has. Where `/proc` is missing (Windows), the Linux figures are left out.

| Field | Meaning |
| --- | --- |
| `warmup_seconds`, `warmup_wall_seconds`, `warmup_settled` | How long the warm-up of a mixed run took (server time and wall time) and whether generation had calmed down by its end |
| `join_full_view_ms`, `join_full_view_ms_avg` | From the joins until every chunk within the send distance of a player had reached it: the slowest player (-1 if one never got its view during the warm-up) and the mean |
| `chunk_packets_warmup`, `chunk_packets_travel`, `chunk_batches_travel` | Chunk packets the fake players received during the warm-up and the flight, summed over the players, and the chunk batches of the flight (0 while the server does not send chunks in batches) |
| `travel`, `steady`, `bases`: `wall_seconds`, `gc_count`, `gc_pause_millis` | Wall time of the phase and the garbage collection pauses in it. Only stop-the-world pauses count. ZGC and Shenandoah report their concurrent cycles as collectors of their own ("... Cycles"); those are left out. G1's "Concurrent GC" bean counts the remark and cleanup pauses and is included |
| `travel`, `steady`, `bases`: `main_alloc_mb`, `all_alloc_mb` | Heap MiB the server main thread and all threads together allocated during the phase, as HotSpot counts them (`com.sun.management.ThreadMXBean`). The total for all threads is the JVM's own approximation. Left out where the JVM does not count |
| `travel`, `steady`, `bases`: `main_run_ms`, `main_wait_ms` | Linux only: how long the server main thread ran and how long it waited in the run queue for a CPU (`/proc/self/task/<tid>/schedstat`). A high wait means the machine is too busy, not that the server is slow |
| `travel`, `steady`, `bases`: `cpu_steal_pct`, `cpu_idle_pct` | Linux only: the share of CPU time of the whole machine that the hypervisor stole or that was idle during the phase (`/proc/stat`) |
| `gc_pause_millis` | Pause time over the whole run. The older `gc_count` and `gc_millis` add up every collector bean, including the overlapping cycles of ZGC and Shenandoah |
| `java_version` | The Java runtime the server ran on (`Runtime.version()`), so runs on different JDK builds, early-access builds in particular, are not mixed up |

### Chunk lead

During the flight, `chunk_lead` samples every 5 ticks per player how many chunks along a line from the player have already reached the client (`Player#isChunkSent`). The first line runs straight ahead in 16-block steps. It can skip a chunk on a diagonal and never looks at the player's own chunk.

| Field | Meaning |
| --- | --- |
| `samples`, `lead_avg`, `lead_min`, `lead_p5` | The lead along the first line |
| `hole_pct`, `close_pct` | Share of samples with lead 0 (the player flies into the void) and with lead at most 2 |
| `new_chunks` | Chunks generated during the flight |
| `rays` | The same figures for five lines, at -90, -45, 0 (straight ahead), 45 and 90 degrees to the flight direction. Each line is followed chunk by chunk up to the send distance, so no chunk it crosses is skipped |
| `own_chunk_missing_pct` | Share of samples in which the chunk the player stands in has not been sent. The lead is then 0 on every line |
| `worst_ray_lead_avg`, `worst_ray_hole_pct` | Mean of the lowest lead of the five lines per sample, and the share of samples where one line has lead 0 |
| `wall_seconds`, `new_chunks_per_second` | The measured wall time of the flight and the newly generated chunks per second of it |
| `windows` | One entry per 10 seconds of the flight (`start_s`) with `wall_seconds`, `samples`, `lead_avg` and `hole_pct` straight ahead, `worst_ray_lead_avg`, `worst_ray_hole_pct`, `own_chunk_missing_pct`, `new_chunks`, `new_chunks_per_second`. It shows when the lead broke down |

### Fake players

The fake players request view distance 12 like a vanilla client (`client-view-distance`). The server sends up to the smaller of that and its own. They join through `PlayerList.placeNewPlayer` and move through the same packets as a client (movement, tick end, teleport and keep-alive answers). Their connection discards all outgoing packets. So everything up to the network is measured, but not encoding and compressing packets on the Netty threads. `flown_blocks_avg` shows whether the flight really happened (expected: `travel-seconds × 20 × blocks-per-tick`).

- **`gamemode=survival`** (default `creative`, which keeps runs comparable): mobs target and path to the players. The bench heals them and refills their food every 5 ticks. Only the mixed scenario uses it; the bases players are always creative.
- **`chunk-acks=<chunks per tick>`** (default 0, off): when the server sends a chunk batch, the connection answers like a client that processes that many chunks per tick. After `batch size / chunk-acks` ticks it sends `ServerboundChunkBatchReceived` with `chunk-acks` as the desired rate. With 0 the fake players never answer. Use `chunk-acks=10` for a fast and `chunk-acks=0.5` for a slow client. Kindling does not pace chunk batches by client speed ([chunks](../docs/measurements/chunks.md)).

### Known quirks

- `chunk_lead.windows` leaves out a last window shorter than a second. Its samples still count in the totals.
- `rss_last_mb` and `rss_idle_mb` are 0 when the server exits within the one-second sampling window of `run_bench.py`. `compare.py` then shows `RSS end MB 0.0` or a huge spread. Ignore those rows or sample `/proc` yourself.
- The kicked fake players leave the world but stay in the server's player list. A feature that waits for an empty server (`performance.idle-memory`, `pause-when-empty-seconds`) never sees one in the idle phase. Measuring those needs a helper plugin that completes their disconnect.
- Long runs on a WSL2 host have to start through a detached Windows process (for example `Invoke-CimMethod Win32_Process Create` running `wsl.exe ... bash script`). A `nohup` job in an SSH session dies with the session, and WSL shuts down.
- The plugin reports only a total of entities. Per-category counts come from a script that reads the entity region files of the run folders.

## The bases scenario

`--scenario "type=bases"` measures what a survival server spends most of its ticks on once players have built: block entities and redstone. Every fake player hovers among `plots-per-player` (4, at most 10) plots. Each plot is four chunks on a stone floor at y 199, high above the terrain.

- **Storage room**: three hopper chains carrying cobblestone from one barrel into another, a row of idle hoppers between empty barrels, and a row of hoppers stuck in front of full barrels.
- **Furnace row**: twelve furnaces fed by hoppers from a barrel of cobblestone above and a barrel of coal beside, the stone carried off by a hopper below; four cold furnaces.
- **Redstone board**: a clock (a redstone block the plugin switches every 10 ticks) feeds a trunk of dust with three branches. Along each, five sticky pistons push planks, observers above the planks light lamps, and a dropper at the end passes one item per pulse into a barrel.
- **Workshop**: five hoppers catching an item dropped onto each at every clock step; two item sorters, where a comparator reads a filter hopper and only one extra cobblestone unlocks the hopper under it, with cobblestone and dirt dropped onto the line by turns; hoppers loading and unloading a hopper minecart and a chest minecart on rails, a composter fed pumpkin pies and a double chest; two brewing stands that a hopper hands five ingredients one after the other, each as soon as the brew before has ended; and a crafter on a clock turning sugar cane into sugar.

Every clock steps one tick after the previous one. The workshop holds the cases where a server that lets idle block entities sleep (Leaf's `performance.sleeping-block-entity`) has to wake them exactly when vanilla acts: an item landing on a hopper, a minecart's inventory, a comparator reading a hopper, a hopper unlocked by redstone, a brew that ends.

The run waits until no chunk has been generated for 5 s and every plot ticks. It removes the animals and items world generation left, builds all plots in one tick, lets them run for `warmup-seconds` and then measures for `steady-seconds`. The result holds tick times (`bases`) and `counters`: how much every contraption did in the measured ticks. Natural spawning, time and weather are off, and each contraption keeps to its own chunk. So nothing depends on chance or on the order in which chunks tick.

**The counters must be identical in every run and in every server build that keeps vanilla behaviour.** `compare.py` marks a counter that differs with `!`. The idle and stuck hoppers must report 0. The supplies in the barrels and droppers last for about nine minutes of warm-up and measurement together. The brewing stands finish their five brews after about 100 seconds, within the default warm-up and measurement, and then stand idle.

Hopper events are not used for counting: Paper skips them when no plugin listens, and a listener would change the code path being measured. The counters read the barrels instead. Only piston extensions are counted through their event, which Paper creates either way.

Two keys add a plugin stand-in:

- `hopper-guard-seconds=10` (default 0) adds a protection plugin stand-in. For the first 10 seconds after the build it holds every hopper back (it hides their sources and cancels transfers and pickups, as a claim plugin does until its claims are loaded), then lets everything through. Its counters compare only with other guarded runs: a server whose hoppers stay stuck after the release reports far lower counters than one without sleeping hoppers.
- `move-event-listener=true` (default `false`) registers `PassiveMoveListener`, which listens to `InventoryMoveItemEvent` at `MONITOR` with `ignoreCancelled=true` and does nothing, for the whole run and every scenario type. A listener makes hoppers take Paper's event path, as protection and logging plugins do. Use it to measure whether sleeping hoppers stay asleep through Paper's temporary stack count. `type=sleeping` and `hopper-guard-seconds` runs already have a listener, so the key matters mostly for `type=bases` and `type=mixed`. The run records it as `scenario.moveEventListener`.

## Parity check (CI)

`parity/run.sh <server.jar>` runs the bases scenario in a size a GitHub runner finishes in a few minutes: one player over two plots, 20 s warm-up, 90 s measured, every contraption type in each plot. It compares the counters with [`parity/bases-ci.json`](parity/bases-ci.json). A counter that differs makes `parity/check.py` print `name: expected X, got Y` and exit 1. The workflows [`parity.yml`](../.github/workflows/parity.yml) (patch, Kindling code and bench changes) and [`leaf-upstream.yml`](../.github/workflows/leaf-upstream.yml) (after the weekly Leaf merge; it reports the result in the `upstream` issue) run it.

The counters do not depend on the speed of the machine. The whole scenario is counted in server ticks (clocks, warm-up, measurement and the hopper guard all use the tick counter), so a slow runner takes longer but counts the same. World generation must have settled before the bases are built. A run that did not (`settled: false`) fails the check instead of reporting counters.

```bash
./gradlew :kindling-bench:jar --no-configuration-cache
bash kindling-bench/parity/run.sh <server.jar>                 # needs Python with psutil
PARITY_RUNS=2 bash kindling-bench/parity/run.sh <server.jar>   # every one of two runs must match
```

**Regenerate the golden file** when the bench changes what it builds, steps or counts, or when behaviour is meant to change (a Leaf merge that fixes a vanilla bug). Build the new bench against a jar you trust, run it twice and write the file only if both runs agree:

```bash
PARITY_RUNS=2 bash kindling-bench/parity/run.sh <server.jar> --update
git diff kindling-bench/parity/bases-ci.json
```

`--update` refuses to write when a run did not settle or the runs disagree. Commit the new file together with the bench change and say in the commit message why the counters moved. The scenario of the CI run is the `scenario` line in `parity/run.sh`. The golden file records the scenario it was made with, and the check fails on a mismatch.

## Option parity scenarios

Two more scenario types check that an option of the server changes speed and nothing else. They are not compared with a golden file but with themselves. Every scenario runs twice, with the option under test on and off (the overlay folders `overlays/parity-*`), and `parity/check.py --pair` requires the counters to agree.

- Their counters are absolute: what each module holds at the end, counted from what it was built with.
- They run without a warm-up (`warmup-seconds=0`), because the plugin stand-ins act at fixed ticks after the build.
- Each run reports `options`: the settings of the files the server started with (`performance.sleeping-block-entity`, `async.async-pathfinding.enabled`, the `spigot.yml` hopper rates and villager activation range). The pair check fails if the toggle is not on in one run and off in the other, or if any other option differs. So an overlay that silently did not apply cannot pass as parity.
- The check requires that the reference run (option off) shows work: counters that must be above 0 are listed in `PAIR_RULES` in `parity/check.py`.

```bash
bash kindling-bench/parity/run.sh <server.jar> sleeping    # six runs of 2 to 3 min each: sleeping block entities on/off, default and tuned hopper rates, and the bases with a move event listener
bash kindling-bench/parity/run.sh <server.jar> villagers   # two runs of about 3 min each: async pathfinding on/off
bash kindling-bench/parity/run.sh <server.jar> all         # bases, sleeping and villagers
```

Without a scenario name `run.sh` runs the bases check. A pair that disagrees is printed counter by counter with the likely cause. The files stay in `runs/parity-<name>-on.json` and `-off.json`. `PARITY_RUNS=n` repeats every run n times and compares run k of one side with run k of the other.

### `type=sleeping`

Three chunks per plot (the fourth is empty). Each module covers a place where a server that lets idle block entities sleep (Leaf's `performance.sleeping-block-entity`) can differ from Paper.

| Module | What it does | Counters | Bug it catches |
| --- | --- | --- | --- |
| Hopper hall | the chains, idle and stuck rows of the bases storage room | `hopper_chain_items`, `hopper_idle_items`, `hopper_stuck_items` | with the overlay `parity-hopper-rates` (`hopper-amount: 3`, `ticks-per.hopper-transfer: 4`; Spigot's defaults are 1 and 8): the sleeping hopper path ignores both, so the chains carry other amounts |
| Hopper rewrite | four hoppers with 20 cobblestone each push into barrels. A plugin stand-in (`HopperRewriter`) calls `InventoryMoveItemEvent#setItem` with a tagged copy of the moved item | `rewrite_arrived` (items in the barrels), `rewrite_left` (items missing from the hoppers), `rewrite_duplicated` (arrived minus left) | the sleeping path shrinks a copy of the stack, so the hopper keeps its items and the barrels fill up: `rewrite_duplicated` above 0 |
| Physics-free placement | after 10 s a chest is placed under two hoppers that point into air, and over two barrels under a stocked chest above two hoppers, once with `Block#setType(type, false)` and once with `Block#setBlockData(data, false)` | `placement_target_arrived`, `placement_source_arrived` (20 each) | the hoppers cached "no inventory" and are not woken by a block placed without block updates: both stay 0 |
| Gated fuel row | two brewing stands (3 water bottles, nether wart, blaze powder) and two furnaces (cobblestone, coal). A plugin stand-in (`FuelGate`) cancels `BrewingStandFuelEvent` and sets `FurnaceBurnEvent#setBurning(false)` for the first 10 s | `gate_brewed_potions` (6 awkward potions), `gate_smelted_items` (12 stones) | the stands and furnaces fall asleep during the hold and never wake: both stay 0. Only the stand half was demonstrated on a buggy build; the furnaces smelted 12 with sleeping on and off |

The plugin stand-ins are real event listeners, so they also switch the server onto code paths that exist only when a plugin listens. The CI size is one player and one plot with 65 s of measuring. The furnaces need the last of it: a stone takes 10 s, the first fuel is allowed after 10 s, six stones each by the end. `rewrite_duplicated` must be 0 in both runs of every pair.

### `type=villagers`

A walled hall in every chunk of every plot (4 halls per plot, so 48 villagers at `plots-per-player=1`). Each hall has twelve unemployed adult villagers, twelve beds (two rows of six) and twelve composters as job sites. So every villager can hold a bed and a job site, and none can hold another villager's. The run only builds and waits; the villagers claim the places themselves.

Counters: `bed_claims` and `job_site_claims` (villagers whose `HOME` and `JOB_SITE` memory is set), `shared_beds` and `shared_job_sites` (places that more than one villager holds).

Vanilla takes a place in the same tick it chooses it, so a place is never held twice. With async pathfinding, `AcquirePoi` sets the memory after a path that arrives later and ignores the result of `PoiManager#take`, so two villagers can end up with the same bed or composter. Only the beds half was demonstrated on a buggy build.

The claims cannot be compared exactly. Both runs should end with 48 and 48 (supply equals demand per hall). When the last villager gets there depends on the random retry delay of the brain (20 to 40 ticks per attempt) and on how long an asynchronous path takes, so a run that ends a little early is a few claims short. `check.py` lets `bed_claims` and `job_site_claims` differ by 10 % of the larger value and requires `shared_*` to be exactly 0 in both runs.

Villagers outside the activation range only tick their timers, not their brain. The overlay `villagers-active` raises `entity-activation-range.villagers` to 128; use it for every villager run.

To use the type as a performance load (for example to compare optimisations of the villager sensors), run it with a warm-up and read the tick times, which land under `bases` in the result. The counters are then only the claims at the end and say nothing about speed. Every variant needs the `villagers-active` overlay or a copy of it with the setting under test added.

```bash
SCENARIO="type=villagers,players=2,plots-per-player=4,warmup-seconds=60,steady-seconds=90" \
    bash kindling-bench/matrix.sh <server.jar> villagers villagers-active
```

## Variants

A variant is a name. Its JVM arguments come from the `jvm` table in `matrix.sh`; without an entry it runs with `-Xms3G -Xmx3G` and G1. If a folder `overlays/<name>` exists, it is copied over the server before the start.

`SCENARIO=type=bases bash kindling-bench/matrix.sh <server.jar> <prefix> fork sleeping-be` runs variants in the bases scenario instead of the mixed one. `sleeping-be` switches on Leaf's sleeping block entities.

| Kind | Variants |
| --- | --- |
| JVM only (`matrix.sh`) | `coh` (compact object headers), `g1-2g`, `elastic`, `g1-tight`, `zgc`, `zgc-free`, `shenandoah` |
| One Leaf feature each | `only-tracker`, `only-pathfinding`, `only-cheap` (Leaf's synchronous optimisations), `only-dab`, `only-throttle` |
| One synchronous Leaf option each (`options-matrix.sh`) | `opt-cache-biome`, `opt-mob-spawning`, `opt-mob-despawn`, `opt-entity-activation`, `opt-waypoint`, `opt-powered-rails`, and `opt-all` (the five without `opt-powered-rails`). Left out: `reuse-random-ticking-blockpos` (shares one mutable position across calls) and `faster-random-generator` (changes the random sequence) |
| Candidate sets for Kindling's defaults | `cand-a`, `cand-b`, `cand-b-throttle`, `leaf-safe`, `leaf-aggressive` |
| The Kindling jar without an overlay | `fork` |
| Used by `elytra.sh` and `pregen.sh` | `workers-5`, `workers-7`, `vd-8` (view distance 8), `pregen-w5` (automatic pregeneration, 5 workers) |
| Worker priority and the pregenerator's main-thread wait throttle (A/B, Linux only) | `worker-priority-off`, `worker-priority-on` (chunk and I/O workers at nice 10) for flights. `pregen-w5-prio-off`, `pregen-w5-prio-on`, `pregen-w5-wait`, `pregen-w5-prio-on-wait`: like `pregen-w5` with `worker-priority` off or on and `max-main-thread-wait` 0 (off) or 0.10. Plain `pregen-w5` runs with the default `max-main-thread-wait: 0` |
| Hoppers with a move event listener (A/B, for sleeping hoppers) | `parity-sleeping-listener-on` (`performance.sleeping-block-entity: true`) and `parity-sleeping-listener-off` (`false`), each with `move-event-listener=true`. `parity/run.sh <jar> sleeping` runs both with the bases contraptions in their CI size and checks each against `bases-ci.json` |
| Functional check of the pregenerator's disk guard | `pregen-disk-guard` (like `pregen-w5` with `min-free-disk-gb: 100000000`): the run must stop by itself at the first tick and `pregeneration.properties` must stay at `next=0` |

Results land in `runs/<prefix>-<variant>.json` (a list of runs).

## Comparing a jar with Mojang's server

[`vanilla-diff/`](vanilla-diff/README.md) is not a performance test. It runs the official server jar and the jar under test on one seed with the same console script, then compares the saved worlds block by block and entity by entity (redstone contraptions, and a world generation mode). Its results are in the [vanilla parity measurements](../docs/measurements/vanilla-parity.md).
