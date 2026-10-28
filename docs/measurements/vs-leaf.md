# 🆚 Kindling against Leaf

[← Overview](../MEASUREMENTS.md)

How Kindling compares with plain Leaf and with its own release under the same load, and which of Leaf's options it did not adopt.

**Contents**

- [Final round: the build of 2026-10-28 against 26.3-11 and plain Leaf](#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf)
- [Mixed load: Leaf against the Kindling defaults](#mixed-load-leaf-against-the-kindling-defaults)
- [Leaf's options one by one](#leafs-options-one-by-one)
- [Not adopted, although Leaf offers them](#not-adopted-although-leaf-offers-them)

---

## Final round: the build of 2026-10-28 against 26.3-11 and plain Leaf

> [!TIP]
> 🏆 **Against plain Leaf in the mixed flight: 85.8 % → 2.0 % of the time over void, tick time p95 174.9 → 65.2 ms** · costs 53 % more CPU time; in the bases the tick time does not differ and CPU time is 26 % higher · against plain Leaf `66f99f8e` and release 26.3-11 (no row differs from it) · 3 runs each · 2026-10-28

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="../media/kindling-vs-leaf-dark.svg">
  <img alt="Bar chart of Kindling's change against plain Leaf per figure; the numbers are in the tables below." src="../media/kindling-vs-leaf-light.svg" width="760">
</picture>

Kindling's current build against release 26.3-11 and against plain Leaf at the commit Kindling is now based on, each with its own defaults: the mixed load (8 players fly outward, then stand among mobs) and 8 players among 32 survival bases.

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

**What the round says.**

- The changes of 2026-10-28 made no measurable difference to speed: the new build and 26.3-11 agree in every row of both loads.
- The [Lithium ports](ticking.md#lithium-ports) measured −7 % in their own A/B, but the new build is not faster than the release in the bases. The only later change to what the bases tick is the hopper cooldown after a failed transfer: hoppers that keep failing stay awake and wait out Paper's cooldown instead of sleeping. It was not measured on its own.
- Against plain Leaf the gain is where players outrun world generation. In the bases, Kindling is not faster than plain Leaf and uses more CPU time.

<details>
<summary><b>Method</b></summary>

The build with all changes of 2026-10-28 (`cd56f6c7`, "the new build" below) against release 26.3-11 and against plain Leaf at the commit Kindling is now based on (`66f99f8e`), on the bench host, 2026-10-28. Every server ran with its own defaults and no overlay, so plain Leaf had sleeping block entities and the async options off. The variants ran interleaved. Bases: `players=8,plots-per-player=4,warmup-seconds=60,steady-seconds=90`. Mixed: `players=8,mobs-per-player=150,travel-seconds=90,steady-seconds=90`, creative fake players that never answer chunk batches. None of the 35 bench runs (28 measured, 7 for parity) failed or was repeated.

</details>

<details>
<summary><b>All numbers</b></summary>

**Parity.** `parity/run.sh <new build> all` passed:

- the bases check: 15 counters identical to `bases-ci.json`;
- both sleeping pairs: 10 counters each, with default and with tuned hopper rates;
- the villager pair: 48 bed and 48 job site claims in both modes, none shared.

The new build contains the five fixes that the earlier rounds had not benched ([sleeping block entities after the fixes](ticking.md#sleeping-block-entities-after-the-fixes)).

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

The new build and 26.3-11 differ in no row. Against plain Leaf:

- **Flight.** Plain Leaf uses Paper's two chunk workers on 8 CPUs and Paper's automatic limit of concurrent generations.
  - After 10 to 20 s of flight the eight players had outrun world generation, in all three runs. From 20 s on every sample was over the void.
  - The server sent them 348 to 358 chunk packets in the whole flight, and their own chunk was missing 84 % of the time.
  - The ticks were either short or very long: median 3.1 to 3.3 ms, p95 175 ms. The 1 800 ticks took 101 to 104 s instead of 93 to 95 s.
  - The main thread ran only 18.5 s of that time while the machine was two thirds idle. It spent most of each long tick neither running nor waiting for a CPU. That fits waiting for chunks that were not generated yet; it was not profiled.

  The comparison shows the [chunk workers](chunks.md#default-number-of-chunk-workers), the [generation cap](chunks.md#concurrent-generations-per-player) and [directional loading](chunks.md#elytra-test-new-chunks-ahead-of-fast-players) together, not one by one.
- **Standing.** Kindling's standing ticks were 20 % shorter, but plain Leaf was behind when the players landed. Its machine was busier while standing (34 % of the CPUs against 23 %), which fits its two workers still generating the players' surroundings. The bench does not count chunks while standing, so that is an inference, and the figure is not a clean measure of the async defaults. A standing comparison on a pregenerated world was not run.
- **CPU time.** Kindling used 53 % more, mostly for work that plain Leaf did not get done: twice the new chunks, 16 times the chunk packets, and a main thread that ticks what the players reach.

**Stop during a pregeneration.** The table of the stops (4 each, release 26.3-11 and the new build) is under [Stop during a pregeneration](pregeneration.md#stop-during-a-pregeneration).

</details>

## Mixed load: Leaf against the Kindling defaults

> [!NOTE]
> 🧪 **Kindling defaults: flight 16.1 → 15.6 ms, standing 26.0 → 22.5 ms, peak RSS 3 590 → 2 555 MB** · only the memory gap is far beyond the gap between two runs, the tick times are not a finding · against Leaf with `-Xms3G -Xmx3G` (Leaf `4c3d5689`) · 2 runs each, Windows machine · 2026-10-26

Plain Leaf against the Kindling defaults (2 GB heap, compact object headers, async tracker, pathfinding and saving) and against Leaf's synchronous optimisations added on top.

| Variant | Tick time flight | Tick time standing | p95 standing | Peak RSS | CPU time |
| --- | ---: | ---: | ---: | ---: | ---: |
| Leaf, `-Xms3G -Xmx3G` | 16.1 | 26.0 | 30.5 | 3 590 MB | 746 s |
| G1 2 GB + compact object headers | 16.1 | 25.8 | 31.5 | 2 549 MB | 782 s |
| **Kindling defaults** (2 GB, compact headers, async tracker, pathfinding and saving) | 15.6 | 22.5 | 28.0 | 2 555 MB | 794 s |
| the same from the Kindling jar itself (no config overlay) | 15.2 | 23.6 | 34.3 (±6.1, one run with a spike) | 2 561 MB | 797 s |
| plus Leaf's synchronous optimisations without `faster-random-generator` | 17.9 | 21.3 | 26.0 | 2 554 MB | 774 s |
| plus `throttle-mob-spawning` | 12.7 | 20.7 | 25.7 | 2 556 MB | 762 s |

<details>
<summary><b>Method</b></summary>

Scenario defaults: 8 fake players fly outward for 90 s at 1 block/tick, then land between 1 200 mobs with natural spawning on and stand for 90 s.

Tick times in milliseconds. Raw data lands in `kindling-bench/runs/` after a run (not checked in); `python -I kindling-bench/compare.py <label> …` puts runs side by side.

</details>

<details>
<summary><b>Caveats and open points</b></summary>

**What this table supports.** Two runs each, Windows machine. The memory rows (2 GB with compact headers: −29 % peak RSS at equal tick time) are far larger than any gap between runs. The Kindling-defaults row is 3 % faster in the flight (15.6 against 16.1 ms) and 13 % faster standing (22.5 against 26.0 ms) than the Leaf row; the row "from the Kindling jar itself" has a standing p95 of 34.3 with a half range of 6.1 (one run spiked). These differences are of the size of two runs of one build, so they are **not** a finding under the three-run rule. What was measured since:

- The fixes that make the async defaults act as vanilla did not change the tick time ([see below](async.md#async-pathfinding-tracker-and-saving-after-the-fixes)).
- The [final round](#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) compared Kindling with plain Leaf in this load with three runs on the bench host. There the chunk defaults decide the flight, and plain Leaf was still generating while standing, so the async options are not measured on their own.

</details>

## Leaf's options one by one

> [!NOTE]
> 🧪 **No option is both faster and the same behaviour; the best single one, `optimize-entity-activation`, takes the standing tick time from 37.2 to 35.5 ms** · against the Kindling defaults · 2 runs each, bench host · 2026-10-27

The group in the [mixed load table](#mixed-load-leaf-against-the-kindling-defaults) (standing −5 %, flight +15 % together) hid which option did what, so each one ran alone on top of the Kindling defaults.

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

<details>
<summary><b>Method</b></summary>

Measured on 2026-10-27 on the bench host, not the machine of the other tables: Linux (WSL 2) on an Intel i7-7700 (4 cores, 8 threads), 16 GB, Java 25.0.5, two runs each (± is half the gap between them).

</details>

<details>
<summary><b>All numbers</b></summary>

No option is both faster and the same behaviour, so none became a default. Together they still cost the flight 6.6 %; the rest of the earlier +15 % is not explained by these six (that group also had `reuse-random-ticking-blockpos`, and it ran on the other machine).

</details>

<details>
<summary><b>Caveats and open points</b></summary>

The time in the void varies a lot between runs (±5 points) and says more about this machine than about the options: see [the elytra test](chunks.md#elytra-test-new-chunks-ahead-of-fast-players).

> [!IMPORTANT]
> Defaults only apply when Leaf writes `config/leaf-global.yml` fresh. A server with an existing file keeps its values; set `async.async-entity-tracker.enabled`, `async.async-pathfinding.enabled`, `async.async-playerdata-save.enabled` and `performance.sleeping-block-entity` to `true` there.

</details>

## Not adopted, although Leaf offers them

> [!CAUTION]
> ❌ **None of these ten Leaf options became a default: `faster-random-generator` is slower (flight +14 % tick time), `throttle-mob-spawning` is the biggest gain (flight −26 %) but changes spawn rates** · against the variants of the mixed load table · 2 runs each · 2026-10-26/27

Leaf offers further optional optimisations that Kindling leaves off. The table gives the reason for each.

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
