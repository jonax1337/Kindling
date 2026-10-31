# World ticking

[← Overview](../MEASUREMENTS.md)

What the main thread spends its ticks on: sleeping block entities, the Lithium ports, natural spawning and the lagging check, with the profile and the ideas that were left alone.

## Sleeping block entities

**Faster.** Survival bases: average tick time 12.3 → 11.3 ms (−8 %, p = 0.001), all 15 counters identical.
Sleeping block entities on against off, build `cd56f6c7` (the build with the Lithium ports and the fixes), 5 runs each.

Leaf's sleeping block entities (from Lithium) are on by default (`performance.sleeping-block-entity`): an idle hopper, furnace, brewing stand, crafter or chest stops ticking until something can happen, and exactly what lets it act again wakes it (an inventory change, an item or a minecart arriving, a redstone change). Kindling's patch `0322` makes them act as on Paper where Leaf's port did not: plugins that rewrite a transfer (`InventoryMoveItemEvent#setItem` duplicated 652 items in the parity test), tuned `spigot.yml` hopper rates (906 instead of 2 751 items on a hopper chain), blocks set without block updates, stands and furnaces a plugin holds back, and Paper's cooldown after a failed transfer. A hopper takes Lithium's shortcut only when no plugin listens to `InventoryMoveItemEvent`; with `ticks-per.hopper-check` above 1, hoppers stay awake, because a sleeping hopper cannot keep Paper's check interval.

<details>
<summary>Method and all numbers</summary>

The [mixed load](vs-leaf.md) has hardly any block entities, so this is measured in the `kindling-bench` bases scenario: 8 players among 32 plots of hopper chains, furnace rows, redstone boards and a workshop of item sorters, minecarts, a composter, a double chest, brewing stands and a crafter ([how it works](../../kindling-bench/README.md)). Warm-up 60 s, measured 90 s, 3 GB heap, bench host.

**Sleeping block entities on against off.** Overlays `parity-sleeping-on` and `parity-sleeping-off`, 5 runs each:

| Figure | Off | On | p |
| --- | ---: | ---: | ---: |
| Tick time avg | 12.3 ±0.4 | **11.3 ±0.1** | 0.001 \* |
| p95 | 16.8 ±1.1 | 15.6 ±0.4 | 0.040 |
| p99 | 21.6 ±2.0 | 20.7 ±1.4 | 0.34 |
| CPU time | 367 ±12 s | 376 ±27 s | 0.42 |

The option saves 8 % of the average tick time. The p95 points the same way but misses the gap rule.

**Parity counters.** The parity scenarios `type=sleeping` ([bench README](../../kindling-bench/README.md)) run the same contraptions with the option on and off and require equal counters; the plugin stand-ins are real event listeners. The counters are deterministic, so a pair ran once per build, and twice on the release (identical both times). Release 26.3-11 against the build with the fix:

| Counter | Release, on | Release, off | Fixed build, on | Fixed build, off |
| --- | ---: | ---: | ---: | ---: |
| `rewrite_arrived` (plugin rewrites the moved item) | 652 | 80 | 80 | 80 |
| `rewrite_left` | 0 | 80 | 80 | 80 |
| `rewrite_duplicated` | **652** | 0 | 0 | 0 |
| `placement_target_arrived`, `placement_source_arrived` (blocks placed without physics) | **0**, **0** | 20, 20 | 20, 20 | 20, 20 |
| `gate_brewed_potions` (plugin holds back the fuel) | **0** | 6 | 6 | 6 |
| `gate_smelted_items` (plugin holds back the furnace) | 12 | 12 | 12 | 12 |
| `hopper_chain_items`, default rates | 453 | 453 | 453 | 453 |
| `hopper_chain_items`, `hopper-amount: 3`, `ticks-per.hopper-transfer: 4` | **906** | 2 751 | 2 751 | 2 751 |

