# Pregeneration

[← Overview](../MEASUREMENTS.md)

The built-in pregenerator: its speed against its first version, its safety checks, what a stop during a run does, and where the chunk workers spend their time.

## The tiled pregenerator

**Indicative.** Chunks generated +59 % (19 040 → 30 315), CPU per chunk −41 % (47 → 28 ms). Against the ring-by-ring first version, Kindling 26.3-1, 2 runs each. Plain Leaf has no pregenerator, and a comparison with Chunky on Leaf was not measured.

`/pregen start <world> <radius> [<x> <z>]`, `pause`, `resume`, `cancel` and `status`, with an optional automatic start per dimension ([commands](../reference/commands.md), [settings](../reference/configuration.md)). No Chunky needed. It runs at the chunk system's lowest priority, so chunks players need always come first. It throttles itself by tick time and resumes after a restart. It works in 16 × 16 chunk tiles and keeps the half-generated neighbours in memory instead of saving and re-reading them. It costs 5 ms per tick while it runs. The throttle by the main thread's wait for a CPU is measured with [worker priority](host.md).

<details>
<summary>Method and all numbers</summary>

Measured with the pregenerator alone: one player standing at spawn, radius 3 000 blocks, 8 cores, 5 workers, 2.5 minutes. Two runs each, interleaved with Kindling 26.3-1 (ring order).

| Variant | Chunks generated | Tick time avg / p95 (ms) | GC time | CPU per chunk | Heap after GC |
| --- | ---: | ---: | ---: | ---: | ---: |
| Kindling 26.3-1 | 19 040 | 7.2 / 13.8 | 8.0 s | 47 ms | 637 MB |
| Kindling 26.3-2, tiles and palette decoder | **30 315 (+59 %)** | **5.1 / 7.8** | **1.9 s** | **28 ms (−41 %)** | 604 MB |

The pregenerator generates a square around a centre in tiles of 16 × 16 chunks: the tiles ring by ring from the centre tile, the chunks of a tile row by row, at `Priority.IDLE`. While the average tick time is above `max-mspt` it halves its chunks in flight and starts no new chunk (the chunks already in flight finish); while it stays below three quarters of that, it adds one per second, up to `max-parallel`. `/pregen status` names the reason while it waits (`throttled: 43 ms average > 40 ms`). On Linux it can also throttle the same way while the main thread waited for a CPU more than `max-main-thread-wait` of the last minute, as the doctor's host monitor reads it. That limit is off by default (0): on kindling-bench, 3 runs each, 0.10 cut pregeneration alone from 140.3 to 65 chunks/s (runs of 47, 76 and 72; [numbers](host.md#pregenerator-wait-throttle); overlays `pregen-w5-prio-off`, `pregen-w5-wait`), and on the VPS the main thread of a nearly idle test server (3 to 6 s of CPU in 181 s) already waited 11 to 14 % of its time, so 0.10 throttled at baseline. The world is the same as without pregeneration (vanilla generator, same seed).

**Why tiles.** Generating a chunk to `FULL` also generates its neighbours part of the way (up to eleven chunks out, mostly only to the structure stage). The first version went ring by ring and released every chunk right after it was done, so those half-generated neighbours were saved and unloaded, then read back from disk when the next ring needed them, often several times per chunk. The profile showed 14 % of the worker time in parsing such chunks, and 7 % of the main thread in copying chunks for saving while unloading them.

The generated chunks of the last two tiles keep a ticket one level below full, so the next tile finds their neighbours still in memory. One level below full means a kept chunk leaves the world right after generation exactly as an unloaded one does (unload event, entities hidden: not ticked, not counted for mob caps, not selectable by commands); only its data stays in memory. Older tiles are let go, so memory stays bounded.

**Resuming.** A run saved by 26.3-1 (no `order` key in `pregeneration.properties`) continues at the first tile ring beyond the rings it had finished; chunks that are generated a second time this way only load from disk. A restart continues at the oldest chunk still in flight, so no chunk is skipped when chunks finished out of order.

**Next to fliers.** While players fly, the pregenerator steps back, because the chunks players ask for come first; the tile order does not change that throttling.

</details>

## Hardening checks

**Correct.** Nine functional checks show that the safety behaviour holds: border, crash, disk, status, stop, empty server, unloaded world. None is a speed measurement. No baseline (expected behaviour), one run per check.

The safety behaviour is covered by unit tests, not a measured speed-up:

