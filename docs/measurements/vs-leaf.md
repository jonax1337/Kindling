# Kindling against Leaf

[← Overview](../MEASUREMENTS.md)

How Kindling compares with Paper and plain Leaf under the same load, what the async options change, and which of Leaf's options Kindling did not adopt.

## Paper, plain Leaf and Kindling

- **Faster** where players reach new terrain: 2.5 to 3.6 times the new chunks per second of plain Leaf, 0 % instead of 82 % of the mixed flight over void, join until the full view 50.1 → 27.2 s, flight tick time p95 130.3 → 59.6 ms.
- **Faster** without new terrain: survival bases 11.8 → 10.8 ms, 1 200 standing mobs 39.1 → 34.6 ms.
- **Same** for working villagers: 8.5 → 8.4 ms.
- **Slower** in a pregenerated world: flight tick time 25.5 → 27.2 ms (+7 %). With [worker demand](host.md) it is 31.7 → 33.3 ms (+5 %) in another series, at Leaf's CPU time.
- **Slower** in CPU time: 8 to 83 % more than plain Leaf; the bases keep 13 % more with worker demand (276 → 313 s).

Against Paper 26.3 build 223 (`e415f5e0`, the commit Leaf builds on) and plain Leaf `66f99f8e`, each with its own defaults, 3 runs each, on the bench host.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="../media/kindling-vs-leaf-dark.svg">
  <img alt="Bar chart of Kindling's change against plain Leaf per figure: time over the void in the flight −100 %, flight tick time p95 −54 %, join until the full view −46 %, flight tick time average +12 %, standing tick time −11 %, survival bases tick time −9 %, CPU time of the bases run +15 %, CPU time of the flight-and-mobs run +43 %." src="../media/kindling-vs-leaf-light.svg" width="760">
</picture>

Three servers with the same JVM flags, each with the defaults it ships: Paper, plain Leaf and Kindling (`03437f89` with the density pruning, `kindling-campaign.jar`, sha256 `5e89563b…`). Five scenarios, three rounds, the server order rotating per round. `*` = marked by `compare.py` against plain Leaf; times in milliseconds unless a unit is given.

| Scenario | Figure | Paper | Leaf | Kindling |
| --- | --- | ---: | ---: | ---: |
| **Mixed** (8 players fly outward, then stand among 1 200 mobs) | Time over void | 82.0 % | 82.2 % | **0.0 %** \* |
| | New chunks per second | 68.1 | 64.8 | **159.8** \* |
| | Join until the full view | 50.2 s | 50.1 s | **27.2 s** \* |
| | Flight tick time p95 | 280.1 | 130.3 | **59.6** \* |
| | Flight tick time avg | 32.8 | **32.4** | 36.3 \* |
| | Standing tick time avg / p95 | 51.6 / 68.6 | 39.1 / 51.7 | **34.6 / 41.4** \* |
| | CPU time | 845 s | **810 s** | 1 160 s \* |
| **Elytra** (5 players, 1.7 blocks/tick, new world) | Time over void | 92.5 % | 92.7 % | **7.5 %** \* |
| | New chunks per second | 43.5 | 39.7 | **143.6** \* |
| | Flight tick time avg / p95 | 39.2 / 158.8 | 39.3 / 158.2 | **22.4 / 43.1** \* |
| | Join until the full view | 35.8 s | 36.3 s | **20.9 s** \* |
| | CPU time | **485 s** | 492 s | 900 s \* |
| **Loaded** (the same flight over a pregenerated world) | Flight tick time avg / p95 | 30.2 / 40.2 | **25.5 / 34.8** | 27.2 \* / 37.2 |
| | Tick time after the flight | 23.3 | **18.4** | 20.7 \* |
| | CPU time | 332 s | **326 s** | 351 s \* |
| **Bases** (8 players among 32 survival bases) | Tick time avg / p95 | 13.2 / 18.7 | 11.8 / 16.5 | **10.8 / 14.8** \* |
| | CPU time | **242 s** | 246 s | 282 s \* |
| **Villagers** (12 halls, villagers working) | Tick time avg / p95 | 14.4 / 16.7 | 8.5 / **9.9** | **8.4** / 10.3 |
| | CPU time | 192 s | **176 s** | 190 s |

Every bases counter and every villager counter (384 job site claims, 0 shared beds, 0 shared job sites) was identical on all three servers in every run.

**What the campaign says.**