On the release the tuned pair also showed `rewrite_arrived` 1 304 against 80. `hopper_stuck_items` was 70 and `hopper_idle_items` 0 in the fixed build's four runs. The release's sleeping hoppers therefore duplicated items, ignored both tuned rates (−67 % on the chain) and stayed blind to blocks placed without block updates; the fixed build agrees with Paper on all ten counters in both pairs (`check.py --pair`: "parity ok").

- **Guarded bases** (`hopper-guard-seconds=10`, a stand-in claim plugin): release and fixed build agree on all 15 counters, one run each.
- **The bases check** (`parity/run.sh`, 15 counters against `bases-ci.json`): all counters matched on every build tested. The script itself exited 1 until the golden file's scenario block had the four fields the bench plugin writes (`chunkAcks`, `gameMode`, `warmupMaxSeconds`, `warmupQuietSeconds`); that is fixed in the repository.
- **Bench rounds of the fixed builds:**
  - the build with chunk quarantine: bases, sleeping (both pairs) and villagers parity all passed;
  - the build `cd56f6c7` against release 26.3-11 and plain Leaf (3 runs each): the same checks passed again (`parity/run.sh <jar> all`).

**Plugins that hold a transfer back.** Leaf's port wakes a hopper only when one of its inventories changes. A plugin that holds a transfer back, such as a claim plugin cancelling `InventoryMoveItemEvent` or a shop plugin hiding a chest through `HopperInventorySearchEvent` until its data has loaded, therefore left the hopper asleep until someone touched the chest, while Paper asks the plugin again on every attempt. Kindling's patch keeps such a hopper awake and asking, as on Paper, and honours Paper's `PrepareHopperTransferEvent`, which the port skipped. `hopper-guard-seconds=10` in the bench is a stand-in plugin that holds every hopper back for the first 10 seconds after the build and then lets everything through:

| Variant (with the stand-in plugin) | Hopper chains, furnaces, minecarts, composter, double chest, crafter, brewing stands |
| --- | --- |
| option off | run normally |
| Leaf's port | **0**: stuck for good after the plugin let go |
| **Kindling** | identical to option off |

**Speed against the release.** Bases scenario, release 26.3-11 against the fixed build, sleeping on. The option is on by default in both, so the runs with and without the `sleeping-be` overlay are the same condition and are pooled: 6 runs per build, interleaved.

| Figure | Release | Fixed build | p |
| --- | ---: | ---: | ---: |
| Tick time avg | 12.6 ±0.4 | 12.7 ±0.3 | 0.60 |
| p95 | 19.4 ±2.0 | 18.9 ±1.9 | 0.56 |
| p99 | 29.0 ±5.1 | 31.2 ±2.4 | 0.23 |
| CPU time | 357 ±10 s | 364 ±14 s | 0.23 |
| GC pause | 1 111 ±121 ms | 1 077 ±58 ms | 0.44 |

No difference is significant: the fix does not slow the bases down by 0.5 ms (about 4 %) or more; a smaller slowdown cannot be excluded with six runs.

**Option off on the fixed build.** Three runs: 14.3 ±1.3 ms against 12.7 ±0.3 ms with the option on (p = 0.20; p95 20.9 against 18.9, p = 0.65). The direction is the same as in the 5-run pair above, but not significant on its own.

The absolute tick times differ between sessions (7 to 13 ms in the same scenario), so only comparisons from the same session count.

</details>

<details>
<summary>Caveats</summary>

