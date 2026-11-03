# Kindling against Paper, Purpur and Leaf

[← Overview](../MEASUREMENTS.md)

How Kindling compares with Paper, Purpur and plain Leaf under the same load, what the async options change, and which of Leaf's options Kindling did not adopt.

## Paper, Purpur, Leaf and Kindling

- **Faster** where players reach new terrain: 3.4 times the new chunks per second of Leaf with 5 elytras (44.9 → 153.2), 2.6 times with 8 players exploring (64.9 → 167.7), 4.3 times with the elytras at view distance 32; time over void 92 % → 4 % for the elytras and 82 % → 0 % for the explorers; flight tick time p95 151 → 37 ms; join until the full view 49.9 → 27.6 s.
- **Trade-off** in pregeneration: Chunky generates 2.1 times the chunks per second (97.9 → 206.7), for tick time 3.7 → 5.2 ms and 8 % more CPU time per chunk.
- **Same** without new terrain: survival bases, working villagers and a flight over a pregenerated world show no marked difference to Leaf; Kindling ticks them 14 to 40 % faster than Paper and Purpur.
- **Faster** standing among 1 200 mobs after exploring: 36.2 → 34.6 ms.
- **Slower** in CPU time: +44 % exploring and +82 % flying with elytras at view distance 10, +67 to +107 % at view distance 32; +10 % in the bases and among villagers, none over a pregenerated world.
- **Trade-off** at view distance 32 with 8 players exploring: flight tick time p95 125.2 → 72.8 ms, but the average 36.0 → 52.8 ms and standing afterwards 23.4 → 69.0 ms, with twice the heap. Kindling gives every player the whole view, which none reaches on the other forks.
- **Slower** after an elytra flight: tick time 1.2 → 26.2 ms in the phase after the flight.

Against Paper 26.3 build 237, Purpur 26.3 build 2655 and Leaf release 26.3-15, each with its own defaults, 3 runs each, on the bench host.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="../media/all-forks-dark.svg">
  <img alt="Dot plot of Paper, Purpur, Leaf and Kindling, one line per figure. New terrain: Kindling 153 chunks per second with 5 elytras against 44.9 to 45.8, 4.3 % of the flight over void against 92.2 to 92.4 %, tick time p95 in flight 37.0 against 151 to 154 ms, 168 chunks per second with 8 players against 64.9 to 69.7, 150 at view distance 32 against 34.2 to 34.9, pregeneration 207 against 94.2 to 98.6. Without new terrain, no significant difference: bases 11.0 against 11.9 to 13.3 ms, villagers 8.6 against 8.4 to 14.4 ms, pregenerated flight 25.0 against 25.2 to 30.4 ms. Costs: tick time at view distance 32 52.8 against 36.0 to 41.9 ms, CPU time 1 110 against 769 to 820 s, heap at view distance 32 3 729 against 1 954 to 2 063 MB." src="../media/all-forks-light.svg" width="760">
</picture>