- **Where players reach new terrain, Kindling is in another class:** 2.5 times Leaf's chunk rate in the mixed load and 3.6 times in the elytra flight, with the void share going from 82 to 0 % and 93 to 7.5 %. Paper and Leaf are equal there; both start two chunk workers.
- **The flight's average tick time is higher (+12 %)** because the main thread finishes and sends 2.5 times the chunks; the p95 halves.
- **Ticking without new terrain:** bases −9 % against Leaf and −18 % against Paper with identical counters, standing mobs −11 % against Leaf and −33 % against Paper, villagers equal to Leaf.
- **A pregenerated world is the one load Leaf wins**, by 1.7 ms (7 %). The async options do not explain it (see [the next section](#async-pathfinding-and-mob-spawning-opt-in)). A profile with the worker count as the variable put it on the five chunk workers, and [worker demand](host.md), which runs them only while new terrain waits, halves the gap to 5 % at Leaf's CPU time.
- **CPU time** is the price: +15 % in the bases, +8 % in the pregenerated world, +43 % in the mixed load and +83 % in the elytra flight, where Kindling generates 3.6 times the chunks. Per new chunk the mixed load costs less CPU time: 127 → 79 ms. A JFR profile of the bases puts the extra CPU in the chunk workers: Kindling starts five on eight threads, where Paper and Leaf start two. With worker demand the bases keep 13 % (276 → 313 s) and the pregenerated world none.

**Worker demand.** `performance.worker-demand` is on by default. In a separate series (the scenarios above, worker priority on, 3 rounds in rotating order) it moved the pregenerated flight from 35.6 ms with five fixed workers to 33.3 ms, against Leaf's 31.7 ms. Elytra, mixed flight and bases showed no marked difference. The thresholds and the full A/B are in [host.md](host.md).

<details>
<summary>Method and all numbers</summary>

**Bench host.** Intel i7-7700 (4 cores, 8 threads) under WSL2 Ubuntu 24.04, Java 25, `-XX:ActiveProcessorCount=8 -XX:+UseCompactObjectHeaders -Xms3G -Xmx3G`, `--cpus 8`, port 25999.

- Paper is the 26.3 build 223 from Paper's download service; plain Leaf is the base Kindling builds on. Neither gets a configuration change.
- The pregenerated world was made once by Kindling on the same five paths (0.8 blocks/tick, 200 s) and copied in for every loaded run.
- The fake players are in creative mode, so mobs never path to them; the villager halls are the scenario that exercises pathfinding.
- Kindling's built-in pregenerator is not in the comparison: Paper and Leaf have none.

**Control run of the final build.** The final build (density pruning and worker priority on, `kindling-final.jar`, sha256 `5dd632a4…`) against plain Leaf, and the same build with async pathfinding and async mob spawning on (overlay `async-previous-defaults`). 3 runs each, interleaved; `*` against Leaf, `†` against the final build:

| Scenario | Figure | Leaf | Kindling | Kindling, async options on |
| --- | --- | ---: | ---: | ---: |
| Elytra | New chunks per second | 41.4 | **150.5** \* | – |
| | Time over void | 92.6 % | **4.0 %** \* | – |
| | Flight tick time avg / p95 | 38.3 / 156.1 | **21.6 / 37.8** \* | – |
| | Join until the full view | 35.7 s | **20.4 s** \* | – |
| Mixed | New chunks per second | 64.6 | **159.4** \* | 150.4 |
| | Time over void | 82.4 % | **0.0 %** \* | 0.0 % |
| | Flight tick time p95 | 133.6 | 51.4 \* | **48.5** |
| | Standing tick time avg | 38.5 | 34.9 \* | **31.8** † |
| | CPU time | **811 s** | 1 161 s | 1 175 s |
| Loaded | Flight tick time avg | **25.3** | 27.3 \* | 26.9 (p = 0.40 †) |
| | Main thread's wait for a CPU | **88 ms** | 243 ms \* | 314 ms |
| | Chunk packets sent in the flight | 24 010 | 24 605 \* | 24 728 |
| | CPU time | **328 s** | 352 s \* | 357 s |

The final build repeats the campaign; with worker priority the elytra flight improves a little (143.6 → 150.5 chunks/s, tick time 22.4 → 21.6 ms). In the pregenerated world Kindling sends 2.5 % more chunk packets and its main thread waits about three times as long for a CPU; the five chunk workers turned out to be the cause. A JFR profile of the main thread (1 run each) shows natural spawning equal and the extra time spread over entity ticks (movement and goals), with pathfinding below 2.5 % of the main thread on both servers.

**Worker demand against five fixed workers and plain Leaf** (another series, `-Xms3G -Xmx3G`, 8 CPUs, 3 rounds in rotating order; `*` marked against Leaf):

| Scenario | Row | Leaf `66f99f8e` | Five fixed workers | Worker demand |
| --- | --- | ---: | ---: | ---: |
| Pregenerated flight | Tick time avg / p95 | **31.7 / 43.5** | 35.6 \* / 54.3 \* | 33.3 \* / 50.5 \* |
| | Main-thread wait (ms) | **149** | 709 \* | 311 \* |
| | CPU time (s) | **462** | 513 \* | 477 (p = 0.085) |
| Bases | Tick time avg | 13.4 | 12.0 \* | **11.5** \* |
| | CPU time (s) | **276** | 324 \* | 313 \* |

</details>

## Async pathfinding and mob spawning (opt-in)

**Faster** with the options on: standing tick time among 1 200 mobs 34.9 → 31.8 ms (−9 %). Against the same final build with them off, 3 runs each, mixed load.

Kindling keeps async pathfinding and async mob spawning off because they are not vanilla ([vanilla differences](../reference/vanilla-differences.md)). Without them it is already faster than plain Leaf standing among mobs (34.6 against 39.1 ms in the campaign). In a pregenerated world the options make **no difference** (27.3 → 26.9 ms, p = 0.40), so they do not explain the gap to Leaf there. The table is in the control run of the [campaign](#paper-plain-leaf-and-kindling).

**Slower** with the vanilla defaults, against release 26.3-12 (which ran both options on): standing among 1 200 mobs 36.6 → 39.7 ms (+8 %, 3 runs, marked). With the two options on again the build stands at 37.5 ms; that difference misses the rule (p = 0.051), so the options are the likely cause, not a proven one. Neither the flight nor the bases show a cost.

<details>
<summary>Method and all numbers</summary>

A second interleaved series ran the build with its defaults again, the same build with the overlay `async-previous-defaults`, and release 26.3-12. Mixed load, 3 runs each, on the bench host (`-Xms2G -Xmx2G -XX:+UseCompactObjectHeaders`); `*` against the build with its defaults:

| Figure | Build, defaults | Build, async options on | Release 26.3-12 |
| --- | ---: | ---: | ---: |
| Standing tick time avg | 40.5 ±1.1 | 37.5 ±0.1 (p = 0.051) | 36.6 ±0.7 \* (p = 0.016) |
| Standing p95 | 66.8 ±6.1 | 64.8 ±1.3 | 63.2 ±2.5 |
| Flight tick time avg / p95 | 28.3 / 67.1 | 27.5 / 62.8 \* (p95, p = 0.037) | 28.1 / 65.6 |
| Heap after GC | 1 155 MB | 1 126 MB \* | 1 121 MB \* |
| CPU time | 1 443 ±14 s | 1 431 ±15 s | 1 438 ±13 s |

The first series' build (39.7 ms standing) and this one (40.5 ms) agree (p = 0.41). With the two options on, the standing time falls by 3.0 of the 3.9 ms gap to 26.3-12. The remaining 0.9 ms is not significant either: the restored vanilla code (hash, goal order, entity order, in-wall and line-of-sight checks every tick) costs at most about 1 ms here and cannot be separated from the noise. The build with the defaults keeps about 30 MB more heap after GC in the mixed load; with the options on it falls to 26.3-12's level.

Whether to offer a documented "fast" profile with the options on is open ([roadmap](../ROADMAP.md)).

</details>

## Leaf's options one by one

**Indicative.** No option is both faster and the same behaviour; the best single one, `optimize-entity-activation`, takes the standing tick time from 37.2 to 35.5 ms. Against the Kindling defaults of that test, 2 runs each, bench host.

Run as a group the options hid which one did what, so each ran alone on top of the Kindling defaults. None became a default. Together they cost the flight 6.6 % (15.1 → 16.1 ms).

<details>
<summary>Method and all numbers</summary>

Intel i7-7700 (4 cores, 8 threads), 16 GB, Linux (WSL 2), Java 25.0.5, two runs each (± is half the gap between them). Scenario: 8 fake players fly outward, then land between 1 200 mobs and stand. Tick times in milliseconds.

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

The time in the void varies a lot between runs (±5 points) and says more about this machine than about the options: see [the elytra test](chunks.md). The figures were measured with `async.async-pathfinding.enabled` on, which a fresh configuration does not write.

</details>

## Not adopted, although Leaf offers them

**Dropped.** None of these ten Leaf options became a default: `faster-random-generator` is slower (flight +14 % tick time), `throttle-mob-spawning` is the biggest gain (flight −26 %) but changes spawn rates. **Indicative**: 2 runs each, in the mixed load (8 fake players fly outward, then stand among 1 200 mobs), against the Kindling defaults of that test.

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

---

[← Overview](../MEASUREMENTS.md)