- **The furnace case did not reproduce.** `gate_smelted_items` is 12 with sleeping on and off on the release too, so this scenario cannot show the fix for furnaces that a plugin holds back. The furnaces smelt 12, not 10; the bench README says 12.
- **Five late fixes.** Five fixes landed after the chunk-quarantine round: the hopper cooldown after a failed transfer (Paper's `cooldown-when-full`), the removal of Lithium's nitwit shortcut, the happy ghast's adult sensor, the idle-memory pause during a pregeneration and the publication of the async saving writer. The jar of the last bench round contains all five, but its scenarios exercise only the hopper cooldown (the sleeping pairs and the bases). The other four rest on unit tests and review.
- **Listeners.** With an `InventoryMoveItemEvent` listener (common with protection plugins), awake hoppers take Paper's transfer code instead of Lithium's shortcut so that plugins see what Paper gives them. The plain bases scenario registers no such listener; that cost was not measured.
- **Pick-up order.** Where dropped items pile up on hoppers while they are held back, the order they are picked up in afterwards is not fixed: two runs of an earlier build differed by 10 of 12 140 sorter items and 1 of 30 266 caught items, and the second matched option off exactly.
- **Sleeping hoppers and plugins.** A plugin that starts redirecting a hopper while the hopper sleeps with nothing to do is asked once one of the hopper's inventories changes, not every tick.

</details>

## Lithium ports

**Faster.** Bases tick time 12.0 → 11.2 ms (p = 0.016), p95 17.0 → 15.3 ms, p99 24.4 → 19.8 ms. Against the build with the spawn-skip patch (`0325`), 3 runs each. The gain does not show against release 26.3-11 (11.7 ms in both).

Patch `0326` ports four things from Lithium with the vanilla result: the redstone wire power calculation, skipping sensors a mob does not use (baby sensors on adults, the goat's item sensor), an early exit from the rain tick, and building the framed map's player name only where it is used.

<details>
<summary>Method and all numbers</summary>

A differential test compares the wire power with vanilla's algorithm in 4 000 random surroundings. Bases scenario (8 players × 4 plots, 60 s warm-up, 90 s measured), the spawn-skip build against the Lithium build, 3 runs each, interleaved:

| Figure | Spawn-skip build | Lithium build | p |
| --- | ---: | ---: | ---: |
| Tick time avg | 12.0 ±0.2 | **11.2 ±0.1** | 0.016 \* |
| p95 | 17.0 ±0.5 | **15.3 ±0.2** | 0.021 \* |
| p99 | 24.4 ±1.2 | **19.8 ±1.0** | 0.011 \* |
| GC pause | 146 ms | 116 ms | 0.27 |
| Main-thread wait | 17.4 ms | 18.7 ms | 0.86 |
| CPU time | 368 s | 365 s | 0.59 |
| Peak RSS | 2 742 MB | 2 725 MB | 0.74 |

All 15 counters were identical in all six runs. That is about −7 % average, −10 % p95 and −19 % p99.

Against release 26.3-11 the gain does not show. Bases scenario, 3 runs each: the build `cd56f6c7` (the ports and the fixes up to it) ran the bases at 11.7 ±0.4 ms, release 26.3-11 at 11.7 ±0.1 ms (p = 0.99); p95 17.0 against 16.4 ms, p99 23.2 against 22.8 ms, CPU time 376 ±5 against 371 ±2 s, all 15 counters identical. Of the fixes in that build, only the hopper cooldown after a failed transfer changes what the bases tick. Whether that change takes the gain back was not measured.

Villagers (the sensor port): 2 players × 4 plots, `villagers-active`, 60 s warm-up, 90 s measured, 3 runs each: tick time avg 8.2 ±0.0 against 8.5 ±0.3 ms (p = 0.18), p95 9.6 against 10.9 (p = 0.17), p99 13.2 against 14.7 (p = 0.26), CPU 230 against 226 s (p = 0.16). No difference; the scenario's villagers are adults, so the skipped sensors had nothing to save there. Claims stayed at 384 and 384 with 0 shared.

</details>

<details>
<summary>Caveats</summary>

The jar differs from the spawn-skip build by all four ports, so the A/B does not separate them; the redstone wire calculation is the likely source. The build was measured with a Lithium shortcut for the nitwit's job search that was removed afterwards (it skipped calls to the level's random, so it was not vanilla-identical); the bases scenario has no villagers, so the result should not depend on it.

The goat sensor, the rain and ice early exit and the framed maps are not covered by any scenario.

</details>

## Natural spawning: skipped spawn cost lookups

**Same.** Flight tick time 27.6 → 26.6 ms (p = 0.084), equal spawn counts per kind.
Against the build before patch `0325`, 3 runs each.

Patch `0325` skips the biome lookup for entity types that have a spawn cost in no biome and keeps a one-entry memo of the spawn lookup at a position; the result is unchanged. The memo avoids resolving the same position up to three times per attempt.

<details>
<summary>Method and all numbers</summary>

Mixed load, survival mode, 8 players, the build before patch `0325` against the build with it, 3 runs each, interleaved, bench host:

| Figure | Before the patch | With the patch | p |
| --- | ---: | ---: | ---: |
| Flight tick time avg | 27.6 ±0.1 | 26.6 ±0.6 | 0.084 |
| Flight p95 | 64.0 ±3.8 | 62.4 ±0.8 | 0.54 |
| Flight, main thread running time | 43 829 ±396 ms | 42 262 ±694 ms | **0.041 \*** |
| Flight, main-thread wait | 5 117 ±347 ms | 4 981 ±316 ms | 0.65 |
| Standing tick time avg | 36.9 ±0.6 | 37.8 ±0.6 | 0.15 |
| Standing p95 | 66.2 ±1.3 | 67.2 ±5.8 | 0.80 |
| Entities at the end | 3 077 ±29 | 3 047 ±24 | 0.26 |

The tick times are not different. One row is marked significant (the main thread's CPU time in the flight, −3.6 %), out of about 35 rows compared; that is weak evidence and no speed-up is claimed. What the test is for is the spawn counts: the bench plugin only reports a total, so the entities saved in the world at the end of each run were counted per kind with a script (own mapping of entity ids, saved state of the whole world rather than the live mob caps): monsters 959 ±7 against 955, animals 1 670 ±12 against 1 657, ambient 124 against 124, water 222 ±4 against 227, villagers 153 against 158; none differs (p between 0.12 and 1.0).

</details>

<details>
<summary>Caveats</summary>

The counts are consistent with a change that keeps vanilla's spawning; they cannot prove it.

</details>

## Cheaper natural spawn tries

**Same** in tick time (flight 37.4 → 37.1 ms, p = 0.47); two small marked rows: the main thread waits 16 % less for a CPU, and 2 % more chunk packets are sent.
Against release 26.3-15, mixed load, 2 GB heap, 3 interleaved runs each.

Patch `0343` removes what the allocation profile in [Where the time goes: main thread](#where-the-time-goes-main-thread) showed per chunk and category: the two bound method references and the consumer passed into `spawnCategoryForChunk` live in the spawn state and the chunk map, the nearby players are looked up once per chunk instead of once per category (and again after a category that ran plugin code: a spawn, or `PreCreatureSpawnEvent` with listeners), the spawn-point distance is checked without a `Vec3` and the chunk without a `ChunkPos` when the position lies in the chunk itself, and the `NATURAL_MOB_SPAWNS` sampler is kept instead of hashed for on every lookup. Every check keeps its result.

<details>
<summary>Method and all numbers</summary>

Labels `cand-a` (the 26.3-15 release jar, whose code equals the base commit), `cand-b` (plus Paper patch `0075`, the lazy ticking-list map) and `cand-c` (plus patch `0343`), `players=8,mobs-per-player=150,travel-seconds=90,steady-seconds=90,gamemode=survival`, `-XX:ActiveProcessorCount=8 -XX:+UseCompactObjectHeaders -Xms2G -Xmx2G -Xlog:gc:file=gc.log`, three rounds a, b, c, a, b, c, a, b, c on the bench host; `compare.py cand-a cand-b cand-c`, whose p-values compare each label with the first.

| Figure | 26.3-15 | + lazy ticking-list map | + this patch | p (against 26.3-15) |
| --- | ---: | ---: | ---: | ---: |
| Travel tick time avg | 37.4 ±0.0 | 37.3 ±0.5 | 37.1 ±0.5 | 0.47 |
| Travel p95 | 62.6 ±1.3 | 64.4 ±0.6 | 61.3 ±1.9 | 0.42 |
| Travel main-thread wait | 4 434 ±269 ms | 3 991 ±292 ms | 3 741 ±165 ms | 0.027 \* |
| Chunk packets in the flight | 9 987 ±59 | 9 979 ±106 | 10 178 ±61 | 0.022 \* |
| New chunks per second | 144.5 ±1.2 | 144.7 ±1.9 | 147.0 ±0.8 | 0.069 |
| Standing tick time avg | 35.7 ±0.3 | 36.0 ±0.6 | 35.1 ±2.4 | 0.72 |
| CPU time | 1 246 ±8 s | 1 245 ±7 s | 1 240 ±20 s | 0.68 |

No tick-time row moves. The marked rows (less main-thread wait, more packets, a 0.1-chunk longer lead) point the same way and are small; with 37 rows compared, they are quoted next to the rows that did not move and not claimed as a speed-up. The patch stays because it is exact and costs nothing.

</details>

## The lagging check of the tick loop

**Correct.** Same value; the TPS averaging fell from 13.6 % to 3.4 % of the main thread on an idle server (JFR), too small to show in the tick time.
Against the build without the change, one player at spawn for 2 minutes.

Purpur's lagging check (`lagging = TPS < lagging-threshold`) runs every tick and asked for all TPS averages, although it only uses the 5-second one. Kindling computes only the 5-second average there; the value is the same. The same change is proposed to Leaf as [pull request #970](https://github.com/Winds-Studio/Leaf/pull/970).

<details>
<summary>Method and all numbers</summary>

Main thread samples in the TPS averaging, JFR, one player at spawn for 2 minutes, 8 cores:

| Run | Before | After |
| --- | ---: | ---: |
| Idle server | 13.6 % | 3.4 % |
| Pregenerating | 2.9 % | 1.5 % |

The tick before has always cleared the cached averages, so every tick walked the 1, 5 and 15 minute windows, about 25 000 ticks.

In a profile of our SMP server (one player, 15 minutes) this was 8.6 % of the main thread.

The average tick time of an idle server is too noisy at this level (2.6 / 2.0 ms before, 1.8 / 2.5 ms after) to show it.

</details>

## Where the time goes: main thread

**Indicative.** Natural spawning is a third of the main thread while players explore (33.3 %) and 6 % while they stand; the standing phase is entity ticks (75 %) with no single hotspot above 3.1 %.
JFR `profile` settings on a build of 26.3-15 with measurement counters, mixed load, 3 GB heap, 2 runs.

Inclusive shares of the main thread's samples. The travel window is the 90 s flight, the standing window the 90 s among the mobs afterwards; the two runs agree within a point and the first is shown.

| Where | Travel | Standing |
| --- | ---: | ---: |
| `ServerChunkCache.tickChunks` (spawning, random and scheduled ticks) | 49.9 % | 17.6 % |
| of which natural spawning (`NaturalSpawner.spawnForChunk`) | 33.3 % | 6.1 % |
| of which the biome lookup of a spawn try (`EnvironmentAttributeSystem.getValue` → `BiomeManager.getBiome`) | 10.1 % → 9.0 % | 1.7 % |
| of which picking the mob (`getRandomSpawnMobAtWithChunk`) / the placement checks (`isValidSpawnPostitionForTypeWithChunk`) | 8.7 % / 6.0 % | 1.3 % / 0.2 % |
| of which the per-tick mob count (`createState`) | 1.6 % | 3.4 % |
| scheduled block ticks (`LevelTicks.tick`) | 10.6 % | 3.0 % |
| random ticks (`iterateTickingChunksFaster`) | 9.6 % | 5.0 % |
| entity ticks (`EntityTickList`) | 23.8 % | 74.7 % |
| of which goals / brains / pathfinding / pushing | 4.2 / 1.5 / 1.4 / 0.8 % | 13.9 / 9.0 / 5.3 / 5.2 % |
| of which the neighbour loop of the path evaluator (`checkNeighbourBlocks`) | 0.4 % | 1.1 % |
| Leaf's `ChunkCache.get` (chunk lookups from everywhere) | 8.4 % | 5.0 % |
| chunk tickets and the player loader (`processTicketUpdates`, `RegionizedPlayerChunkLoader`) | 5.1 % + 3.0 % | 0.8 % + 0.1 % |

The 1.1 points between the attribute lookup and the biome lookup are what the 26.3 `EnvironmentAttributeSystem` adds on top of the biome zoom (three map lookups where 1.21 needed one). The spawner's share while exploring is the measured basis for the candidates in [the roadmap](../ROADMAP.md) (pre-computing the read-only checks off the main thread, and the small exact costs); while players stand it is not worth a patch. The pathfinding neighbour loop at 1.1 % caps what a per-section danger counter could give.

**What the main thread allocates** (JFR allocation samples, whole run, 36 GB sampled weight in about 5.5 minutes):

| Share | Site or class |
| ---: | --- |
| 20 % / 15 % / 10 % | `AABB` / `Vec3` / `BlockPos` objects |
| 6.8 % + 4.3 % + 2.1 % | `BlockGetter.forEachBlockIntersectedBetween`, `Entity.checkInsideBlocks`, `BlockPos.betweenCornersInDirection` (the 26.x inside-block check, every moving entity every tick) |
| 6.8 % + 5.7 % + 3.4 % | `AABB.inflate`, `Vec3.add`, `EntityDimensions.makeBoundingBox` (entity movement) |
| 2.4 % + 1.1 % + 1.1 % + 3.1 % | `spawnForChunk` (the two bound method references per category and chunk), `getRandomPosWithin`, `BlockPos.containing` in the distance check, four `NaturalSpawner` lambdas |
| 1.6 % + 1.4 % + 1.1 % | pathfinding nodes (`findAcceptedNode`, `getNode`, `getPathType`) |
| 1.4 % | `PlayerChunkSender.sendChunk` |

The spawner is about 7 % of the main thread's allocation; the inside-block check and entity movement are about 30 %. Both are vanilla code.

<details>
<summary>Method</summary>

`run_bench.py` with `-XX:StartFlightRecording=settings=profile,dumponexit=true` on the measurement build (the 26.3-15 sources plus counters that are off without `-Dkindling.measure=true`; not a patch), `-XX:ActiveProcessorCount=8 -XX:+UseCompactObjectHeaders -Xms3G -Xmx3G`, two interleaved runs (`measure-flight-a`, `-b`), mixed load `players=8,mobs-per-player=150,travel-seconds=90,steady-seconds=90,gamemode=survival`, bench host. The recordings were read with a small `jdk.jfr.consumer` program that groups `jdk.ExecutionSample` by thread, counts a marker method once per sample when it appears anywhere in the stack (inclusive), and sums `jdk.ObjectAllocationSample` weights by the first non-JDK frame. The travel window is seconds 62 to 150 of the recording, the standing window 162 to 250 (the server is up after 18 s, the warm-up took 35 s, the flight 91 s). Tick times of these runs are not comparable with the bench rounds: the profiler and the counters cost, and a diagnostic full collection was requested twice per run.

</details>

## Looked at and left alone

**Dropped.** Three ideas left alone: the gain was too small for the risk.
Judged against the profile shares above and the worker profile on the [Pregeneration](pregeneration.md) page.

- Replacing the `AtomicLong` in `LegacyRandomSource` with a plain field (same sequence, no compare-and-set): random numbers are below 1 % of the main thread and of the workers.
- Leaf's `ChunkCache.get` (9 % of the main thread with 1 200 mobs): a simulation of its hash table with real chunk keys gives 1.35 probes per hit and 1.7 per miss, so the cost is the number of lookups and their cache misses, not the table.
- Surface rules and aquifers are vanilla code that does its work per block; there is no shortcut that is identical for every datapack.