- *Border and radius.* The radius is cut to the world border when a run starts, and one tick walks at most 4 096 indices (a whole tile outside the border counts as one): a radius of 30 000 000 blocks next to a 10 000 block border does not hold the tick for hours.
- *Failure.* A run that throws is stopped and saved as paused; the other runs and the server tick go on.
- *Worlds.* A run waits while its world is unloaded and continues when the world is loaded again (also a world that appears later). The saved run carries the world's seed; a run saved for another seed is dropped.
- *State file.* It is written on a thread of its own from a snapshot (no fsync on the main thread) and once, synchronously, while the server stops, with the exact index. Between those, the saved index steps back over the kept tiles and the chunks in flight, so a crash costs a few chunks that load from disk instead of leaving holes.
- *Disk.* Before a run starts and every 30 seconds it compares the free space of the world's disk with what the rest of the run needs, estimated from the space the disk lost per delivered chunk since the run (re)started (16 KiB per chunk assumed for the first 2 048 chunks), and stops when less than `min-free-disk-gb` would remain; it continues once there is room again.
- *Empty server.* While a run is working the server does not pause for lack of players (`pause-when-empty-seconds`); a paused, stopped or finished run does not hold it awake.

A second hardening round is covered by unit tests only (`PregenStateTest`, `PregenTaskTest`, `TaskSupervisorTest`, `StateStoreTest`, `StateWriterTest`); there is no server check yet. It contains these changes: an automatic run cut short by the border counts as done, a failed run stays paused and resumable, a full disk is re-checked while an empty server sleeps, the state loads before the first write, the final write never races another, and a radius above 3 750 000 chunks is dropped as damaged.

<details>
<summary>Method and all numbers</summary>