Kindling here is the build with preview chunks off (they are opt-in; their cost is in [preview chunks](preview-chunks.md#previews-in-the-comparison-with-other-forks)). Five scenarios at view distance 10, the two exploring ones again at 32, and a pregeneration. `*` = marked by `compare.py` against Leaf; bold marks the better of Leaf and Kindling where the difference is marked; times in milliseconds unless a unit is given.

| Scenario | Figure | Paper | Purpur | Leaf | Kindling |
| --- | --- | ---: | ---: | ---: | ---: |
| **Elytra** (5 players, 1.7 blocks/tick, new world) | New chunks per second | 45.8 | 45.3 | 44.9 | **153.2** \* |
| | Time over void | 92.4 % | 92.3 % | 92.2 % | **4.3 %** \* |
| | Flight tick time avg / p95 | 38.1 / 150.8 | 38.8 / 154.4 | 37.2 / 151.0 | **22.0 / 37.0** \* |
| | Tick time after landing | 1.1 | 1.1 | **1.2** | 26.2 \* |
| | Join until the full view | 36.5 s | 36.5 s | 36.6 s | **20.1 s** \* |
| | CPU time | 486 s | 483 s | **482 s** | 877 s \* |
| **Mixed** (8 players fly outward, then stand among 1 200 mobs) | New chunks per second | 69.7 | 68.4 | 64.9 | **167.7** \* |
| | Time over void | 82.3 % | 82.3 % | 82.2 % | **0.0 %** \* |
| | Flight tick time avg / p95 | 31.3 / 264.0 | 32.7 / 274.8 | 33.3 / 127.2 | 34.8 / **51.3** \* |
| | Standing tick time | 47.3 | 51.5 | 36.2 | **34.6** \* |
| | Join until the full view | 49.8 s | 50.3 s | 49.9 s | **27.6 s** \* |
| | CPU time | 804 s | 820 s | **769 s** | 1 110 s \* |
| **Mixed, view distance 32** | New chunks per second | 61.2 | 60.2 | 57.0 | **168.5** \* |
| | Time over void | 68.7 % | 68.6 % | 69.0 % | **0.4 %** \* |
| | Flight tick time avg / p95 | 41.9 / 283.1 | 41.8 / 289.2 | **36.0** / 125.2 | 52.8 \* / **72.8** \* |
| | Standing tick time | 34.7 | 37.6 | **23.4** | 69.0 \* |
| | Join until the full view | not reached | not reached | not reached | 74.8 s |
| | CPU time | 1 342 s | 1 374 s | **1 238 s** | 2 568 s \* |
| | Heap after GC | 2 035 MB | 2 063 MB | **1 954 MB** | 3 729 MB \* |
| **Elytra, view distance 32** | New chunks per second | 34.2 | 34.7 | 34.9 | **150.0** \* |
| | Time over void | 82.2 % | 82.0 % | 82.2 % | **3.2 %** \* |
| | Flight tick time avg / p95 | 42.3 / 158.4 | 42.0 / 150.6 | 40.1 / 150.7 | **29.8 / 53.9** \* |
| | CPU time | 715 s | 711 s | **704 s** | 1 172 s \* |
| **Loaded** (the elytra flight over a pregenerated world) | Flight tick time avg / p95 | 29.1 / 38.5 | 30.4 / 42.7 | 25.2 / 33.7 | 25.0 / 34.6 |
| | Tick time after the flight | 22.8 | 23.0 | 18.0 | 19.6 |
| | CPU time | 288 s | 296 s | 285 s | 284 s |
| **Bases** (8 players among 32 survival bases) | Tick time avg / p95 | 13.3 / 19.0 | 13.3 / 18.9 | 11.9 / 16.4 | 11.0 / 17.4 |
| | CPU time | 245 s | 249 s | **246 s** | 271 s \* |
| **Villagers** (12 halls, villagers working) | Tick time avg / p95 | 12.3 / 16.1 | 14.4 / 16.6 | 8.4 / 10.7 | 8.6 / 10.7 |
| | CPU time | 183 s | 198 s | **176 s** | 194 s \* |
| **Pregeneration** (Chunky, radius 3 000, 240 s) | New chunks per second | 98.6 | 94.2 | 97.9 | **206.7** \* |
| | Tick time avg | 4.3 | 4.3 | **3.7** | 5.2 \* |
| | CPU time | 727 s | 714 s | **728 s** | 1 500 s \* |

**What the comparison says.**

- **Where players reach new terrain, Kindling is in another class.** Paper, Purpur and Leaf generate at about the same rate (34 to 70 chunks per second; Paper and Purpur slightly ahead of Leaf while exploring) and leave 69 to 92 % of the flight over void. Kindling keeps up with 5 elytras and 8 explorers at view distance 10 and 32.
- **The flight's tick spikes fall** (p95 151 → 37 ms with elytras) even though Kindling generates 3.4 times the chunks.
- **Without new terrain Kindling ticks like Leaf**, with identical counters in the bases and the villager halls.
- **Pregeneration doubles** on cores that sit idle on the other forks: the machine is 67 % idle during Leaf's pregeneration and 30 % during Kindling's. Per chunk Kindling spends 8 % more CPU time (41.2 → 38.0 chunks per CPU second of the pregeneration, marked). Kindling's own `/pregen` reaches 191.4 chunks per second at 38.3 chunks per CPU second.
- **CPU time is the price**, and it grows with the extra chunks: +44 % exploring, +82 % with elytras, +67 to +107 % at view distance 32, +106 % in pregeneration.
- **At view distance 32 Kindling gives 8 explorers the whole view** (74.8 s after joining); on the other forks none of them gets it during the run, and 69 % of the flight is over void. With it the average tick time rises: the exploring flight ticks at 52.8 instead of 36.0 ms and standing afterwards at 69.0 instead of 23.4 ms, with twice the heap, while the p95 falls from 125.2 to 72.8 ms. With 5 elytras at view distance 32 the full view arrives on Kindling after 68 s and on the other forks after about 120 s, in two of three runs on Leaf and Paper. A server with a view distance of 32 and many explorers needs the cores for it; preview chunks give the far view at the cost of view distance 10 ([preview chunks](preview-chunks.md#previews-to-32-chunks)).
- **After an elytra flight** Kindling's tick time stays at 26.2 ms in the phase after the flight, against 1.2 ms on Leaf, where most of the view was never generated. Which work keeps the main thread busy there is not profiled yet ([roadmap](../ROADMAP.md)).

<details>
<summary>Method and all numbers</summary>

**Bench host.** Intel i7-7700 (4 cores, 8 threads) under WSL2 Ubuntu 24.04, Java 25, `-XX:ActiveProcessorCount=8 -XX:+UseCompactObjectHeaders -Xms6G -Xmx6G`, `--cpus 8`, port 25999. The fake players ask for a render distance of 32.

- Paper and Purpur are the builds from their download services, Leaf the official 26.3-15 release, Kindling `93dc05f3` from the preview chunks branch with previews off (`kindling-allforks.jar`, sha256 `5253de7a…`). None gets a configuration change.
- Pregeneration: Chunky-Bukkit 1.5.3 on every fork, `chunky radius 3000` and `chunky start`, 240 s. Kindling's `/pregen` ran as an arm of its own in the same rounds.
- The pregenerated world for the loaded scenario was made once and copied in for every run.
- Five rounds ran with the server order rotating per round. The host restarted after the second round and ran about 10 % faster afterwards, so only the three rounds after the restart count. At view distance 32 the bases, villager and loaded scenarios ran once after the restart: **Indicative**, not in the table; they tick within 15 % of Leaf.
- Every bases counter and every villager counter was identical on all four servers in every run.

</details>

## Async pathfinding and mob spawning (opt-in)

**Faster** with the options on: standing tick time among 1 200 mobs 34.9 → 31.8 ms (−9 %). Against the same build with them off, 3 runs each, mixed load.

Kindling keeps async pathfinding and async mob spawning off because they are not vanilla ([vanilla differences](../reference/vanilla-differences.md)). Without them it already stands among mobs faster than plain Leaf (34.6 against 36.2 ms in the [comparison](#paper-purpur-leaf-and-kindling)). In a pregenerated world the options make **no difference** (27.3 → 26.9 ms, p = 0.40).

**Slower** with the vanilla defaults, against release 26.3-12 (which ran both options on): standing among 1 200 mobs 36.6 → 39.7 ms (+8 %, 3 runs, marked). With the two options on again the build stands at 37.5 ms; that difference misses the rule (p = 0.051), so the options are the likely cause, not a proven one. Neither the flight nor the bases show a cost.

<details>
<summary>Method and all numbers</summary>

**Against plain Leaf.** The build with density pruning and worker priority on (`kindling-final.jar`, sha256 `5dd632a4…`) against plain Leaf `66f99f8e`, and the same build with async pathfinding and async mob spawning on (overlay `async-previous-defaults`). 3 runs each, interleaved, `-Xms3G -Xmx3G`; `*` against Leaf, `†` against the build with its defaults:

| Scenario | Figure | Leaf | Kindling | Kindling, async options on |
| --- | --- | ---: | ---: | ---: |
| Mixed | New chunks per second | 64.6 | **159.4** \* | 150.4 |
| | Flight tick time p95 | 133.6 | 51.4 \* | **48.5** |
| | Standing tick time avg | 38.5 | 34.9 \* | **31.8** † |
| | CPU time | **811 s** | 1 161 s | 1 175 s |
| Loaded | Flight tick time avg | **25.3** | 27.3 \* | 26.9 (p = 0.40 †) |
| | CPU time | **328 s** | 352 s \* | 357 s |

A JFR profile of the main thread in the pregenerated flight (1 run each) shows natural spawning equal and pathfinding below 2.5 % of the main thread on both servers.

**Against release 26.3-12.** A second interleaved series ran the build with its defaults again, the same build with the overlay `async-previous-defaults`, and release 26.3-12. Mixed load, 3 runs each, on the bench host (`-Xms2G -Xmx2G -XX:+UseCompactObjectHeaders`); `*` against the build with its defaults:

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
