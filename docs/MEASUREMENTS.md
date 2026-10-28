# 📊 Measurements

Why each Kindling change exists, and what was tried and left out.

> [!NOTE]
> The numbers up to 2026-10-27 come from `kindling-bench` ([how it works](../kindling-bench/README.md)), measured on a Windows machine with 12 logical cores and Java 25.0.4 (the mixed load, the elytra test, the worker counts) or on the bench host (the sections that say so), against Leaf `4c3d5689` (Paper `1b9bf5c8`). **Those variants ran twice; tables show the mean.** Two runs do not meet the rule that applies since 2026-10-28 ([how to read the numbers](#how-to-read-the-numbers)); where a claim rests on two runs, its section says so. The sections from 2026-10-28 ran three or more times per variant; the [final round](#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) compared against Leaf `66f99f8e`, the commit Kindling is now based on. A shared-core VPS will show other absolute numbers; the direction should hold.
>
> **Bench host:** a PC with an Intel i7-7700 (4 cores, 8 threads), 16 GB, Linux (Ubuntu 24.04 under WSL2), Temurin 25.0.5, G1. Unless a section says otherwise, its server ran with `-XX:+UseCompactObjectHeaders -Xms2G -Xmx2G` and 8 CPUs.

**Contents**

- [How to read the numbers](#how-to-read-the-numbers)
- [Final round: the build of 2026-10-28 against 26.3-11 and plain Leaf](#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf)
- [Mixed load](#mixed-load) · [not adopted, although Leaf offers them](#not-adopted-although-leaf-offers-them)
- [Survival bases: sleeping block entities](#survival-bases-sleeping-block-entities) · [after the fixes](#sleeping-block-entities-after-the-fixes)
- [Async pathfinding, tracker and saving after the fixes](#async-pathfinding-tracker-and-saving-after-the-fixes)
- [Natural spawning: skipped spawn cost lookups](#natural-spawning-skipped-spawn-cost-lookups)
- [Lithium ports](#lithium-ports)
- [JVM and heap](#jvm-and-heap) · [start time and native memory](#start-time-and-native-memory-cubyx-vps)
- [Elytra test: new chunks ahead of fast players](#elytra-test-new-chunks-ahead-of-fast-players)
- [Default number of chunk workers](#default-number-of-chunk-workers)
- [Concurrent generations per player](#concurrent-generations-per-player)
- [Worker priority and the main thread's wait for a CPU](#worker-priority-and-the-main-threads-wait-for-a-cpu)
- [Pregeneration](#pregeneration) · [hardening checks](#pregenerator-hardening-checks)
- [Client-paced chunk sending](#client-paced-chunk-sending)
- [Idle memory](#idle-memory)
- [Loading chunks from disk](#loading-chunks-from-disk) · [copies of unreadable chunks](#copies-of-unreadable-chunks)
- [The doctor's live readings](#the-doctors-live-readings)
- [The lagging check of the tick loop](#the-lagging-check-of-the-tick-loop)
- [Where the time goes (profile)](#where-the-time-goes-profile)

---

## How to read the numbers

**The rule (since 2026-10-28).** A variant runs at least three times; `compare.py` prints `mean ±half range nN` (half range: half the gap between the highest and the lowest run) and the p-value of Welch's t-test against the first label. A difference counts as real only if it is marked `*`: p < 0.05 **and** the means are more than twice the larger half range apart. Before, a difference counted when it was larger than the gap between two runs; with two runs that is about p = 0.29, so a claim such as "−3 %" could not be told from noise. A table in this file marked "two runs" meets the old rule only. Tables with three or more runs name the p-value; a row without `*` is not a finding, even when it points in a direction. With many rows per comparison, one row at p < 0.05 can be chance; this file only quotes such a row next to the others it came from.

**What is compared with what.**

- *Against plain Leaf:* the [final round](#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) compares Kindling with plain Leaf at the same base commit, each with its own defaults. That is the comparison the README and the website lead with. Older tables name the Leaf commit they used.
- *Against the same build without the change:* a section about one change compares the build with it against the build without it. That is the only way to see what each change does on its own. The baseline is named in each section, and it can be an earlier Kindling build, for example the pregenerator's first version (26.3-1), because plain Leaf has no pregenerator.

**Corrections to the older tables.**

- *Tick time during a flight depends on the time in the void.* A player over ungenerated terrain has nothing to load, tick or send, so less void means more chunks for the main thread and a higher flight tick time (see [the worker counts](#default-number-of-chunk-workers)). Flight tick times compare only at the same void share.
- *The "0 % void" figures measure one line straight ahead* of each player. The bench now also follows four more lines (at ±45° and ±90° to the flight direction) and the player's own chunk. The figures of 2026-10-26/27 below were measured with the single line; they were not repeated with the five lines.
- *"GC time" in older tables adds up every collector bean* of the JVM. For G1 against G1 that is comparable; for ZGC and Shenandoah it adds the concurrent cycles to the pauses. Newer runs report `gc_pause_millis`, which leaves out the "... Cycles" beans.
- *Elytra comparisons with equal JVM flags.* The elytra script used to start Kindling with a 2 GB heap and compact object headers and the Leaf jar with a 3 GB heap without them. It now gives both the same flags (3 runs each by default). The Leaf rows of [the elytra table](#elytra-test-new-chunks-ahead-of-fast-players) were measured with the old script and were not repeated, so their CPU and tick time may differ from a like-for-like run; the void and lead figures there come from the number of workers and the loading order, which the heap flags do not change.
- *Fake players are creative by default*, so mobs never target them. `gamemode=survival` exists since 2026-10-28; the mixed A/Bs of that day used it.
- *Raw data* lands in `kindling-bench/runs/` (not checked in). The Linux figures (`main_wait_ms`, steal, idle) exist only where `/proc` does.

## Final round: the build of 2026-10-28 against 26.3-11 and plain Leaf

The build with all changes of 2026-10-28 (`cd56f6c7`, "the new build" below) against release 26.3-11 and against plain Leaf at the commit Kindling is now based on (`66f99f8e`), on the bench host, 2026-10-28. Every server ran with its own defaults and no overlay, so plain Leaf had sleeping block entities and the async options off. The variants ran interleaved. Bases: `players=8,plots-per-player=4,warmup-seconds=60,steady-seconds=90`. Mixed: `players=8,mobs-per-player=150,travel-seconds=90,steady-seconds=90`, creative fake players that never answer chunk batches. None of the 35 bench runs (28 measured, 7 for parity) failed or was repeated.

**Parity.** `parity/run.sh <new build> all` passed:

- the bases check: 15 counters identical to `bases-ci.json`;
- both sleeping pairs: 10 counters each, with default and with tuned hopper rates;
- the villager pair: 48 bed and 48 job site claims in both modes, none shared.

The new build contains the five fixes that the earlier rounds had not benched ([sleeping block entities after the fixes](#sleeping-block-entities-after-the-fixes)).

**Sleeping block entities on against off.** New build, overlays `parity-sleeping-on` and `parity-sleeping-off`, 5 runs each:

| Figure | Off | On | p |
| --- | ---: | ---: | ---: |
| Tick time avg | 12.3 ±0.4 | **11.3 ±0.1** | 0.001 \* |
| p95 | 16.8 ±1.1 | 15.6 ±0.4 | 0.040 |
| p99 | 21.6 ±2.0 | 20.7 ±1.4 | 0.34 |
| CPU time | 367 ±12 s | 376 ±27 s | 0.42 |

All 15 counters were identical in all ten runs. The option saves 8 % of the average tick time. The p95 points the same way but misses the gap rule. This replaces the two-run "−12 to −15 %" of 2026-10-27, which was measured on older builds before the fixes.

**Survival bases.** 3 runs each:

| Figure | New build | 26.3-11 | Leaf `66f99f8e` |
| --- | ---: | ---: | ---: |
| Tick time avg | 11.7 ±0.4 | 11.7 ±0.1 (p = 0.99) | 12.5 ±0.8 (p = 0.28) |
| p95 / p99 | 17.0 / 23.2 | 16.4 / 22.8 | 17.8 / 25.6 |
| CPU time | 376 ±5 s | 371 ±2 s | 298 ±6 s \* |
| GC count / heap after GC | 47 / 485 MB | 48 / 485 MB | 36 \* / 475 MB \* |

All 15 counters were identical in the three builds, and no tick time differs. Plain Leaf ran at 11.9, 12.1 and 13.5 ms, about where the new build runs with sleeping off (12.0 to 12.7 ms above).

Kindling used 26 % more CPU time per run:

- About 16 of the 78 extra CPU-seconds fall into the measured 90 s. The machine was 89 % idle there, 91 % with Leaf.
- The rest falls into the start, the building of the plots and the warm-up, the phases in which the chunks around the players are generated. There Kindling runs five chunk workers instead of two on four cores with hyper-threading, plus its async threads.

Which part is extra work was not separated.

**Mixed load.** 3 runs each:

| Figure | New build | 26.3-11 | Leaf `66f99f8e` |
| --- | ---: | ---: | ---: |
| Flight tick time avg / p95 | 27.8 ±0.9 / 65.2 ±2.8 | 28.8 / 67.6 (p = 0.25 / 0.59) | 37.7 / 174.9 \* |
| Time in the void (five lines) | 2.0 ±2.1 % | 4.5 ±2.7 % | 85.8 % \* |
| Own chunk missing | 0.0 % | 0.4 % | 84.2 % \* |
| Lead avg | 3.5 | 3.5 | 0.8 \* |
| New chunks per second | 98.0 | 97.8 | 51.3 \* |
| Chunk packets in the flight | 5 700 | 5 724 | 354 \* |
| Flight: main thread running / waiting for a CPU | 44.6 / 3.8 s | 46.2 / 4.9 s | 18.5 / 0.1 s \* |
| Machine idle in the flight | 19.5 % | 18.7 % | 64.7 % \* |
| Join until the full view | 39.9 s | 40.1 s | 57.4 s \* |
| Standing tick time avg / p95 | 35.4 ±0.5 / 60.6 | 35.9 / 64.3 (p = 0.23 / 0.13) | 44.4 \* / 62.5 |
| Machine idle while standing (from the raw runs) | 77.0 % | 76.0 % | 65.9 % |
| Entities at the end | 2 926 | 2 946 | 2 904 |
| CPU time | 1 416 ±10 s | 1 434 ±11 s | 927 ±12 s \* |

The new build and 26.3-11 differ in no row. Against plain Leaf:

- **Flight.** Plain Leaf uses Paper's two chunk workers on 8 CPUs and Paper's automatic limit of concurrent generations.
  - After 10 to 20 s of flight the eight players had outrun world generation, in all three runs. From 20 s on every sample was over the void.
  - The server sent them 348 to 358 chunk packets in the whole flight, and their own chunk was missing 84 % of the time.
  - The ticks were either short or very long: median 3.1 to 3.3 ms, p95 175 ms. The 1 800 ticks took 101 to 104 s instead of 93 to 95 s.
  - The main thread ran only 18.5 s of that time while the machine was two thirds idle. It spent most of each long tick neither running nor waiting for a CPU. That fits waiting for chunks that were not generated yet; it was not profiled.

  The comparison shows the [chunk workers](#default-number-of-chunk-workers), the [generation cap](#concurrent-generations-per-player) and [directional loading](#elytra-test-new-chunks-ahead-of-fast-players) together, not one by one.
- **Standing.** Kindling's standing ticks were 20 % shorter, but plain Leaf was behind when the players landed. Its machine was busier while standing (34 % of the CPUs against 23 %), which fits its two workers still generating the players' surroundings. The bench does not count chunks while standing, so that is an inference, and the figure is not a clean measure of the async defaults. A standing comparison on a pregenerated world was not run.
- **CPU time.** Kindling used 53 % more, mostly for work that plain Leaf did not get done: twice the new chunks, 16 times the chunk packets, and a main thread that ticks what the players reach.

**Stop during a pregeneration.** Fresh world, no bench plugin, `/pregen start world 1500`, then `stop` 60 s later. Release 26.3-11 and the new build each stopped once in the round and three more times afterwards, alternating:

| Figure | 26.3-11, 4 stops | New build, 4 stops |
| --- | --- | --- |
| Overworld save at the stop | 0.34, 1.10, 0.52, 0.47 s | 5.05, 0.54, 0.59, 0.50 s |
| "Accessing poi chunk off-main" | none | once (in the last stop) |

The slow save of the round did not come back in three more stops; its cause was not looked into. The stack trace is the one under [the doctor's readings](#the-doctors-live-readings).

**What the round says.**

- The changes of 2026-10-28 made no measurable difference to speed: the new build and 26.3-11 agree in every row of both loads.
- The [Lithium ports](#lithium-ports) measured −7 % in their own A/B, but the new build is not faster than the release in the bases. The only later change to what the bases tick is the hopper cooldown after a failed transfer: hoppers that keep failing stay awake and wait out Paper's cooldown instead of sleeping. It was not measured on its own.
- Against plain Leaf the gain is where players outrun world generation. In the bases, Kindling is not faster than plain Leaf and uses more CPU time.

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

**What this table supports.** Two runs each, Windows machine. The memory rows (2 GB with compact headers: −29 % peak RSS at equal tick time) are far larger than any gap between runs. The Kindling-defaults row is 3 % faster in the flight (15.6 against 16.1 ms) and 13 % faster standing (22.5 against 26.0 ms) than the Leaf row; the row "from the Kindling jar itself" has a standing p95 of 34.3 with a half range of 6.1 (one run spiked). These differences are of the size of two runs of one build, so they are **not** a finding under the three-run rule. What was measured since:

- The fixes that make the async defaults act as vanilla did not change the tick time ([see below](#async-pathfinding-tracker-and-saving-after-the-fixes)).
- The [final round](#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) compared Kindling with plain Leaf in this load with three runs on the bench host. There the chunk defaults decide the flight, and plain Leaf was still generating while standing, so the async options are not measured on their own.

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

The flight does not get slower (the difference is within two runs of the same build). **The earlier claim "−11 % standing among mobs" is withdrawn.** The standing runs were 29.79 and 33.64 ms with the option off and 26.51 and 29.95 ms with it on: a gap of 3.5 ms between the means, where the two "off" runs alone differ by 3.85 ms, so it does not meet even the old rule. The mixed load holds hardly any block entities, so there is no mechanism for such a saving either. The bases figures (−12 to −15 %) are what the option is measured on; [they were re-measured](#sleeping-block-entities-after-the-fixes).

**Plugins.** Leaf's port wakes a hopper only when one of its inventories changes. A plugin that holds a transfer back, such as a claim plugin cancelling `InventoryMoveItemEvent` or a shop plugin hiding a chest through `HopperInventorySearchEvent` until its data has loaded, therefore left the hopper asleep until someone touched the chest, while Paper asks the plugin again on every attempt. Kindling's patch keeps such a hopper awake and asking, as on Paper, and honours Paper's `PrepareHopperTransferEvent`, which the port skipped. `hopper-guard-seconds=10` in the bench is a stand-in plugin that holds every hopper back for the first 10 seconds after the build and then lets everything through:

| Variant (with the stand-in plugin) | Hopper chains, furnaces, minecarts, composter, double chest, crafter, brewing stands |
| --- | --- |
| option off | run normally |
| Leaf's port | **0**: stuck for good after the plugin let go |
| **Kindling** | identical to option off |

Where dropped items pile up on hoppers while they are held back, the order they are picked up in afterwards is not fixed: two runs of this build differed by 10 of 12 140 sorter items and 1 of 30 266 caught items, and the second matched option off exactly.

A plugin that starts redirecting a hopper while the hopper sleeps with nothing to do is asked once one of the hopper's inventories changes, not every tick.

## Sleeping block entities after the fixes

Leaf's port differed from Paper in several places (a plugin's replacement item duplicated, tuned `spigot.yml` hopper rates ignored, blocks placed without block updates not noticed, held-back brewing stands never woken, and more). Kindling's patch `0322` fixes them. The parity scenarios `type=sleeping` ([bench README](../kindling-bench/README.md#typesleeping)) run the same contraptions with `performance.sleeping-block-entity` on and off and require equal counters; the plugin stand-ins are real event listeners. The counters are deterministic, so a pair ran once per build, and twice on the release (identical both times). Bench host, release 26.3-11 against the build with the fix:

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

- **The furnace case did not reproduce.** `gate_smelted_items` is 12 with sleeping on and off on the release too, so this scenario cannot show the fix for furnaces that a plugin holds back. The expectation of 10 in earlier notes was wrong (the furnaces smelt 12); the bench README now says 12.
- **Guarded bases** (`hopper-guard-seconds=10`, a stand-in claim plugin): release and fixed build agree on all 15 counters, one run each.
- **The bases check** (`parity/run.sh`, 15 counters against `bases-ci.json`): all counters matched on every build tested. The script itself exited 1 until the golden file's scenario block got the four fields the bench plugin gained later (`chunkAcks`, `gameMode`, `warmupMaxSeconds`, `warmupQuietSeconds`); that is fixed in the repository.
- **Bench rounds of the fixed builds:**
  - the build with chunk quarantine: bases, sleeping (both pairs) and villagers parity all passed;
  - the [final round](#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf): the same checks passed again.
- **Five late fixes.** Five fixes landed after the chunk-quarantine round: the hopper cooldown after a failed transfer (Paper's `cooldown-when-full`), the removal of Lithium's nitwit shortcut, the happy ghast's adult sensor, the idle-memory pause during a pregeneration and the publication of the async saving writer. The final round's jar contains all five, but its scenarios exercise only the hopper cooldown (the sleeping pairs and the bases). The other four rest on unit tests and review.

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
- *Final round, five runs each:* **12.3 → 11.3 ms (−8 %, p = 0.001)** with identical counters ([table](#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf)). This is the figure the option is measured on now.

The absolute tick times of this day (11 to 13 ms) are far above those of the 2026-10-27 runs (7 to 8 ms in the same scenario), so only same-session comparisons count.

With an `InventoryMoveItemEvent` listener (common with protection plugins), awake hoppers take Paper's transfer code instead of Lithium's shortcut so that plugins see what Paper gives them. The plain bases scenario registers no such listener; that cost was not measured.

## Async pathfinding, tracker and saving after the fixes

Kindling's patches `0321` (async mob spawning stall, mirrors Leaf pull request #937), `0323` (async player data saving), `0324` and Paper `0071` (async pathfinding and tracker) make the async defaults act as vanilla where they did not.

**Villager claims.** `type=villagers` builds walled halls with twelve villagers, twelve beds and twelve composters each; vanilla takes a place in the tick it chooses it, so no place is held twice. One run per side, async pathfinding on against off, bench host:

| Build | `shared_beds`, on | `shared_beds`, off | `shared_job_sites` | `bed_claims`, `job_site_claims` |
| --- | ---: | ---: | ---: | ---: |
| Release 26.3-11, round 1 | **5** | 0 | 0 | 48, 48 |
| Release 26.3-11, round 2 | **3** | 0 | 0 | 48, 48 |
| Build with the fix | 0 | 0 | 0 | 48, 48 in both |

The fix held in the later builds as well (the Lithium build, the last build). Only beds showed the bug; shared job sites were 0 everywhere on the release, so that half of the bug is documented but not reproduced. The number of shared beds varies between runs.

**Cost of the fixes.** Mixed load, survival mode, 8 players, no overlay, the previous build against the async-path build, 3 runs each, interleaved:

| Figure | Previous | With the fix | p |
| --- | ---: | ---: | ---: |
| Standing tick time avg | 37.0 ±0.7 | 36.8 ±0.5 | 0.77 |
| Standing p95 | 64.0 ±2.8 | 65.3 ±4.8 | 0.71 |
| Flight tick time avg | 26.6 ±1.0 | 26.7 ±0.6 | 0.92 |
| Flight p95 | 62.1 ±3.1 | 61.3 ±0.9 | 0.71 |
| Standing GC pause | 888 ±486 ms | 1 066 ±442 ms | 0.69 |
| Standing main-thread wait | 586 ±229 ms | 516 ±308 ms | 0.77 |
| CPU time | 1 442 ±6 s | 1 449 ±20 s | 0.62 |
| Peak RSS | 2 799 ±55 MB | 2 844 ±68 MB | 0.45 |

Nothing is significant; with three runs and a half range of 0.5 to 1 ms, an effect below about 1 ms of standing tick time cannot be excluded. The extra loop over the path types per search and the tracker's flag read are therefore not shown to be free, only below the noise of this test. The comparison also covers the async-saving change that was in the same build.

**Not tested on a running server:** async player data saving. Its new write ordering, flush barrier and shutdown drain have unit tests (`SerialWrites`), but `save-all flush` followed by a look at the file times, and a stop on a slow disk, were not run. The tracker's flag race has no unit test (`Entity` cannot be loaded without registries). The bench's fake players are creative by default, so mobs never path to them; the villager hall is the only scenario that exercises async pathfinding hard.

## Natural spawning: skipped spawn cost lookups

Patch `0325` skips the biome lookup for entity types that have a spawn cost in no biome and keeps a one-entry memo of the spawn lookup at a position; the result is the same as before. Mixed load, survival mode, 8 players, the previous build against this one, 3 runs each, interleaved, bench host:

| Figure | Previous | With the patch | p |
| --- | ---: | ---: | ---: |
| Flight tick time avg | 27.6 ±0.1 | 26.6 ±0.6 | 0.084 |
| Flight p95 | 64.0 ±3.8 | 62.4 ±0.8 | 0.54 |
| Flight, main thread running time | 43 829 ±396 ms | 42 262 ±694 ms | **0.041 \*** |
| Flight, main-thread wait | 5 117 ±347 ms | 4 981 ±316 ms | 0.65 |
| Standing tick time avg | 36.9 ±0.6 | 37.8 ±0.6 | 0.15 |
| Standing p95 | 66.2 ±1.3 | 67.2 ±5.8 | 0.80 |
| Entities at the end | 3 077 ±29 | 3 047 ±24 | 0.26 |

The tick times are not different. One row is marked significant (the main thread's CPU time in the flight, −3.6 %), out of about 35 rows compared; that is weak evidence and **no speed-up is claimed**. What the test is for is the spawn counts: the bench plugin only reports a total, so the entities saved in the world at the end of each run were counted per kind with a script (own mapping of entity ids, saved state of the whole world rather than the live mob caps): monsters 959 ±7 against 955, animals 1 670 ±12 against 1 657, ambient 124 against 124, water 222 ±4 against 227, villagers 153 against 158; none differs (p between 0.12 and 1.0). The counts are consistent with a change that keeps vanilla's spawning; they cannot prove it.

## Lithium ports

Patch `0326` ports four things from Lithium with the vanilla result: the redstone wire power calculation, skipping sensors a mob does not use (baby sensors on adults, the goat's item sensor), an early exit from the rain tick, and building the framed map's player name only where it is used. A differential test compares the wire power with vanilla's algorithm in 4 000 random surroundings. Bases scenario (8 players × 4 plots, 60 s warm-up, 90 s measured), the spawn-skip build against the Lithium build, 3 runs each, interleaved:

| Figure | Previous | Lithium build | p |
| --- | ---: | ---: | ---: |
| Tick time avg | 12.0 ±0.2 | **11.2 ±0.1** | 0.016 \* |
| p95 | 17.0 ±0.5 | **15.3 ±0.2** | 0.021 \* |
| p99 | 24.4 ±1.2 | **19.8 ±1.0** | 0.011 \* |
| GC pause | 146 ms | 116 ms | 0.27 |
| Main-thread wait | 17.4 ms | 18.7 ms | 0.86 |
| CPU time | 368 s | 365 s | 0.59 |
| Peak RSS | 2 742 MB | 2 725 MB | 0.74 |

All 15 counters were identical in all six runs. That is about −7 % average, −10 % p95 and −19 % p99. The jar differs from the previous one by all four ports, so the A/B does not separate them; the redstone wire calculation is the likely source. The build was measured with a Lithium shortcut for the nitwit's job search that was removed afterwards (it skipped calls to the level's random, so it was not vanilla-identical); the bases scenario has no villagers, so the result should not depend on it.

Against release 26.3-11 the gain does not show: in the [final round](#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) the build with the ports and every later fix ran the bases at 11.7 ±0.4 ms, the release at 11.7 ±0.1 ms (p = 0.99, 3 runs each). The only later change to what the bases tick is the hopper cooldown after a failed transfer. Whether that change takes the gain back was not measured.

Villagers (the sensor port): 2 players × 4 plots, `villagers-active`, 60 s warm-up, 90 s measured, 3 runs each: tick time avg 8.2 ±0.0 against 8.5 ±0.3 ms (p = 0.18), p95 9.6 against 10.9 (p = 0.17), p99 13.2 against 14.7 (p = 0.26), CPU 230 against 226 s (p = 0.16). No difference; the scenario's villagers are adults, so the skipped sensors had nothing to save there. Claims stayed at 384 and 384 with 0 shared. The goat sensor, the rain and ice early exit and the framed maps are not covered by any scenario.

## JVM and heap

A server jar cannot bring its own JVM flags. Measured and recommended:

- ✅ `-XX:+UseCompactObjectHeaders` (Java 25): live heap −5 %, GC time −17 %, no tick time cost.
- ✅ Heap 2 GB instead of 3 GB with G1: under full load (8 players, 2 900 entities) the server holds about 1.2 GB of live data; with 2 GB the process uses 1 GB less memory (−29 %) at the same tick time.
- The process needs about 500 MB beyond the heap at `-Xmx2G` (metaspace, thread stacks, code cache, direct buffers; `kindling-bench`). `/kindling doctor` therefore wants `-Xmx` plus 600 MB to fit into the memory the server can use (container limit included), and counts every other server on the machine on top.
- ❌ Not recommended:
  - ZGC: −22 % peak memory and returns memory to the OS, but +11 % CPU and worse tick spikes.
  - Generational Shenandoah: slower, barely less memory.
  - G1 with a small `-Xms`: G1 returns practically nothing while the server ticks, because `G1PeriodicGCInterval` only acts after GC-free pauses.

## Start time and native memory (Cubyx VPS)

Measured on the Cubyx VPS with the production servers, one start each:

- **AOT cache** (`-XX:AOTCache`, Java 25): billiards started in 9.5 to 10.7 s instead of 16.9 to 26.9 s, smp in 19.2 s instead of 23 to 27 s. The cache only works with a direct class path start; `java -jar` with Paperclip loads the server in its own class loader, where the cache does not apply.
- **Native heap trim**: one `jcmd <pid> System.trim_native_heap` freed 101 MB on billiards-1 and 103 MB on smp-1, so `/kindling doctor` points at `-XX:TrimNativeHeapInterval` (the interval it names is a suggestion; only the one-off trim was measured). Trimming while players are online can delay safepoints, so Kindling's own memory return runs only [while nobody is online](#idle-memory).

### Waiting for a CPU on the VPS

The VPS has 8 vCPUs that the guest sees as 8 sockets with one core each (8 last-level caches), kernel 6.12. Linux ignores Java thread priorities there (HotSpot's default `ThreadPriorityPolicy=0`), so Paper's raised main thread has no edge over the chunk workers, which the Windows benchmarks did not show. Measured on the live smp-1 while a pregeneration ran, with the kernel's per-thread scheduler statistics (`/proc/<pid>/task/<tid>/schedstat`), 2026-10-27; read-only except for the experiment below, and a single sample, not repeated:

| Reading | Result |
| --- | --- |
| 28 minutes, main thread | ran 326 s, waited 92 s in the run queue (**+28 %**) |
| 28 minutes, Netty | ran 19 s, waited 101 s |
| One 60 s window, main thread | ran 4.68 s, waited 1.30 s |
| The same window, Netty | waited 11.8 ms per wake-up |
| The same window, machine | 33 % idle, steal 0.8 %: the competition is inside the guest |
| Experiment: five chunk workers at nice 10, 45 s alternating with nice 0, twice each | main-thread wait 1 140 and 1 032 ms at nice 0, 945 and 721 ms at nice 10 (**about −25 %**, noisy); Netty wait per wake-up 16.1 and 5.1 ms at nice 0, 15.9 and 29.0 ms at nice 10 (no improvement) |

The experiment set the nice values by hand and reset all 93 threads to 0 afterwards (checked with `ps`). It led to [`worker-priority`](#worker-priority-and-the-main-threads-wait-for-a-cpu), which the bench host and a second VPS check measured further.

**VPS check of the build with worker priority and the host monitor (2026-10-28).** A test server (127.0.0.1, port 25997, `-Xms1G -Xmx1G`, no plugins, view distance 8, seed 1234, a fresh world per run) next to the production servers, with nobody online (0 established connections to the proxy, rechecked every 5 s); the live servers were only read, none logged "Can't keep up". The machine was 85 to 91 % idle, load 0.3 to 1.6, steal 0.05 to 0.09 %. `/pregen start world 2000`, 3 chunk workers, 181 s per run, restart in between, the default `max-main-thread-wait` (then 0.10) active, order off, on, off, on:

| Run | Chunks in 186 s | Main-thread wait | Chunk worker CPU | Tick time (1 min avg / max) |
| --- | ---: | ---: | ---: | ---: |
| 1, nice 0 | 1 188 | 11.5 % | 45 s | 1.2 / 12.0 ms |
| 1, nice 10 | 1 842 | 12.7 % | 72 s | 0.9 / 28.6 ms |
| 2, nice 0 | 753 | 13.8 % | 27 s | 1.0 / 31.4 ms |
| 2, nice 10 | 3 017 | 10.9 % | 99 s | 1.6 / 27.7 ms |

Worker priority had **no measurable effect** on the main thread here: the wait was 11 to 14 % in both modes and the ticks were about 1 ms. Nice 10 showed more chunks (13.1 against 5.2 chunks/s on average) and more worker CPU, but with two runs per mode and a fourfold spread inside the nice-10 pair (9.9 and 16.2 chunks/s) that is not significant. The machine never ran short of CPUs, which is when a lower priority matters. Side finding: a nearly idle main thread (3 to 6 s of CPU in 181 s) already waited 11 to 14 % of its time at nice 0 and nice 10 alike, on the idle live smp-1 too (5.2 % over 20 s, about 10 µs per scheduler slice). On this VM that is wake-up latency, not contention, and it made the pregenerator's wait throttle (limit 0.10) fire at baseline in 4 to 6 of 8 status lines per run, while the four runs made only 4.0 to 16.2 chunks/s. That is why the throttle is off by default.

The doctor's `LIVE` section agreed with `/proc` seen from outside on an idle test server (nice 0 and steal 0.0 against 0.05 %; main-thread wait 5.4, 3.6 and 4.6 % against 2.5 % over a different window of a nearly idle thread). Its warning text ("other threads or processes on this machine compete with it") was misleading on a VM like this one, where a lightly loaded thread shows 10 % wait without any competitor. The warning now also needs at least 20 ms of waiting per second: the idle VPS thread waited about 3 ms per second, the loaded bench host 25 to 50 ms per second (mixed flight, worker priority on and off).

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

**What "0 %" means.** Measured on the 12-thread Windows machine, pinned to 8 threads, two runs per variant, 90 s of flight, and the lead followed **one line straight ahead** of each player; sides and turns were not checked. The flight starts with a full cushion of chunks from the warm-up: about 45 new chunks per second are needed per player against about 34 offered, so a longer flight would eat into it. The row combines 5 workers with directional loading, and the 0 % holds with that machine's generation capacity.

**On the bench host the same load is not at 0 %.** Elytra flights (5 players at 1.7 blocks/tick) with the current defaults on the 4-core, 8-thread bench host under Linux spent 52.3 ±4.7 % (worker priority off) and 49.1 ±5.3 % (on) of the time in the void by the old line-ahead measure (3 runs each, [worker priority](#worker-priority-and-the-main-threads-wait-for-a-cpu)), with a lead of 1.4 and about 86 new chunks per second against the roughly 225 five elytras need. Chunk generation capacity of that machine is the limit, which is what [the next sections](#concurrent-generations-per-player) say about it. A pregenerated world is the answer there (2 workers, 0 % void in the table above). On this host the cap on concurrent generations took eight fliers at 1 block/tick from 31.5 % to 1.2 % void; at 1.7 blocks/tick the same host is out of capacity.

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

## Concurrent generations per player

On the 8-thread bench host (Intel i7-7700, Linux, the machine of [Leaf's options one by one](#leafs-options-one-by-one)) eight players flying outward still spent 30–40 % of the time over the void with Kindling's five workers, while the same load on 12 threads stayed at 0 %. A profile showed the five workers busy with vanilla world generation, the machine only 70–80 % busy, and about 110 new chunks per second against roughly 220 that eight fliers need at view distance 10.

More workers do not help: they share the four cores with the main thread. What helps is the order. Paper lets every player have a fifth of the chunks of the view distance in generation at once (`chunk-loading-advanced.player-max-concurrent-chunk-generates: 0`, 88 at view distance 10). With that many already handed to the workers, [directional loading](#elytra-test-new-chunks-ahead-of-fast-players) has nothing left to re-sort: the workers spend half their time on chunks beside and behind the flight that the player has left before they are done. A shorter queue keeps re-sorting toward the flight direction. Kindling caps the automatic value at 32; an explicit value in `paper-global.yml` still wins. Which chunks are generated, and how, does not change.

Flight only (eight players, no mobs), two runs each:

| Variant | Time in the void | Lead avg | Tick time flight avg / p95 | Tick time standing | New chunks / s |
| --- | ---: | ---: | ---: | ---: | ---: |
| Paper's automatic limit (88) | 31.5 % | 1.6 | 14.9 / 40.2 | 38.7 | 112 |
| limit 32 | **1.2 %** | 3.0 | 24.0 / 49.6 | 35.7 | 103 |
| limit 16 | 3.5 % | 3.1 | 24.8 / 51.2 | 35.1 | 101 |
| 7 workers, automatic limit | 32.2 % | 1.8 | 31.4 / 63.6 | 50.4 | 116 |
| 7 workers, limit 16 | 0.9 % | 4.2 | 44.0 / 94.6 | 51.8 | 118 |

Mixed load (flight, then 1 200 mobs, then idle), two runs each; the last row is the Kindling jar with the cap and no configuration overlay:

| Variant | Time in the void | Tick time flight avg / p95 | Tick time standing avg / p95 | Peak RSS | CPU time |
| --- | ---: | ---: | ---: | ---: | ---: |
| Paper's automatic limit | 38.5 % | 14.7 / 40.5 | 37.7 / 71.8 | 2 783 MB | 1 322 s |
| limit 32 set in `paper-global.yml` | 1.4 % | 23.1 / 50.0 | 36.5 / 65.8 | 2 784 MB | 1 339 s |
| **Kindling default (cap 32)** | **0.9 %** | 23.6 / 48.2 | **35.9 / 67.5** | 2 766 MB | 1 327 s |

The flight ticks get more expensive because the players now fly over chunks that exist: the main thread loads, ticks and sends what they reach instead of waiting for it. They stay below 50 ms at the 95th percentile. Standing ticks, memory and CPU time do not get worse.

## Worker priority and the main thread's wait for a CPU

Two opt-in settings come out of the finding that on Linux the chunk workers compete with the main thread on equal terms ([VPS facts](#waiting-for-a-cpu-on-the-vps)): `performance.worker-priority` (chunk and I/O worker threads lower their own priority, nice 10) and `performance.pregeneration.max-main-thread-wait` (the pregenerator throttles while the main thread waited for a CPU longer than that share of the last minute). Both are off by default; this is what the bench host showed.

Method: Linux (WSL2) bench host, 8 CPUs, `-XX:ActiveProcessorCount=8` flags of `pregen.sh`, the build that introduced the settings, three runs per variant, interleaved with alternating order, 36 flight and pregeneration runs and 6 mixed runs, none failed. The main thread's wait is `main_wait_ms` (run-queue time from `schedstat`, see [the bench README](../kindling-bench/README.md#more-figures-per-run)).

**Worker priority off against on** (nice 10 for the chunk and I/O workers; pregeneration throttle off):

| Scenario | Figure | Off | On | p |
| --- | --- | ---: | ---: | ---: |
| Elytra flight, 5 players at 1.7 blocks/tick | Main-thread wait | 5 430 ±460 ms | **2 673 ±345 ms** | 0.002 \* |
| | Tick time avg / p95 | 15.9 ±1.2 / 52.0 ±6.7 | 14.3 ±0.4 / 46.5 ±5.6 | 0.17 / 0.38 |
| | Time in the void, lead | 52.3 ±4.7 %, 1.4 | 49.1 ±5.3 %, 1.4 | 0.50 |
| | New chunks per second | 85.6 | 86.7 | 0.55 |
| Pregeneration alone, one player standing, 240 s | Main-thread wait | 2 867 ±250 ms | **1 732 ±61 ms** | 0.012 \* |
| | Tick time avg / p95 | 6.2 / 11.2 ±1.0 | 5.9 / 10.1 ±0.4 | 0.24 / 0.20 |
| | Chunks per second | 140.3 | 142.4 | 0.17 |
| Pregeneration while 5 players fly, 90 s | Main-thread wait | 4 380 ±314 ms | **2 307 ±126 ms** | 0.004 \* |
| | Tick time avg / p95 | 23.1 ±0.7 / 65.9 ±3.6 | 21.5 ±0.7 / 57.7 ±1.0 | 0.049 \* / 0.050 |
| | Time in the void, lead | 43.1 ±8.8 %, 3.0 | 42.2 ±2.5 %, 2.9 | 0.88 / 0.94 |
| Mixed load, 8 survival players | Main-thread wait (flight) | 4 512 ±48 ms | **2 322 ±187 ms** | 0.001 \* |
| | Flight tick time avg / p95 | 27.3 ±0.6 / 64.4 ±0.5 | **24.8 ±0.2 / 57.6 ±1.2** | 0.010 \* / 0.005 \* |
| | Standing tick time avg / p95 | 36.7 ±0.6 / 65.7 ±3.5 | 37.4 ±1.0 / 68.4 ±4.7 | 0.40 / 0.50 |
| | Time in the void, lead | 3.1 ±1.9 %, 3.5 | 6.3 ±0.8 %, 3.3 | 0.10 / 0.09 |

The main thread's wait roughly halves in every scenario, significantly. Tick times improve in the mixed flight and, at the edge of significance, with the pregenerator beside the flight; nothing gets significantly worse. Two cautions: in the mixed load the time in the void rose from 3.1 to 6.3 % (p = 0.10, not significant) with a slightly lower lead, and more void means less to load, tick and send, so part of the better flight tick time can come from that; and the standing ticks after the pregeneration runs (a 5 s window) and after the elytra flight (p95 28.5 against 38.6 ms, p = 0.08) are too short and noisy to read. The runs say that nice 10 does not cost pregeneration speed or lead here; they were made on the bench host, and the [VPS check](#waiting-for-a-cpu-on-the-vps) saw no effect on a machine that was 85 to 90 % idle. The default stays off until a run under real load on the VPS confirms it.

**Pregenerator throttle by the main thread's wait** (limit 0.10):

| Scenario | Figure | Limit off | Limit 0.10 | With priority on, limit 0.10 |
| --- | --- | ---: | ---: | ---: |
| Pregeneration alone | Chunks per second | 140.3 | **65** (47, 76, 72; p = 0.013 \*, −55 %) | **113.7** (−20 % against priority on, p < 0.001 \*) |
| | Tick time p95 | 11.2 | 9.7 | 10.4 (none significant) |
| Pregeneration while 5 players fly | Tick time p95 | 65.9 | 48.1 (p = 0.004 \*) | 41.3 (against off, p = 0.001 \*) |
| | Lead avg / worst ray | 3.0 / 2.9 | **1.6 / 1.4** (p = 0.002 \*) | 1.6 (p = 0.001 \*) |
| | Time in the void | 43.1 ±8.8 % | 49.1 % (p = 0.36) | not reported |
| | Pregeneration progress | 12 547, 13 254, 13 067 | **2, 3, 3** | 7, 8, 5 |

The throttle lowers the tick times, but at 0.10 it stops the pregenerator for the whole flight, halves the lead and cuts pregeneration alone by more than a third without priority. That is why the default is 0 (off). A limit such as 0.20 was not tested; the throttle's switching on and off could not be observed because the run does not log the progress over time. On the VPS the main thread of an idle test server already sat above 10 % ([see above](#waiting-for-a-cpu-on-the-vps)), so a share of the last minute is a poor criterion there; judging the absolute wait per tick, or only a busy machine, is an open item on the [roadmap](ROADMAP.md).

## Pregeneration

The pregenerator generates a square around a centre in tiles of 16 × 16 chunks: the tiles ring by ring from the centre tile, the chunks of a tile row by row, at `Priority.IDLE`. While the average tick time is above `max-mspt` it halves its chunks in flight and starts no new chunk (the chunks already in flight finish); while it stays below three quarters of that, it adds one per second, up to `max-parallel`. `/pregen status` names the reason while it waits (`throttled: 43 ms average > 40 ms`). On Linux it can also throttle the same way while the main thread waited for a CPU more than `max-main-thread-wait` of the last minute, as the doctor's host monitor reads it. That limit is off by default (0): on kindling-bench, 3 runs each, 0.10 cut pregeneration alone from 140–143 to 46–75 chunks/s (overlays `pregen-w5-prio-off`, `pregen-w5-wait`), and on the VPS the main thread of a nearly idle test server (3 to 6 s of CPU in 181 s) already waited 11 to 14 % of its time, so 0.10 throttled at baseline. The world is the same as without pregeneration (vanilla generator, same seed).

**Why tiles.** Generating a chunk to `FULL` also generates its neighbours part of the way (up to eleven chunks out, mostly only to the structure stage). The first version went ring by ring and released every chunk right after it was done, so those half-generated neighbours were saved and unloaded, then read back from disk when the next ring needed them, often several times per chunk. The profile showed 14 % of the worker time in parsing such chunks, and 7 % of the main thread in copying chunks for saving while unloading them.

Now the generated chunks of the last two tiles keep a ticket one level below full: the next tile finds their neighbours still in memory. One level below full means a kept chunk leaves the world right after generation exactly as an unloaded one does (unload event, entities hidden: not ticked, not counted for mob caps, not selectable by commands); only its data stays in memory. Older tiles are let go, so memory stays bounded.

Measured with the pregenerator alone (one player standing at spawn, radius 3 000 blocks, 8 cores, 5 workers, 2.5 minutes), two runs each, interleaved with Kindling 26.3-1 (ring order):

| Variant | Chunks generated | Tick time avg / p95 | GC time | CPU per chunk | Heap after GC |
| --- | ---: | ---: | ---: | ---: | ---: |
| Kindling 26.3-1 | 19 040 | 7.2 / 13.8 | 8.0 s | 47 ms | 637 MB |
| Kindling 26.3-2, tiles and palette decoder | **30 315 (+59 %)** | **5.1 / 7.8** | **1.9 s** | **28 ms (−41 %)** | 604 MB |

**Resuming.** A run saved by 26.3-1 (no `order` key in `pregeneration.properties`) continues at the first tile ring beyond the rings it had finished; chunks that are generated a second time this way only load from disk. A restart continues at the oldest chunk still in flight instead of at the newest index minus the chunks in flight, which could skip a chunk when chunks finished out of order.

**Safety (behaviour, covered by unit tests, not a measured speed-up).**

- *Border and radius.* The radius is cut to the world border when a run starts, and one tick walks at most 4 096 indices (a whole tile outside the border counts as one): a radius of 30 000 000 blocks next to a 10 000 block border no longer holds the tick for hours.
- *Failure.* A run that throws is stopped and saved as paused; the other runs and the server tick go on.
- *Worlds.* A run waits while its world is unloaded and continues when the world is loaded again (also a world that appears later). The saved run carries the world's seed; a run saved for another seed is dropped.
- *State file.* It is written on a thread of its own from a snapshot (no fsync on the main thread) and once, synchronously, while the server stops, with the exact index. Between those, the saved index steps back over the kept tiles and the chunks in flight, so a crash costs a few chunks that load from disk instead of leaving holes.
- *Disk.* Before a run starts and every 30 seconds it compares the free space of the world's disk with what the rest of the run needs, estimated from the space the disk lost per delivered chunk since the run (re)started (16 KiB per chunk assumed for the first 2 048 chunks), and stops when less than `min-free-disk-gb` would remain; it continues once there is room again.
- *Empty server.* While a run is working the server does not pause for lack of players (`pause-when-empty-seconds`); a paused, stopped or finished run does not hold it awake.

**Next to fliers.** With 26.3-1 the pregenerator stepped back almost entirely while five players flew at elytra speed (616 chunks in 90 s, lead 4.1 / min 1, 0 % void, 19.5 / 37 ms per tick); the tile order does not change that throttling.

## Pregenerator hardening checks

Functional checks of the pregenerator's safety changes (the list is under [Pregeneration](#pregeneration)), run on the bench host with fresh worlds, one run per check, the console driven from a script (`-Xms2G -Xmx2G`, 8 CPUs; the check's own `leaf-global.yml` with `max-mspt: 40` and `max-parallel: 32`, the worker setting of `pregen-w5`). They show that the behaviour holds; none is a speed measurement.

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

## Client-paced chunk sending

The setting was taken out again after this measurement, because it gave no gain ([ROADMAP](ROADMAP.md#reviewed-and-dropped)); the results stay here as the reason.

`performance.client-paced-chunk-sending` sends the chunks a player gets in one tick as one vanilla batch, and the number per tick follows the client's answers (Paper's fixed rate still applies on top unless it is set to -1). Bench host, `chunk-acks` makes the bench's fake players answer batches like a client that takes that many chunks per tick: 32 is a fast client, 2 a slow one, 0 one that never answers. 8 players, 3 GB heap, tail shortened to 30 s standing and 10 s idle; 3 runs per variant, interleaved.

The default mixed scenario generates its world fresh, so generation (join about 43 s) is the bottleneck and off against on is identical there (fast clients: join 42.7 ±0.5 against 44.0 ±0.5 s, p = 0.055, chunk packets and lead equal). The meaningful test ran on a world pregenerated from a previous run (273 MB), where chunks load from disk and the sending shows:

| Clients | Variant | Join until the full view | Chunk packets in the flight | Batches | Flight tick time avg / p95 |
| --- | --- | ---: | ---: | ---: | ---: |
| Fast (32) | Off | 5 169 ±139 ms | 17 603 | 0 | 69.1 / 131 |
| | On | 5 875 ±1 106 ms (p = 0.41) | 17 550 | 3 680 | 68.2 / 128 |
| | On, send rate -1 | 5 406 ±677 ms (p = 0.63) | 18 018 | 1 830 | 69.7 / 135 |
| | On, send and load rate caps removed | 6 894 ±567 ms (**p = 0.032 \***, slower) | 18 051 | 1 813 | 74.5 / 151 (off with the same caps removed: 74.3 / 150) |
| Slow (2) | Off | 6 325 ±368 ms | 17 248 | 0 | 69.3 / 130 |
| | On | **12 881 ±1 144 ms** (p = 0.006 \*) | 16 599 (p = 0.060) | 8 563 | 71.1 / 138 (p = 0.56 / 0.30) |
| | On, send rate -1 | 13 152 ±675 ms (p = 0.001 \*) | 16 800 (p = 0.009 \*) | 8 660 | 72.2 / 141 |
| Never answers (0) | Off | 6 111 ±946 ms | 17 726 | 0 | 74.6 / 144 |
| | On | **11 016 ±850 ms** (p = 0.003 \*) | 17 943 | 0 | 75.7 / 149 |

Result: **on gives a fast client its view no sooner**; the join is bounded by loading chunks on the server, not by Paper's 75 chunks per second (removing the caps in the off variant changed the join not at all, 5 255 ±501 ms, and raised its tick times as far as the on variant: that is the caps, not the pacing). A slow client is throttled as designed (4 % fewer packets in the flight, many more batches), but its own join takes twice as long, and the main thread gets no measurable relief. A client that never answers (an old, modded or fake client) joins about 5 s later than off, more than the 3 s the code allows before the fixed rate takes over; the flight afterwards is unchanged. Lead, holes (0 %) and the chunk count at the end of the warm-up (3 784 packets in each of more than 120 runs) show no lost chunks; the bench has no counter for duplicates. Compared with the previous build the off variant is not slower (flight tick time 75.4 ±0.9 against 77.1 ±3.6 ms, p = 0.52). The setting stays off. A real client, ViaVersion clients and packet-watching plugins were not tried.

## Idle memory

`performance.idle-memory` runs a full collection with lowered free-heap ratios and a native heap trim after `after-seconds` without players. Bench host, 4 fake players, `-Xms512M -Xmx2G -XX:+DisableExplicitGC`, 20 s warm-up, 40 s flight, 30 s standing, 75 s idle, `after-seconds: 30`; 3 runs on and 3 off, interleaved, plus 2 of the previous build and 1 with GC logging. The bench kicks its fake players but never removes them from the player list, so a small helper plugin (kept in `runs/idle-memory/`, not in the repository) completes their disconnect as a real client would; without it the feature cannot fire. Resident memory comes from `/proc` once a second.

| Figure | Off | On |
| --- | ---: | ---: |
| Resident memory when the last player left | 2 481 ±56 MiB | 2 500 ±64 MiB |
| 50 s later | not recorded | 1 021 ±22 MiB |
| 70 s later | 2 492 ±52 MiB | **1 032 ±16 MiB** |
| Committed heap under load / at the end | 1 853 / 1 853 MiB | 1 816 / **512** MiB |
| Time until the feature fired | | 41 to 43 s after the last player left |
| Flight tick time avg / standing | 32.5 ±1.0 / 11.8 ms | 32.5 ±0.7 / 11.8 ms (p = 0.96) |
| Pause of the idle collection | | 204 ms (one run with GC logging) |

About 1.45 GiB less resident memory (−59 %) with nobody online; the load figures are unchanged. The one significant row of the comparison, the whole run's GC pause (1 946 ±36 against 2 062 ±43 ms, p = 0.028), is the 204 ms collection itself. It only works when `-Xms` is below `-Xmx`: with `-Xms2G -Xmx2G`, a small test program in an earlier session kept 1.68 GB resident whatever was run (the trim freed 14 to 25 MB). Lowering the ratios saved a further 330 MiB over a plain full collection in that program (306 against 650 MiB with `-Xms256M`). **Not measured:** the tick spike when players rejoin after the heap shrank (the bench cannot rejoin players), the pregeneration case (the benched build still ran the collection while a pregeneration kept the server awake; the later fix for that was not benched) and a real production server.

## Loading chunks from disk

Since 26.x, Minecraft decodes every palette entry of a saved chunk section (a block name, or a block with its properties, or a biome name) through DataFixerUpper codecs: an identifier is parsed and validated and several `DataResult`s are built per entry. Kindling reads the palettes straight from NBT (`net.kindlingmc.chunk.SectionPaletteDecoder`), resolves names through a cache and hands the result to the same `PalettedContainer.unpack` the codec uses. Whatever the codec would treat as an error (unknown or invalid names, unexpected tag types, a broken container) is left to the codec, so such chunks are logged and repaired exactly as in vanilla.

- **Same result:** with `-Dkindling.verifyPaletteDecoder=true` every section is decoded both ways and compared block by block; flying five players through a pregenerated world (thousands of sections) found no difference.
- **Cost:** in the pregeneration profile, parsing chunks fell from 13.9 % to 10.0 % of the worker time; the codec frames vanished and the decoder itself takes about 1 %.
- **Flying through an already generated world** (five players at elytra speed, 8 cores) showed no measurable difference (tick time 37.2 vs 36.6 ms, CPU 660 vs 654 s, lead and void identical): there the workers are not the bottleneck.

> [!NOTE]
> Writing the palettes without the codecs as well (branch `exp/palette-encoder`, byte for byte the codec's output) was measured and not taken: saving is only about 1.3 % of the CPU of a pregeneration run, the encoder cut that to 1.1 %, and chunks per run differed less than two runs of the same build did (28 421 / 28 410 against 26 368 / 28 396).

## Copies of unreadable chunks

Patch `0328` copies the stored bytes of a chunk that cannot be decompressed, read as NBT, or upgraded and parsed to `<world>/kindling-quarantine/` before the existing handling continues. Functional check on the bench host with a copy of a small generated world, one run per case, the build with the patch against the one before it:

| Case | Result |
| --- | --- |
| Six bytes of the compressed stream overwritten | One `WARN` ("could not be read (java.util.zip.ZipException: invalid code lengths set); kept a copy of its stored bytes at ...c.20.20-1.bin") next to the usual Moonrise errors; the `.bin` (3 990 bytes) is identical to the stored record, a `.txt` holds the stack trace; the chunk was regenerated and saved as before. After a restart with the same damage put back, the copy is `-2`. The previous build logged the same Moonrise errors and kept nothing |
| Valid NBT whose `block_states` has no palette | Moonrise's "Failed to parse chunk data ... chunk data will be lost", then the same `WARN` and a `.bin` (3 461 bytes, equal to the stored record); regenerated; `-2` after a restart. The previous build: only the Moonrise error |
| An unknown block in the palette | Both builds log "Unknown registry key ... using default" for every section and load the chunk with default blocks. No exception, so **no copy**: this is not a trigger on 26.x |
| A chunk saved by a newer version (data version +100 000) | Paper stops the server (`System.exit(1)`) in `SerializableChunkData.parse` before the quarantine runs, on both builds; the stored bytes stay untouched. With `-DPaper.ignoreWorldDataVersion=true` the chunk loads without an error. So **a newer Minecraft version's chunk is not covered**, and not needed |
| An untouched chunk | No `WARN`, no error, no files. Start and stop times are the same as on the previous build |

The final regression of that build passed: the bases check (15 counters), the sleeping pairs (default and tuned rates) and the villager pair. After a chunk saved by a newer version Paper's exit hung on the chunk system's halt on both builds, so it is not a regression of this patch.

## The doctor's live readings

On Linux `/kindling doctor` reads the main thread's run-queue wait, the chunk workers' wait and the host's steal from `/proc` in a background thread. Check on the bench host, server idle and then pregenerating (`/pregen start world 3000`, about 135 to 143 chunks/s), each window 60 s, the doctor run right after the window against the same counters read from outside (`schedstat` per thread, the first line of `/proc/stat`):

| Window | Main-thread wait, doctor / outside | Chunk-worker wait | Steal |
| --- | ---: | ---: | ---: |
| Idle, a | 0.0 % / 0.00 % | not shown | 0.0 % / 0.00 % |
| Idle, b | 0.1 % / 0.16 % | not shown | 0.0 % / 0.00 % |
| Pregeneration, a | 12 % / 12.02 % | 0.1 % / 0.11 % | 0.0 % / 0.00 % |
| Pregeneration, b | 13 % / 12.87 % | 0.2 % / 0.16 % | 0.0 % / 0.00 % |
| Pregeneration, c (the cancel command was malformed, so the run kept going) | 4.8 % / 5.11 % | 0.1 % / 0.06 % | 0.0 % / 0.00 % |

The readings agree within about half a percentage point; the machine was 99 % idle in the idle windows and 32 to 34 % idle with the pregenerator (62 % user, 6 % system). At 12 to 13 % the doctor gave its warning about contention; at 4.8 % it said contention is not what slows the server down. The doctor prints no machine idle share (only the main thread, the workers, steal and the container limit). The windows differ slightly (the doctor's last 60 s against a window I read by hand), so this is a check for agreement, not a precision test. On the VPS the same readings matched too ([above](#waiting-for-a-cpu-on-the-vps)), with the caveat that a nearly idle thread on a VM shows 10 % wait without competition.

Other lines checked in a second short run (world on a 1 GiB tmpfs, an older `leaf-global.yml` with async pathfinding and sleeping block entities off, then `host-monitor: false`): disk space was an `INFO` at 936.7 GiB free and a `WARN` at 832 MiB of 1.0 GiB (also logged at start-up); the older file produced the hint that Kindling's defaults apply only to a fresh file and named both options; with `host-monitor: false` the `LIVE` section is absent; the pregenerator paused itself for lack of disk and `/pregen status` showed the `disk:` line. The advice for a direct class path start (AOT cache) was not exercised, because the server started with `java -jar`.

**A shutdown race in upstream code.** Stopping the server while a pregeneration ran can log an ERROR stack trace: "Thread failed main thread check: Accessing poi chunk off-main".

- *Where it was seen:* first while a beehive was generated, then once in the [final round's stops](#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) while a jigsaw structure piece (a village part) was placed. The server stopped cleanly both times.
- *The path, all of it vanilla, Paper or Moonrise code:* a chunk worker generating features sets a block through `WorldGenRegion.setBlock`. `ServerLevel.updatePOIOnBlockStateChange` then hands the POI update to the server with `execute`. Once the server is stopping, that runs the task at once on the calling worker. `PoiManager.getOrLoad` then fails the main-thread check, and the server logs "Error executing task on Server".
- *Kindling:* no Kindling frame is in the stack. A pregeneration only makes it likely that generations are in flight at the stop, as any generation would.
- *Frequency:* once in four stops of the new build and in none of four stops of release 26.3-11. That does not tell the builds apart.
- *Not checked:* whether the skipped POI update for that block is repaired when the chunk loads again.

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