Functional checks of the safety changes above, run on the bench host with fresh worlds, one run per check, the console driven from a script (`-Xms2G -Xmx2G`, 8 CPUs; the check's own `leaf-global.yml` with `max-mspt: 40` and `max-parallel: 32`, the worker setting of `pregen-w5`).

| Check | Result |
| --- | --- |
| Border 2 000, `/pregen start world 30000000` | Reply at once: "radius 89 chunks (1424 blocks) ... limited by the world border from 1875000 chunks". 43 264 chunks in 111 s, worst console round trip 0.51 s, no watchdog line, stop in 1.0 s |
| Border 10 000, the same command | 473 344 chunks planned, worst console round trip 0.50 s over 75 s, about 126 chunks/s, no watchdog line |
| `kill -9` after about 60 s (radius 1 500), restart | The state file held index 6 068 while the status showed 7 407; the run continued at 6 068 (a lower index than before the kill). It finished 201 s later; all 34 969 chunks of the square were in the region files at status full, none missing (every chunk's NBT read). A second run (radius 1 000) was killed twice before the first save at 30 s and restarted from 0 both times: 15 625 chunks, none missing. A weaker test, since the file never advanced |
| Disk guard (reserve set impossibly high) | Stopped at the first tick with the log line naming free space, the estimate and the reserve; `/pregen status` ended in "paused: the disk would run out" with a `disk:` line; the state file kept index 0, state running and a seed key |
| Status names the throttle | "throttled: 1 ms average > 1 ms" with `max-mspt: 1` on an idle server; under a flight of 5 players "throttled: 42 / 44 / 48 / 49 / 55 ms average > 40 ms" with the chunks in flight down to 1. With the default 40 ms on an idle server no throttle text appeared. The pregenerator stood still at 14 215 chunks for the last 75 s of the flight, as the chunks players ask for come first |
| Seed key and measured disk figure | The seed is in every state file; "disk: ... measured" appears after 2 048 chunks and gave 4 to 10 KiB per chunk against the assumed 16 KiB |
| Clean stop during a run (border 10 000) | Stop in 1.4 s; the file held the exact index 17 152 (last status 16 899, the crash-safe value before was 14 736); the restart continued at 17 152 |
| Empty server (`pause-when-empty-seconds: 5`) | No pausing for 75 s while the run worked; 5 s after `/pregen pause` the "Server empty ... pausing" line appeared |
| World unloaded and loaded again | A warning on unload; "Pregeneration continues: minecraft:pw: 0 / 12,544" on reload, finished with state done. It started again at 0 because an unload saves the crash-safe (stepped-back) index; chunks already done only load from disk |

**Not done.** The hang of the unbounded skip loop (K1) was not reproduced as such, because the border cannot be set from an overlay; the 2 000 and 10 000 border runs did not stall. The step-back distance of a crash (two tiles plus the chunks in flight) was not compared with the in-memory index at the moment of a save: the two kills either came before the first save or left a gap of about 1 300 chunks.

</details>

## Stop during a pregeneration

**Same.** Overworld save at the stop: 0.34 to 1.10 s on release 26.3-11, 0.50 to 0.59 s on build `cd56f6c7` except one 5.05 s save; one off-main POI trace on that build. Against release 26.3-11, 4 stops each.

This measures what a stop does while a pregeneration has chunks in flight: how long the overworld save takes and whether the shutdown logs an error. One shutdown race in upstream code can log an error stack trace; see the details.

<details>
<summary>Method and all numbers</summary>

Fresh world, no bench plugin, `/pregen start world 1500`, then `stop` 60 s later. Release 26.3-11 and build `cd56f6c7` each stopped once in the round and three more times afterwards, alternating.

| Figure | 26.3-11, 4 stops | Build `cd56f6c7`, 4 stops |
| --- | --- | --- |
| Overworld save at the stop | 0.34, 1.10, 0.52, 0.47 s | 5.05, 0.54, 0.59, 0.50 s |
| "Accessing poi chunk off-main" | none | once (in the last stop) |

The slow save did not come back in three more stops; its cause was not looked into.

**A shutdown race in upstream code.** Stopping the server while a pregeneration ran can log an ERROR stack trace: "Thread failed main thread check: Accessing poi chunk off-main".

- *Where it was seen:* first while a beehive was generated, then once in the stops above while a jigsaw structure piece (a village part) was placed. The server stopped cleanly both times.
- *The path, all of it vanilla, Paper or Moonrise code:* a chunk worker generating features sets a block through `WorldGenRegion.setBlock`. `ServerLevel.updatePOIOnBlockStateChange` then hands the POI update to the server with `execute`. Once the server is stopping, that runs the task at once on the calling worker. `PoiManager.getOrLoad` then fails the main-thread check, and the server logs "Error executing task on Server".
- *Kindling:* no Kindling frame is in the stack. A pregeneration only makes it likely that generations are in flight at the stop, as any generation would.
- *Frequency:* once in four stops of build `cd56f6c7` and in none of four stops of release 26.3-11. That does not tell the builds apart.
- *Not checked:* whether the skipped POI update for that block is repaired when the chunk loads again.

</details>

## Frontier pregeneration

**Not measured.** Built and opt-in (`performance.pregeneration.frontier.enabled`, default `false`; see [settings](../reference/configuration.md)). No speed or disk figure exists. It is covered by 61 unit tests with a fake world (`TileSetTest`, `FrontierPlanTest`, `FrontierStateTest`, `FrontierTaskTest`, `FrontierPregenTest`); nothing ran on a server.

While at least one player is online, every 20 ticks the 16 × 16 chunk tile each player stands in is noted (a hash-set add per player, no per-move hook) and saved in `<world>/pregeneration-frontier.properties`. With nobody online the server stays awake and generates the tiles within `margin-chunks` (default 32, rounded up to whole tiles, so 5 × 5 tiles around each visited tile; clamped to 0 to 512) that are not done yet, nearest first, one tile at a time (256 chunks, chunk-system priority `IDLE`). Chunks outside the world border are never requested. It reuses the pregenerator's throttle (`max-mspt`, `max-parallel`, `max-main-thread-wait`), disk guard (`min-free-disk-gb`; the forecast counts every pending tile as still to be written, so it is conservative) and kept-chunk tickets. It pauses and releases its tickets on the first tick with a player online, and leaves a world to an explicit or automatic `/pregen` run. A failure switches off only that world's frontier until restart. `/pregen status` shows `frontier <world>: N tiles visited, M generated`.

**The world folder grows.** With the default margin each isolated visited tile adds up to 25 tiles, 6 400 chunks; at the disk guard's 16 KiB-per-chunk assumption (not measured for this feature) about 100 MiB. Generation is deterministic, so the world's content does not change, only when it is generated.

<details>
<summary>Caveats</summary>

- A tile drains its in-flight chunks before the next starts, which should cost some throughput against `/pregen`'s continuous spiral; not measured.
- A chunk the chunk system returns as not loaded still counts toward its tile, and the tile is then saved as done.
- The visited tile's chunks are mostly generated already and are only read again.
- Nothing ran on a server: join, move, log out, join again, restart and `/pregen start` on the same world need a manual check.

</details>

## Where the time goes in the chunk workers

**Indicative.** Two thirds of the workers' CPU builds terrain (66.8 %), and the density compiler evaluates both branches of every range choice, throwing away 7 to 10 % of all worker CPU that way. A profile of a build of 26.3-15 with measurement counters, the pregenerator alone, 240 s, 2 runs on the bench host.

**Chunk workers while the pregenerator runs alone** (inclusive shares of the five workers' samples, 1 409 worker-seconds in run `a`; run `b` within a point):

| Share | Where |
| ---: | --- |
| 66.8 % | building terrain (`ChunkStatusTasks.buildTerrain`): 36.9 % noise fill (`NoiseBasedChunkGenerator.doFill`; 16.1 % interpolation, 10.8 % aquifers, 10.3 % Perlin noise), 23.3 % surface and ore rules (`MaterialSystem.buildSurface`, compiled; 3.3 % of it in biome lookups) |
| 19.5 % | inside range choices of the density functions (`RangeChoiceFunction$Sampler.sampleVolume`, counted once per sample; part of the two rows above) |
| 17.3 % | features |
| 4.9 % | biomes (`Climate` lookups 4.4 %) |
| 3.7 % | light |
| 1.5 % + 1.2 % | saving (`SerializableChunkData.write`) and parsing loaded chunks (`parse`): the 14 % of the first version is gone since [the tile order](#the-tiled-pregenerator) and [the palette decoder](chunks.md) |
| 0.8 % | structure starts |

Of the 2 025 CPU seconds of the run the workers used 1 409 and the main thread 40; the rest is garbage collection, I/O and start-up. Flying players give the same worker picture (`buildTerrain` 69 %, range choices 21 %).

### Discarded density branches

**Indicative.** About 103 s of the workers' CPU (7.3 %, or 5.1 % of the process) goes into branches of range choices that the point did not choose. Same profile build as above, 2 runs; the second run gives the same shares.

`RangeChoiceFunction.Sampler.sampleVolume` evaluates the in-range branch, then the input, then the out-of-range branch for the whole volume and picks per point. The timers below sit around those three calls; the overworld's router has 15 such samplers. Times are summed over all workers; a nested range choice is counted in its parent too, so the rows do not add up. The compiled density patch described in [chunks](chunks.md) acts on this finding.

| Range choice (overworld router) | Calls | Points | In range | In branch | Out branch | Input |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `caves`: `slopedCheese` in −10⁶..1.5625 → surface, else the underground subtree (cell corners) | 75 292 | 86.8 M | 71.1 % | 110.3 s | 46.5 s | 0.1 s |
| noodle caves: toggle < 0 → 64, else thickness + ridges (block resolution) | 75 292 | 6 915 M | 49.5 % | 2.4 s | 61.5 s | 15.5 s |
| ore veins, copper: y in 0..50 → the vein chain, else −1 (block resolution) | 279 294 | 2 506 M | 35.9 % | 40.2 s | 0.4 s | 3.9 s |
| ore veins, iron: y in −60..−8 | 279 294 | 2 506 M | 37.4 % | 10.4 s | 0.4 s | 3.9 s |
| ore vein mask: toggle in −0.4..0.4 → −1, else 0.08 − max(vein A, vein B) | 279 294 | 2 506 M | 80.7 % | 0.9 s | 17.2 s | 1.3 s |
| the inner vein gates (two) | 279 294 | 2 506 M | 0.8 % | 14.0 s + 7.0 s | 0.4 s | 23.3 s + 0.7 s |
| pillars, the y limits of noodle and vein noises (seven more) | | | 84 to 98 % | | | below 0.3 s wasted each |

What is certainly wasted, counting only a branch's own time at the points that chose the other branch:

| | Wasted | How |
| --- | ---: | --- |
| `caves` | 33 s | the underground subtree (about a dozen noises) for the 71 % of cell corners above the terrain |
| noodle caves | 30 s | thickness and ridge interpolation per block for the half of the blocks whose toggle is negative |
| ore veins | about 40 s | the vein chain outside the y range of each type (64 %), the vein noises where the toggle is in the dead band (81 %), the inner gates for the 99 % of blocks with no vein |
| together | 103 s | 7.3 % of the workers' CPU, 5.1 % of the process |

The `caves` row's in-branch time is misleading on its own: `surfaceWithEntrances` is cheap, but the shared `entrances` cache is filled during its evaluation and reused by the underground branch, so the 110 s belong to both branches. Counting the in-branch time at out-of-range points as wasted too gives 135 s, 9.6 % of the workers. The second run gives the same shares (71.1 % and 49.7 %; 326 s inside range choices).

<details>
<summary>Method and all numbers</summary>

`run_bench.py --overlay overlays/pregen-w5` with the pregenerator's "alone" scenario (`players=1,blocks-per-tick=0,warmup-seconds=20,travel-seconds=240,mobs-per-player=0`), `-XX:StartFlightRecording=settings=profile,dumponexit=true -Dkindling.measure=true`, `-XX:ActiveProcessorCount=8 -XX:+UseCompactObjectHeaders -Xms3G -Xmx3G`, two runs interleaved with the flight runs (`measure-pregen-a`, `-b`, 68 096 and 67 072 chunks in 240 s). The measurement build turns `RangeChoiceFunction.Sampler` into a class with `LongAdder` counters around its three `sampleVolume` calls and the per-point choice; it reports every 60 s and at shutdown. The JFR recording was read with the same `jdk.jfr.consumer` program as the main-thread profile. The branch timers include the timers of nested range choices, the JFR share counts a sample once however deep the nesting. See [kindling-bench](../../kindling-bench/README.md) for the bench.

</details>

---

[← Overview](../MEASUREMENTS.md)
