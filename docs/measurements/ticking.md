# ⏱️ World ticking

[← Overview](../MEASUREMENTS.md)

What the main thread spends its ticks on: sleeping block entities, the Lithium ports, natural spawning and the lagging check, with the profile and the ideas that were left alone.

**Contents**

- [Sleeping block entities after the fixes](#sleeping-block-entities-after-the-fixes)
- [Survival bases: sleeping block entities](#survival-bases-sleeping-block-entities)
- [Lithium ports](#lithium-ports)
- [Natural spawning: skipped spawn cost lookups](#natural-spawning-skipped-spawn-cost-lookups)
- [The lagging check of the tick loop](#the-lagging-check-of-the-tick-loop)
- [Where the time goes: main thread (profile)](#where-the-time-goes-main-thread-profile)
- [Looked at and left alone](#looked-at-and-left-alone)

---

## Sleeping block entities after the fixes

> [!TIP]
> 🏆 **−8 % average tick time in survival bases (12.3 → 11.3 ms, p = 0.001) with identical results** · sleeping block entities on against off, current build · 5 runs each · 2026-10-28

Leaf's sleeping block entities (from Lithium) are on by default: idle hoppers, furnaces, brewing stands, crafters and chests stop ticking until something can happen. Kindling's patch `0322` makes them act as on Paper where Leaf's port did not: plugins that rewrite a transfer (`InventoryMoveItemEvent#setItem` duplicated 652 items in the parity test), tuned `spigot.yml` hopper rates (906 instead of 2 751 items on a hopper chain), blocks set without block updates, stands and furnaces a plugin holds back, and Paper's cooldown after a failed transfer. A hopper takes Lithium's shortcut only when no plugin listens to `InventoryMoveItemEvent`; with `ticks-per.hopper-check` above 1, hoppers stay awake, because a sleeping hopper cannot keep Paper's check interval.

**Sleeping block entities on against off.** New build, overlays `parity-sleeping-on` and `parity-sleeping-off`, 5 runs each:

| Figure | Off | On | p |
| --- | ---: | ---: | ---: |
| Tick time avg | 12.3 ±0.4 | **11.3 ±0.1** | 0.001 \* |
| p95 | 16.8 ±1.1 | 15.6 ±0.4 | 0.040 |
| p99 | 21.6 ±2.0 | 20.7 ±1.4 | 0.34 |
| CPU time | 367 ±12 s | 376 ±27 s | 0.42 |

All 15 counters were identical in all ten runs. The option saves 8 % of the average tick time. The p95 points the same way but misses the gap rule. This replaces the two-run "−12 to −15 %" of 2026-10-27, which was measured on older builds before the fixes.

<details>
<summary><b>Method</b></summary>

Leaf's port differed from Paper in several places (a plugin's replacement item duplicated, tuned `spigot.yml` hopper rates ignored, blocks placed without block updates not noticed, held-back brewing stands never woken, and more). Kindling's patch `0322` fixes them. The parity scenarios `type=sleeping` ([bench README](../../kindling-bench/README.md#typesleeping)) run the same contraptions with `performance.sleeping-block-entity` on and off and require equal counters; the plugin stand-ins are real event listeners. The counters are deterministic, so a pair ran once per build, and twice on the release (identical both times). Bench host, release 26.3-11 against the build with the fix:

</details>

<details>
<summary><b>All numbers</b></summary>

Parity counters, release 26.3-11 against the build with the fix:

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
- **The bases check** (`parity/run.sh`, 15 counters against `bases-ci.json`): all counters matched on every build tested. The script itself exited 1 until the golden file's scenario block got the four fields the bench plugin gained later (`chunkAcks`, `gameMode`, `warmupMaxSeconds`, `warmupQuietSeconds`); that is fixed in the repository.
- **Bench rounds of the fixed builds:**
  - the build with chunk quarantine: bases, sleeping (both pairs) and villagers parity all passed;
  - the [previous round](vs-leaf.md#previous-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf): the same checks passed again.

**Speed.** Bases scenario (8 players × 4 plots, 60 s warm-up, 90 s measured, 3 GB heap), release 26.3-11 against the fixed build, sleeping on. The option is on by default in both, so the runs with and without the `sleeping-be` overlay are the same condition and are pooled: 6 runs per build, interleaved.

| Figure | Release | Fixed build | p |
| --- | ---: | ---: | ---: |
| Tick time avg | 12.6 ±0.4 | 12.7 ±0.3 | 0.60 |
| p95 | 19.4 ±2.0 | 18.9 ±1.9 | 0.56 |
| p99 | 29.0 ±5.1 | 31.2 ±2.4 | 0.23 |
| CPU time | 357 ±10 s | 364 ±14 s | 0.23 |
| GC pause | 1 111 ±121 ms | 1 077 ±58 ms | 0.44 |

No difference is significant: the fix does not slow the bases down by 0.5 ms (about 4 %) or more; a smaller slowdown cannot be excluded with six runs.

**Does the option still help?**

- *Three runs on the fixed build:* the option off gave 14.3 ±1.3 ms against 12.7 ±0.3 ms on (p = 0.20; p95 20.9 against 18.9, p = 0.65). That is the direction of the −12 to −15 % of 2026-10-27, but not significant.
- *Previous round, five runs each:* **12.3 → 11.3 ms (−8 %, p = 0.001)** with identical counters ([table](vs-leaf.md#previous-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf)). This is the figure the option is measured on now.

The absolute tick times of this day (11 to 13 ms) are far above those of the 2026-10-27 runs (7 to 8 ms in the same scenario), so only same-session comparisons count.

</details>

<details>
<summary><b>Caveats and open points</b></summary>

- **The furnace case did not reproduce.** `gate_smelted_items` is 12 with sleeping on and off on the release too, so this scenario cannot show the fix for furnaces that a plugin holds back. The expectation of 10 in earlier notes was wrong (the furnaces smelt 12); the bench README now says 12.
- **Five late fixes.** Five fixes landed after the chunk-quarantine round: the hopper cooldown after a failed transfer (Paper's `cooldown-when-full`), the removal of Lithium's nitwit shortcut, the happy ghast's adult sensor, the idle-memory pause during a pregeneration and the publication of the async saving writer. The final round's jar contains all five, but its scenarios exercise only the hopper cooldown (the sleeping pairs and the bases). The other four rest on unit tests and review.

With an `InventoryMoveItemEvent` listener (common with protection plugins), awake hoppers take Paper's transfer code instead of Lithium's shortcut so that plugins see what Paper gives them. The plain bases scenario registers no such listener; that cost was not measured.

</details>

## Survival bases: sleeping block entities

> [!NOTE]
> 🧪 **Bases tick time 9.3 → 8.0 ms with the option on (an earlier scenario without the workshop: 8.5 → 7.2 ms); re-measured with five runs after the fixes** · against Kindling 26.3-4 with the option off · 2 runs each · 2026-10-27

`performance.sleeping-block-entity` is Leaf's port of Lithium's sleeping block entities: a hopper, furnace, brewing stand, crafter or chest that cannot do anything stops ticking and is woken by exactly what lets it act again (an inventory change, an item or a minecart arriving, a redstone change).

| Variant | Tick time avg | p95 | p99 |
| --- | ---: | ---: | ---: |
| Kindling 26.3-4, option off | 9.3 | 12.2 | 13.9 |
| option on | 8.0 | 10.9 | 12.4 |
| **this build, on by default** | **8.2** | **11.8** | **14.7** |

Tick times in milliseconds; for "off" only the undisturbed run of the two (the other ran at 11.1 ms while something else used the machine). An earlier version of the scenario without the workshop gave 8.5 → 7.2 ms. All 16 counters of the scenario (items carried, sorted, caught, smelted, brewed, crafted, composted, redstone pulses, piston moves) are identical in every run with and without the option: it changes how fast, not what happens.

<details>
<summary><b>Method</b></summary>

The [mixed load](vs-leaf.md#mixed-load-leaf-against-the-kindling-defaults) has hardly any block entities, so this was measured in the `kindling-bench` bases scenario: 8 players among 32 plots of hopper chains, furnace rows, redstone boards and a workshop of item sorters, minecarts, a composter, a double chest, brewing stands and a crafter ([how it works](../../kindling-bench/README.md#the-bases-scenario)). Measured on 2026-10-27 on the same machine, Kindling 26.3-4 against this build, two runs each.

</details>

<details>
<summary><b>All numbers</b></summary>

Mixed load (flight and mobs, Kindling 26.3-4, the option alone; not comparable with [the mixed load table](vs-leaf.md#mixed-load-leaf-against-the-kindling-defaults), which was measured on another day with other defaults):

| Variant | Flight avg / p95 | Standing avg / p95 | Chunk lead avg | CPU |
| --- | ---: | ---: | ---: | ---: |
| option off | 29.1 / 46.8 | 31.7 / 41.4 | 6.4 | 1 108 s |
| option on | 29.7 / 42.4 | **28.2 / 36.4** | 7.3 | 1 123 s |

The flight does not get slower (the difference is within two runs of the same build). **The earlier claim "−11 % standing among mobs" is withdrawn.** The standing runs were 29.79 and 33.64 ms with the option off and 26.51 and 29.95 ms with it on: a gap of 3.5 ms between the means, where the two "off" runs alone differ by 3.85 ms, so it does not meet even the old rule. The mixed load holds hardly any block entities, so there is no mechanism for such a saving either. The bases figures (−12 to −15 %) are what the option is measured on; [they were re-measured](#sleeping-block-entities-after-the-fixes).

**Plugins.** Leaf's port wakes a hopper only when one of its inventories changes. A plugin that holds a transfer back, such as a claim plugin cancelling `InventoryMoveItemEvent` or a shop plugin hiding a chest through `HopperInventorySearchEvent` until its data has loaded, therefore left the hopper asleep until someone touched the chest, while Paper asks the plugin again on every attempt. Kindling's patch keeps such a hopper awake and asking, as on Paper, and honours Paper's `PrepareHopperTransferEvent`, which the port skipped. `hopper-guard-seconds=10` in the bench is a stand-in plugin that holds every hopper back for the first 10 seconds after the build and then lets everything through:

| Variant (with the stand-in plugin) | Hopper chains, furnaces, minecarts, composter, double chest, crafter, brewing stands |
| --- | --- |
| option off | run normally |
| Leaf's port | **0**: stuck for good after the plugin let go |
| **Kindling** | identical to option off |

Where dropped items pile up on hoppers while they are held back, the order they are picked up in afterwards is not fixed: two runs of this build differed by 10 of 12 140 sorter items and 1 of 30 266 caught items, and the second matched option off exactly.

</details>

<details>
<summary><b>Caveats and open points</b></summary>

A plugin that starts redirecting a hopper while the hopper sleeps with nothing to do is asked once one of the hopper's inventories changes, not every tick.

</details>

## Lithium ports

> [!TIP]
> 🏆 **Bases tick time 12.0 → 11.2 ms (p = 0.016), p95 17.0 → 15.3 ms, p99 24.4 → 19.8 ms** · the gain does not show against release 26.3-11 (11.7 ms in both) · against the previous build · 3 runs each · 2026-10-28

Patch `0326` ports four things from Lithium with the vanilla result: the redstone wire power calculation, skipping sensors a mob does not use (baby sensors on adults, the goat's item sensor), an early exit from the rain tick, and building the framed map's player name only where it is used.

| Figure | Previous | Lithium build | p |
| --- | ---: | ---: | ---: |
| Tick time avg | 12.0 ±0.2 | **11.2 ±0.1** | 0.016 \* |
| p95 | 17.0 ±0.5 | **15.3 ±0.2** | 0.021 \* |
| p99 | 24.4 ±1.2 | **19.8 ±1.0** | 0.011 \* |
| GC pause | 146 ms | 116 ms | 0.27 |
| Main-thread wait | 17.4 ms | 18.7 ms | 0.86 |
| CPU time | 368 s | 365 s | 0.59 |
| Peak RSS | 2 742 MB | 2 725 MB | 0.74 |

<details>
<summary><b>Method</b></summary>

A differential test compares the wire power with vanilla's algorithm in 4 000 random surroundings. Bases scenario (8 players × 4 plots, 60 s warm-up, 90 s measured), the spawn-skip build against the Lithium build, 3 runs each, interleaved:

</details>

<details>
<summary><b>All numbers</b></summary>

All 15 counters were identical in all six runs. That is about −7 % average, −10 % p95 and −19 % p99.

Against release 26.3-11 the gain does not show: in the [previous round](vs-leaf.md#previous-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) the build with the ports and every later fix ran the bases at 11.7 ±0.4 ms, the release at 11.7 ±0.1 ms (p = 0.99, 3 runs each). The only later change to what the bases tick is the hopper cooldown after a failed transfer. Whether that change takes the gain back was not measured.

Villagers (the sensor port): 2 players × 4 plots, `villagers-active`, 60 s warm-up, 90 s measured, 3 runs each: tick time avg 8.2 ±0.0 against 8.5 ±0.3 ms (p = 0.18), p95 9.6 against 10.9 (p = 0.17), p99 13.2 against 14.7 (p = 0.26), CPU 230 against 226 s (p = 0.16). No difference; the scenario's villagers are adults, so the skipped sensors had nothing to save there. Claims stayed at 384 and 384 with 0 shared.

</details>

<details>
<summary><b>Caveats and open points</b></summary>

The jar differs from the previous one by all four ports, so the A/B does not separate them; the redstone wire calculation is the likely source. The build was measured with a Lithium shortcut for the nitwit's job search that was removed afterwards (it skipped calls to the level's random, so it was not vanilla-identical); the bases scenario has no villagers, so the result should not depend on it.

The goat sensor, the rain and ice early exit and the framed maps are not covered by any scenario.

</details>

## Natural spawning: skipped spawn cost lookups

> [!NOTE]
> 🤝 **No measurable tick time change (flight 27.6 → 26.6 ms, p = 0.084); equal spawn counts per kind** · against the previous build · 3 runs each · 2026-10-28

Patch `0325` skips the biome lookup for entity types that have a spawn cost in no biome and keeps a one-entry memo of the spawn lookup at a position; the result is the same as before. The memo avoids resolving the same position up to three times per attempt.

| Figure | Previous | With the patch | p |
| --- | ---: | ---: | ---: |
| Flight tick time avg | 27.6 ±0.1 | 26.6 ±0.6 | 0.084 |
| Flight p95 | 64.0 ±3.8 | 62.4 ±0.8 | 0.54 |
| Flight, main thread running time | 43 829 ±396 ms | 42 262 ±694 ms | **0.041 \*** |
| Flight, main-thread wait | 5 117 ±347 ms | 4 981 ±316 ms | 0.65 |
| Standing tick time avg | 36.9 ±0.6 | 37.8 ±0.6 | 0.15 |
| Standing p95 | 66.2 ±1.3 | 67.2 ±5.8 | 0.80 |
| Entities at the end | 3 077 ±29 | 3 047 ±24 | 0.26 |

<details>
<summary><b>Method</b></summary>

Mixed load, survival mode, 8 players, the previous build against this one, 3 runs each, interleaved, bench host:

</details>

<details>
<summary><b>All numbers</b></summary>

The tick times are not different. One row is marked significant (the main thread's CPU time in the flight, −3.6 %), out of about 35 rows compared; that is weak evidence and **no speed-up is claimed**. What the test is for is the spawn counts: the bench plugin only reports a total, so the entities saved in the world at the end of each run were counted per kind with a script (own mapping of entity ids, saved state of the whole world rather than the live mob caps): monsters 959 ±7 against 955, animals 1 670 ±12 against 1 657, ambient 124 against 124, water 222 ±4 against 227, villagers 153 against 158; none differs (p between 0.12 and 1.0).

</details>

<details>
<summary><b>Caveats and open points</b></summary>

The counts are consistent with a change that keeps vanilla's spawning; they cannot prove it.

</details>

## The lagging check of the tick loop

> [!NOTE]
> 🛡️ **Same value; the TPS averaging fell from 13.6 % to 3.4 % of the main thread on an idle server (JFR), too small to show in the tick time** · against the build without the change · one player at spawn for 2 minutes · undated

Purpur's lagging check (`lagging = TPS < lagging-threshold`) runs every tick and asked for all TPS averages, although it only uses the 5-second one.

Kindling computes only the 5-second average there; the value is the same.

The same change is proposed to Leaf as [pull request #970](https://github.com/Winds-Studio/Leaf/pull/970).

| Run | Before | After |
| --- | ---: | ---: |
| Idle server | 13.6 % | 3.4 % |
| Pregenerating | 2.9 % | 1.5 % |

<details>
<summary><b>Method</b></summary>

Main thread samples in the TPS averaging, JFR, one player at spawn for 2 minutes, 8 cores:

</details>

<details>
<summary><b>All numbers</b></summary>

The tick before has always cleared the cached averages, so every tick walked the 1, 5 and 15 minute windows, about 25 000 ticks.

In a profile of our SMP server (one player, 15 minutes) this was 8.6 % of the main thread.

The average tick time of an idle server is too noisy at this level (2.6 / 2.0 ms before, 1.8 / 2.5 ms after) to show it.

</details>

## Where the time goes: main thread (profile)

> [!NOTE]
> 🧪 **Natural spawning is a third of the main thread while players explore and 6 % while they stand; the standing phase is entity ticks (75 %) with no single hotspot above 3.1 %** · JFR `profile` settings, the mixed load (`players=8,mobs-per-player=150,travel-seconds=90,steady-seconds=90,gamemode=survival`, 3 GB heap), a build of 26.3-15 with measurement counters, 2 runs · bench host · 2026-10-29

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

The 1.1 points between the attribute lookup and the biome lookup are what the 26.3 `EnvironmentAttributeSystem` adds on top of the biome zoom (three map lookups where 1.21 needed one). The spawner's share while exploring is the measured basis for the candidates in [the roadmap](../ROADMAP.md#-open) (pre-computing the read-only checks off the main thread, and the small exact costs); while players stand it is not worth a patch. The pathfinding neighbour loop at 1.1 % caps what a per-section danger counter could give.

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
<summary><b>Method</b></summary>

`run_bench.py` with `-XX:StartFlightRecording=settings=profile,dumponexit=true` on the measurement build (the 26.3-15 sources plus counters that are off without `-Dkindling.measure=true`; not a patch), `-XX:ActiveProcessorCount=8 -XX:+UseCompactObjectHeaders -Xms3G -Xmx3G`, two interleaved runs (`measure-flight-a`, `-b`). The recordings were read with a small `jdk.jfr.consumer` program that groups `jdk.ExecutionSample` by thread, counts a marker method once per sample when it appears anywhere in the stack (inclusive), and sums `jdk.ObjectAllocationSample` weights by the first non-JDK frame. The travel window is seconds 62 to 150 of the recording, the standing window 162 to 250 (the server is up after 18 s, the warm-up took 35 s, the flight 91 s). Tick times of these runs are not comparable with the bench rounds: the profiler and the counters cost, and a diagnostic full collection was requested twice per run.

</details>

## Looked at and left alone

> [!CAUTION]
> ❌ **Three ideas left alone: the gain was too small for the risk** · against the profile shares above and under [Pregeneration](pregeneration.md#where-the-time-goes-chunk-workers-profile)

These were looked at and left alone, because the gain was too small for the risk:

- Replacing the `AtomicLong` in `LegacyRandomSource` with a plain field (same sequence, no compare-and-set): random numbers are below 1 % of the main thread and of the workers.
- Leaf's `ChunkCache.get` (9 % of the main thread with 1 200 mobs): a simulation of its hash table with real chunk keys gives 1.35 probes per hit and 1.7 per miss, so the cost is the number of lookups and their cache misses, not the table.
- Surface rules and aquifers are vanilla code that does its work per block; there is no shortcut that is identical for every datapack.

---

[← Overview](../MEASUREMENTS.md)
