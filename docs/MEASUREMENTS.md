# Measurements

Why each Kindling change exists, and what was tried and left out. All numbers come from `kindling-bench` ([how it works](../kindling-bench/README.md)), measured on 2026-10-26 on a Windows machine with 12 logical cores and Java 25.0.4, against Leaf `4c3d5689` (Paper `1b9bf5c8`). Each variant ran twice; tables show the mean. A shared-core VPS will show other absolute numbers; the direction should hold.

## Mixed load

Scenario defaults: 8 fake players fly outward for 90 s at 1 block/tick, then land between 1 200 mobs with natural spawning on and stand for 90 s.

| Variant | Tick time flight | Tick time standing | p95 standing | Peak RSS | CPU time |
| --- | --- | --- | --- | --- | --- |
| Leaf, `-Xms3G -Xmx3G` | 16.1 | 26.0 | 30.5 | 3 590 MB | 746 s |
| G1 2 GB + compact object headers | 16.1 | 25.8 | 31.5 | 2 549 MB | 782 s |
| **Kindling defaults** (2 GB, compact headers, async tracker, pathfinding and saving) | 15.6 | 22.5 | 28.0 | 2 555 MB | 794 s |
| the same from the Kindling jar itself (no config overlay) | 15.2 | 23.6 | 34.3 (±6.1, one run with a spike) | 2 561 MB | 797 s |
| plus Leaf's synchronous optimisations without `faster-random-generator` | 17.9 | 21.3 | 26.0 | 2 554 MB | 774 s |
| plus `throttle-mob-spawning` | 12.7 | 20.7 | 25.7 | 2 556 MB | 762 s |

Tick times in milliseconds. Raw data lands in `kindling-bench/runs/` after a run (not checked in); `python -I kindling-bench/compare.py <label> …` puts runs side by side.

### Not adopted, although Leaf offers them

- `performance.faster-random-generator`: **slower** than vanilla's random in this test (flight +14 % tick time; the profile shows 18 % instead of 10 % of the main thread in the generator during natural spawning).
- Leaf's other synchronous optimisations together (`cache-biome`, `optimize-mob-spawning`, `optimize-entity-activation`, `optimize-mob-despawn`, `sleeping-block-entity`, …): standing −5 %, but flight +15 %. Which of them slows chunk generation is still open.
- `performance.dab`: no gain in this test, and it changes how distant mobs behave.
- `performance.throttle-mob-spawning`: the biggest single gain (flight −26 %, standing −5 %), but it skips 75 % of spawn attempts in chunks whose last 8 attempts failed. That changes spawn rates (mob farms with many failed attempts yield less), so it is a per-server decision, not a default.

Defaults only apply when Leaf writes `config/leaf-global.yml` fresh. A server with an existing file keeps its values; set `async.async-entity-tracker.enabled`, `async.async-pathfinding.enabled` and `async.async-playerdata-save.enabled` to `true` there.

## JVM and heap

A server jar cannot bring its own JVM flags. Measured and recommended:

- `-XX:+UseCompactObjectHeaders` (Java 25): live heap −5 %, GC time −17 %, no tick time cost.
- Heap 2 GB instead of 3 GB with G1: under full load (8 players, 2 900 entities) the server holds about 1.2 GB of live data; with 2 GB the process uses 1 GB less memory (−29 %) at the same tick time.
- Not recommended: ZGC (−22 % peak memory and returns memory to the OS, but +11 % CPU and worse tick spikes), generational Shenandoah (slower, barely less memory), G1 with a small `-Xms` (G1 returns practically nothing while the server ticks, because `G1PeriodicGCInterval` only acts after GC-free pauses).

## Elytra test: new chunks ahead of fast players

Five players fly 90 s straight outward in five directions at elytra speed (1.7 blocks/tick, about 34 blocks/s), on a stand-in for an 8 vCPU VPS (`kindling-bench/elytra.sh`: server pinned to 8 logical cores, `-XX:ActiveProcessorCount=8`; a typical VPS core is about 1.4 times slower than one here). The measure is the **lead**: how many chunks in flight direction, gap-free from the player, have already reached the client. A lead of 0 means the player flies into the void.

At view distance 10 one elytra needs about 45 new chunks per second, five need about 225. Paper uses a quarter of the cores for chunk generation (`chunk-system.worker-threads: -1` gives **2 threads** on 8 cores, log line "Paper is using 2 worker threads"), which manages about 75 chunks/s.

| Variant | Lead avg / min | Time in the void | Tick time avg / p95 | CPU |
| --- | --- | --- | --- | --- |
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

## Pregeneration

The pregenerator generates a square around a centre ring by ring, nearest first, at `Priority.IDLE`. While the average tick time is above `max-mspt` it halves its chunks in flight; while it stays below three quarters of that, it adds one per second, up to `max-parallel`. Chunks go to status `FULL` and are released right away; they do not stay in memory. The world is the same as without pregeneration (vanilla generator, same seed).

Measured with `kindling-bench/pregen.sh` (8 cores, 5 workers, radius 3 000 blocks):

| Situation | Generated | Tick time avg / p95 | Elytra test |
| --- | --- | --- | --- |
| alone, one player standing at spawn | about 35 700 chunks in 4.5 min (≈ 140/s) | 6.5 / 10.9 | – |
| while 5 players fly at elytra speed | 616 chunks (it steps back almost entirely) | 19.5 / 37 | lead 4.1 / min 1, **0 % void** |

The flight runs as well as without pregeneration (19.2 / 35, 0 % void). After the run only 634 chunks were loaded. On a VPS (cores about 1.4 times slower, shared with other servers) expect roughly one to one and a half hours for a 5 000 block radius (≈ 391 000 chunks), four to six hours for 10 000 blocks.

## Where the time goes (profile)

JFR profile of the main thread during the flight (plain Leaf):

- 40 % natural spawning (`NaturalSpawner.spawnForChunk`), of which about 11 % each go to biome lookups with Voronoi zoom (`BiomeManager.getBiome`, called through the new `EnvironmentAttributeSystem` for `NATURAL_MOB_SPAWNS`) and 10 % to random numbers.
- 21 % entity ticks, 8 % scheduled block ticks (water and lava in new chunks), 8 % random ticks.
- 9 % in Leaf's `ChunkCache.get` (memory access for the spawner's random chunk lookups).

These are the candidates for further patches. A vanilla-identical shortcut for the biome lookup during spawning would be the biggest lever, but it has to reproduce the value computation of `EnvironmentAttributeSystem` exactly. Every such patch has to beat `kindling-bench`.
