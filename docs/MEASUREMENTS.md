# 📊 Measurements

Why each Kindling change exists, and what was tried and left out.

> [!NOTE]
> All numbers come from `kindling-bench` ([how it works](../kindling-bench/README.md)), measured on 2026-10-26 on a Windows machine with 12 logical cores and Java 25.0.4, against Leaf `4c3d5689` (Paper `1b9bf5c8`). Each variant ran twice; tables show the mean. A shared-core VPS will show other absolute numbers; the direction should hold.

**Contents**

- [Mixed load](#mixed-load) · [not adopted, although Leaf offers them](#not-adopted-although-leaf-offers-them)
- [Survival bases: sleeping block entities](#survival-bases-sleeping-block-entities)
- [JVM and heap](#jvm-and-heap)
- [Elytra test: new chunks ahead of fast players](#elytra-test-new-chunks-ahead-of-fast-players)
- [Default number of chunk workers](#default-number-of-chunk-workers)
- [Pregeneration](#pregeneration)
- [Loading chunks from disk](#loading-chunks-from-disk)
- [The lagging check of the tick loop](#the-lagging-check-of-the-tick-loop)
- [Where the time goes (profile)](#where-the-time-goes-profile)

---

## Mixed load

Scenario defaults: 8 fake players fly outward for 90 s at 1 block/tick, then land between 1 200 mobs with natural spawning on and stand for 90 s.

| Variant | Tick time flight | Tick time standing | p95 standing | Peak RSS | CPU time |
| --- | ---: | ---: | ---: | ---: | ---: |
| Leaf, `-Xms3G -Xmx3G` | 16.1 | 26.0 | 30.5 | 3 590 MB | 746 s |
| G1 2 GB + compact object headers | 16.1 | 25.8 | 31.5 | 2 549 MB | 782 s |
| **Kindling defaults** (2 GB, compact headers, async tracker, pathfinding and saving) | 15.6 | 22.5 | 28.0 | 2 555 MB | 794 s |
| the same from the Kindling jar itself (no config overlay) | 15.2 | 23.6 | 34.3 (±6.1, one run with a spike) | 2 561 MB | 797 s |
| plus Leaf's synchronous optimisations without `faster-random-generator` | 17.9 | 21.3 | 26.0 | 2 554 MB | 774 s |
| plus `throttle-mob-spawning` | 12.7 | 20.7 | 25.7 | 2 556 MB | 762 s |

Tick times in milliseconds. Raw data lands in `kindling-bench/runs/` after a run (not checked in); `python -I kindling-bench/compare.py <label> …` puts runs side by side.

### Not adopted, although Leaf offers them

| Option | Why not |
| --- | --- |
| `performance.faster-random-generator` | **Slower** than vanilla's random in this test (flight +14 % tick time; the profile shows 18 % instead of 10 % of the main thread in the generator during natural spawning). |
| `performance.optimize-entity-activation` | Standing −4.6 % ([one by one](#leafs-options-one-by-one)), but not the same behaviour: it activates entities within a circle of the activation range around each player, Paper within a square of bounding boxes. That is about 21 % less area; at the default monster range of 32 a zombie 30 blocks away on both axes stays inactive. Part of the gain is simply fewer active mobs. |
| `performance.optimize-mob-despawn` | Standing −2 %, flight −4 %: barely above the gap between two runs. |
| `performance.cache-biome` | Standing −2 % for 112 MB more memory. |
| `performance.optimize-mob-spawning` | No effect; spawns use a different random sequence. |
| `performance.optimize-waypoint` | Flight +3 %, garbage collection time +18 %. |
| `performance.optimized-powered-rails` | No effect in a load without rails. |
| `performance.reuse-random-ticking-blockpos` | Hands one shared, mutable position to every random tick, which plugins can keep and see change. |
| `performance.dab` | No gain in this test, and it changes how distant mobs behave. |
| `performance.throttle-mob-spawning` | The biggest single gain (flight −26 %, standing −5 %), but it skips 75 % of spawn attempts in chunks whose last 8 attempts failed. That changes spawn rates (mob farms with many failed attempts yield less), so it is a per-server decision, not a default. |

### Leaf's options one by one

The group above (standing −5 %, flight +15 % together) hid which option did what, so each one ran alone on top of the Kindling defaults. Measured on 2026-10-27 on the bench host, not the machine of the other tables: Linux (WSL 2) on an Intel i7-7700 (4 cores, 8 threads), 16 GB, Java 25.0.5, two runs each (± is half the gap between them).

| Variant | Tick time flight | Tick time standing | Peak RSS | Time in the void |
| --- | ---: | ---: | ---: | ---: |
| Kindling defaults | 15.1 ±0.5 | 37.2 ±0.3 | 2 792 MB | 40 % |
| `cache-biome` | 14.9 | 36.3 ±0.6 | 2 904 MB | 32 % |
| `optimize-mob-spawning` | 15.1 | 36.9 | 2 842 MB | 31 % |
| `optimize-mob-despawn` | 14.5 ±0.6 | 36.5 ±0.2 | 2 851 MB | 33 % |
| `optimize-entity-activation` | 15.2 | **35.5 ±0.3** | 2 812 MB | 32 % |
| `optimize-waypoint` | 15.6 ±0.4 | 36.7 | 2 848 MB | 29 % |
| `optimized-powered-rails` | 15.2 | 37.4 | 2 812 MB | 34 % |
| all six together | 16.1 | 35.5 | 2 833 MB | 29 % |

No option is both faster and the same behaviour, so none became a default. Together they still cost the flight 6.6 %; the rest of the earlier +15 % is not explained by these six (that group also had `reuse-random-ticking-blockpos`, and it ran on the other machine). The time in the void varies a lot between runs (±5 points) and says more about this machine than about the options: see [the elytra test](#elytra-test-new-chunks-ahead-of-fast-players).

> [!IMPORTANT]
> Defaults only apply when Leaf writes `config/leaf-global.yml` fresh. A server with an existing file keeps its values; set `async.async-entity-tracker.enabled`, `async.async-pathfinding.enabled`, `async.async-playerdata-save.enabled` and `performance.sleeping-block-entity` to `true` there.

## Survival bases: sleeping block entities

`performance.sleeping-block-entity` is Leaf's port of Lithium's sleeping block entities: a hopper, furnace, brewing stand, crafter or chest that cannot do anything stops ticking and is woken by exactly what lets it act again (an inventory change, an item or a minecart arriving, a redstone change). The mixed load above has hardly any block entities, so this was measured in the `kindling-bench` bases scenario: 8 players among 32 plots of hopper chains, furnace rows, redstone boards and a workshop of item sorters, minecarts, a composter, a double chest, brewing stands and a crafter ([how it works](../kindling-bench/README.md#the-bases-scenario)). Measured on 2026-10-27 on the same machine, Kindling 26.3-4 against this build, two runs each.

| Variant | Tick time avg | p95 | p99 |
| --- | ---: | ---: | ---: |
| Kindling 26.3-4, option off | 9.3 | 12.2 | 13.9 |
| option on | 8.0 | 10.9 | 12.4 |
| **this build, on by default** | **8.2** | **11.8** | **14.7** |

Tick times in milliseconds; for "off" only the undisturbed run of the two (the other ran at 11.1 ms while something else used the machine). An earlier version of the scenario without the workshop gave 8.5 → 7.2 ms. All 16 counters of the scenario (items carried, sorted, caught, smelted, brewed, crafted, composted, redstone pulses, piston moves) are identical in every run with and without the option: it changes how fast, not what happens.

Mixed load (flight and mobs, Kindling 26.3-4, the option alone; not comparable with the table further up, which was measured on another day with other defaults):

| Variant | Flight avg / p95 | Standing avg / p95 | Chunk lead avg | CPU |
| --- | ---: | ---: | ---: | ---: |
| option off | 29.1 / 46.8 | 31.7 / 41.4 | 6.4 | 1 108 s |
| option on | 29.7 / 42.4 | **28.2 / 36.4** | 7.3 | 1 123 s |

The flight does not get slower (the difference is within two runs of the same build); standing among 1 200 mobs gets 11 % faster.

**Plugins.** Leaf's port wakes a hopper only when one of its inventories changes. A plugin that holds a transfer back, such as a claim plugin cancelling `InventoryMoveItemEvent` or a shop plugin hiding a chest through `HopperInventorySearchEvent` until its data has loaded, therefore left the hopper asleep until someone touched the chest, while Paper asks the plugin again on every attempt. Kindling's patch keeps such a hopper awake and asking, as on Paper, and honours Paper's `PrepareHopperTransferEvent`, which the port skipped. `hopper-guard-seconds=10` in the bench is a stand-in plugin that holds every hopper back for the first 10 seconds after the build and then lets everything through:

| Variant (with the stand-in plugin) | Hopper chains, furnaces, minecarts, composter, double chest, crafter, brewing stands |
| --- | --- |
| option off | run normally |
| Leaf's port | **0**: stuck for good after the plugin let go |
| **Kindling** | identical to option off |

Where dropped items pile up on hoppers while they are held back, the order they are picked up in afterwards is not fixed: two runs of this build differed by 10 of 12 140 sorter items and 1 of 30 266 caught items, and the second matched option off exactly.

A plugin that starts redirecting a hopper while the hopper sleeps with nothing to do is asked once one of the hopper's inventories changes, not every tick.

## JVM and heap

A server jar cannot bring its own JVM flags. Measured and recommended:

- ✅ `-XX:+UseCompactObjectHeaders` (Java 25): live heap −5 %, GC time −17 %, no tick time cost.
- ✅ Heap 2 GB instead of 3 GB with G1: under full load (8 players, 2 900 entities) the server holds about 1.2 GB of live data; with 2 GB the process uses 1 GB less memory (−29 %) at the same tick time.
- ❌ Not recommended:
  - ZGC: −22 % peak memory and returns memory to the OS, but +11 % CPU and worse tick spikes.
  - Generational Shenandoah: slower, barely less memory.
  - G1 with a small `-Xms`: G1 returns practically nothing while the server ticks, because `G1PeriodicGCInterval` only acts after GC-free pauses.

## Elytra test: new chunks ahead of fast players

Five players fly 90 s straight outward in five directions at elytra speed (1.7 blocks/tick, about 34 blocks/s), on a stand-in for an 8 vCPU VPS (`kindling-bench/elytra.sh`: server pinned to 8 logical cores, `-XX:ActiveProcessorCount=8`; a typical VPS core is about 1.4 times slower than one here). The measure is the **lead**: how many chunks in flight direction, gap-free from the player, have already reached the client. A lead of 0 means the player flies into the void.

At view distance 10 one elytra needs about 45 new chunks per second, five need about 225. Paper uses a quarter of the cores for chunk generation (`chunk-system.worker-threads: -1` gives **2 threads** on 8 cores, log line "Paper is using 2 worker threads"), which manages about 75 chunks/s.

| Variant | Lead avg / min | Time in the void | Tick time avg / p95 | CPU |
| --- | ---: | ---: | ---: | ---: |
| Leaf, 2 workers | 0.2 / 0 | 92 % | 19.8 / 95 | 394 s |
| Kindling, 2 workers | 0.2 / 0 | 94 % | 24.5 / 113 | 413 s |
| view distance 8 instead of 10 | 0.3 / 0 | 89 % | 8.5 / 36 | 408 s |
| 5 workers | 3.5 / 0 | 9 % | 18.5 / 36 | 831 s |
| 7 workers | 5.1 / 0 | 1.5 % | 34.9 / 79 | 986 s |
| **5 workers + directional loading** | 4.4 / **1** | **0 %** | 19.2 / 35 | 828 s |
| **pregenerated world**, 2 workers | **8.1 / 4** | **0 %** | 24.4 / **33** | 503 s |

Conclusions:

- The bottleneck is chunk generation, not the main thread. A smaller view distance relieves the ticks but does not stop the void.
- A **pregenerated world** solves it completely: loading from disk is many times cheaper than generating. The five flight corridors (about 3.7 km each) took 357 MB; a square with a 5 000 block radius comes to about 5 GB, 10 000 blocks to about 21 GB. No world border is needed: beyond the pregenerated area, 5 workers and directional loading keep fliers out of the void as well.
- Where generation does happen (the edge, the Nether, the End), the server needs more workers than Paper's default. 5 keep the main thread calm; 7 buy lead with noticeably worse ticks, because workers and main thread share the cores.
- **Directional loading** only reorders. It weights a chunk's distance by `1 − w·cos θ` (θ the angle to the flight direction, w grows with speed, at most 0.75), so chunks ahead come earlier while every direction keeps its order by distance. With 5 workers it turns 9 % void into 0 % at the same CPU; with 2 workers there is not enough capacity for any order to help. A first draft that shifted the centre forward was worse (55 % void), because it put chunks right in front of the player behind farther ones.

## Default number of chunk workers

Paper starts about a quarter of the cores as chunk workers (`chunk-system.worker-threads: -1`). The elytra test above shows that 2 cannot keep up with even one elytra. Kindling's default is five eighths of the cores, at most five, and never fewer than Paper's quarter. A value in `paper-global.yml` still wins.

| Cores | 4 | 8 | 12 | 16 | 24 | 32 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Paper | 1 | 2 | 3 | 4 | 6 | 8 |
| **Kindling** | **2** | **5** | **5** | **5** | **6** | **8** |

Why at most five: the mixed load (eight players flying outward, 12 logical cores, not pinned), two runs each:

| Workers | Time in the void | Tick time flying avg / p95 | Tick time standing avg / p95 | New chunks / s |
| --- | ---: | ---: | ---: | ---: |
| 3 (Paper's default here) | 24 % | 13.4 / 29.5 | 29.8 / 39.9 | 125 |
| **5 (Kindling's default)** | **0 %** | 30.6 / 43.7 | **26.6 / 31.2** | 203 |
| 7 (five eighths of 12) | 0 % | 40.1 / 64.9 | 26.9 / 32.3 | 244 |

With 3 workers the ticks look cheap only because a quarter of the time there is nothing to tick: the players fly into the void. Five workers keep them out of it; the main thread then does the vanilla work of the chunks they really reach (loading, entities, block ticks), within the 50 ms of a tick. Seven only build more lead than anyone needs and push the 95th percentile over 50 ms, because workers and main thread share the cores.

## Pregeneration

The pregenerator generates a square around a centre in tiles of 16 × 16 chunks: the tiles ring by ring from the centre tile, the chunks of a tile row by row, at `Priority.IDLE`. While the average tick time is above `max-mspt` it halves its chunks in flight; while it stays below three quarters of that, it adds one per second, up to `max-parallel`. The world is the same as without pregeneration (vanilla generator, same seed).

**Why tiles.** Generating a chunk to `FULL` also generates its neighbours part of the way (up to eleven chunks out, mostly only to the structure stage). The first version went ring by ring and released every chunk right after it was done, so those half-generated neighbours were saved and unloaded, then read back from disk when the next ring needed them, often several times per chunk. The profile showed 14 % of the worker time in parsing such chunks, and 7 % of the main thread in copying chunks for saving while unloading them.

Now the generated chunks of the last two tiles keep a ticket one level below full: the next tile finds their neighbours still in memory. One level below full means a kept chunk leaves the world right after generation exactly as an unloaded one does (unload event, entities hidden: not ticked, not counted for mob caps, not selectable by commands); only its data stays in memory. Older tiles are let go, so memory stays bounded.

Measured with the pregenerator alone (one player standing at spawn, radius 3 000 blocks, 8 cores, 5 workers, 2.5 minutes), two runs each, interleaved with Kindling 26.3-1 (ring order):

| Variant | Chunks generated | Tick time avg / p95 | GC time | CPU per chunk | Heap after GC |
| --- | ---: | ---: | ---: | ---: | ---: |
| Kindling 26.3-1 | 19 040 | 7.2 / 13.8 | 8.0 s | 47 ms | 637 MB |
| Kindling 26.3-2, tiles and palette decoder | **30 315 (+59 %)** | **5.1 / 7.8** | **1.9 s** | **28 ms (−41 %)** | 604 MB |

**Resuming.** A run saved by 26.3-1 (no `order` key in `pregeneration.properties`) continues at the first tile ring beyond the rings it had finished; chunks that are generated a second time this way only load from disk. A restart continues at the oldest chunk still in flight instead of at the newest index minus the chunks in flight, which could skip a chunk when chunks finished out of order.

**Next to fliers.** With 26.3-1 the pregenerator stepped back almost entirely while five players flew at elytra speed (616 chunks in 90 s, lead 4.1 / min 1, 0 % void, 19.5 / 37 ms per tick); the tile order does not change that throttling.

## Loading chunks from disk

Since 26.x, Minecraft decodes every palette entry of a saved chunk section (a block name, or a block with its properties, or a biome name) through DataFixerUpper codecs: an identifier is parsed and validated and several `DataResult`s are built per entry. Kindling reads the palettes straight from NBT (`net.kindlingmc.chunk.SectionPaletteDecoder`), resolves names through a cache and hands the result to the same `PalettedContainer.unpack` the codec uses. Whatever the codec would treat as an error (unknown or invalid names, unexpected tag types, a broken container) is left to the codec, so such chunks are logged and repaired exactly as in vanilla.

- **Same result:** with `-Dkindling.verifyPaletteDecoder=true` every section is decoded both ways and compared block by block; flying five players through a pregenerated world (thousands of sections) found no difference.
- **Cost:** in the pregeneration profile, parsing chunks fell from 13.9 % to 10.0 % of the worker time; the codec frames vanished and the decoder itself takes about 1 %.
- **Flying through an already generated world** (five players at elytra speed, 8 cores) showed no measurable difference (tick time 37.2 vs 36.6 ms, CPU 660 vs 654 s, lead and void identical): there the workers are not the bottleneck.

> [!NOTE]
> Writing the palettes without the codecs as well (branch `exp/palette-encoder`, byte for byte the codec's output) was measured and not taken: saving is only about 1.3 % of the CPU of a pregeneration run, the encoder cut that to 1.1 %, and chunks per run differed less than two runs of the same build did (28 421 / 28 410 against 26 368 / 28 396).

## The lagging check of the tick loop

Purpur's lagging check (`lagging = TPS < lagging-threshold`) runs every tick and asked for all TPS averages, although it only uses the 5-second one. The tick before has always cleared the cached averages, so every tick walked the 1, 5 and 15 minute windows, about 25 000 ticks. Kindling computes only the 5-second average there; the value is the same. In a profile of our SMP server (one player, 15 minutes) this was 8.6 % of the main thread. The same change is proposed to Leaf as [pull request #970](https://github.com/Winds-Studio/Leaf/pull/970).

Main thread samples in the TPS averaging, JFR, one player at spawn for 2 minutes, 8 cores:

| Run | Before | After |
| --- | ---: | ---: |
| Idle server | 13.6 % | 3.4 % |
| Pregenerating | 2.9 % | 1.5 % |

The average tick time of an idle server is too noisy at this level (2.6 / 2.0 ms before, 1.8 / 2.5 ms after) to show it.

## Where the time goes (profile)

**Main thread during the flight** (JFR, plain Leaf):

| Share | Where |
| ---: | --- |
| 40 % | natural spawning (`NaturalSpawner.spawnForChunk`), of which about 11 % each go to biome lookups with Voronoi zoom (`BiomeManager.getBiome`, called through the new `EnvironmentAttributeSystem` for `NATURAL_MOB_SPAWNS`) and 10 % to random numbers |
| 21 % | entity ticks |
| 8 % | scheduled block ticks (water and lava in new chunks) |
| 8 % | random ticks |
| 9 % | Leaf's `ChunkCache.get` (memory access for the spawner's random chunk lookups) |

These are the candidates for further patches. A vanilla-identical shortcut for the biome lookup during spawning would be the biggest lever, but it has to reproduce the value computation of `EnvironmentAttributeSystem` exactly. Every such patch has to beat `kindling-bench`.

**Chunk workers while the pregenerator runs alone** (JFR, Kindling 26.3-1):

| Share | Where |
| ---: | --- |
| 58 % | building terrain: 28 % surface rules (`MaterialSystem.buildSurface`: rule evaluation for every solid block of a column, 10 % of it in biome lookups with Voronoi zoom), 25 % noise fill (density functions, 7 % aquifers) |
| 14 % | parsing chunks loaded back from disk (fixed by the tile order and the palette decoder above) |
| 10 % | features |
| 4 / 3 / 2 / 3 % | carvers / biomes / light / saving |

**Looked at and left alone**, because the gain was too small for the risk:

- Replacing the `AtomicLong` in `LegacyRandomSource` with a plain field (same sequence, no compare-and-set): random numbers are below 1 % of the main thread and of the workers.
- Leaf's `ChunkCache.get` (9 % of the main thread with 1 200 mobs): a simulation of its hash table with real chunk keys gives 1.35 probes per hit and 1.7 per miss, so the cost is the number of lookups and their cache misses, not the table.
- Surface rules and aquifers are vanilla code that does its work per block; there is no shortcut that is identical for every datapack.
