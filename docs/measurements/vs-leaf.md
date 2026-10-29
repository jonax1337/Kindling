# 🆚 Kindling against Leaf

[← Overview](../MEASUREMENTS.md)

How Kindling compares with plain Leaf and with its own release under the same load, and which of Leaf's options it did not adopt.

**Contents**

- [Final round: the vanilla build of 2026-10-29 against 26.3-12 and plain Leaf](#final-round-the-vanilla-build-of-2026-10-29-against-263-12-and-plain-leaf)
- [Previous round: the build of 2026-10-28 against 26.3-11 and plain Leaf](#previous-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf)
- [Mixed load: Leaf against the Kindling defaults](#mixed-load-leaf-against-the-kindling-defaults)
- [Leaf's options one by one](#leafs-options-one-by-one)
- [Not adopted, although Leaf offers them](#not-adopted-although-leaf-offers-them)

---

## Final round: the vanilla build of 2026-10-29 against 26.3-12 and plain Leaf

> [!TIP]
> 🏆 **Against plain Leaf in the mixed flight: 85.5 % → 1.5 % of the time over void, tick time p95 172.0 → 63.4 ms; in the bases 12.3 → 11.5 ms** · 🔻 costs 48 % more CPU time in the mixed load (968 → 1 433 s) and 25 % in the bases (294 → 368 s) · 🔻 standing among 1 200 mobs it is 8 % slower than 26.3-12 (36.6 → 39.7 ms), most likely because async pathfinding and async mob spawning are off now · against plain Leaf `66f99f8e` and release 26.3-12, each with its own defaults · 3 runs each · 2026-10-28/29

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="../media/kindling-vs-leaf-dark.svg">
  <img alt="Bar chart of Kindling's change against plain Leaf per figure: time over the void in the flight −98 %, flight tick time p95 −63 %, join until the full view −28 %, flight tick time average −26 %, standing tick time −17 % (not significant), survival bases tick time −7 %, CPU time of the bases run +25 %, CPU time of the flight-and-mobs run +48 %." src="../media/kindling-vs-leaf-light.svg" width="760">
</picture>

The build of `work/kindling-hardening` (`kindling-deep-1.jar`, sha256 `cd74236e47c7…`, "the new build" below) has the vanilla-parity restorations and the new defaults of 26.3-13. It ran against release 26.3-12 (the code released as 26.3-12) and plain Leaf, each with its own defaults, in the mixed load (8 players fly outward, then stand among 1 200 mobs) and with 8 players among 32 survival bases. The variants ran interleaved.

**Mixed load.** 3 runs each; `*` = marked by `compare.py` against the new build:

| Figure | New build | 26.3-12 | Leaf `66f99f8e` |
| --- | ---: | ---: | ---: |
| Flight tick time avg | 27.2 ±0.8 | 28.1 ±1.0 (p = 0.29) | 37.0 ±0.7 \* |
| Flight tick time p95 | 63.4 ±0.7 | 65.6 ±0.6 \* (p = 0.016) | 172.0 ±2.5 \* |
| Time in the void | 1.5 ±1.1 % | 1.3 ±1.2 % | 85.5 % \* |
| Own chunk missing | 0.0 % | 0.0 % | 83.6 % \* |
| New chunks per second | 99.0 | 98.5 | 51.4 \* |
| Join until the full view | 40.7 s | 40.7 s | 56.7 s \* |
| Standing tick time avg | 39.7 ±0.5 | 36.6 ±0.7 \* (p = 0.005) | 47.9 ±5.4 (p = 0.13) |
| Standing tick time p95 | 64.9 ±3.2 | 63.2 ±2.5 | 75.7 ±20.4 |
| CPU time | 1 433 ±13 s | 1 438 ±13 s | 968 ±30 s \* |

**Survival bases.** 3 runs each:

| Figure | New build | 26.3-12 | Leaf `66f99f8e` |
| --- | ---: | ---: | ---: |
| Tick time avg | 11.5 ±0.1 | 11.5 ±0.3 (p = 0.80) | 12.3 ±0.2 \* (p = 0.008) |
| p95 / p99 | 16.2 / 21.9 | 16.6 / 24.1 | 17.5 / 24.6 |
| CPU time | 368 ±7 s | 365 ±5 s | 294 ±2 s \* |

All 15 counters were identical in all runs of the three builds. With a move event listener on the bases (hopper fix, sleeping hoppers on): new build 11.8 ±0.2 ms, 26.3-12 11.7 ±0.1 ms (p = 0.63); no measurable effect of the fix.

**What the round says.**

- **Speed against 26.3-12:** the restored vanilla code costs nothing measurable in the bases (11.5 against 11.5 ms) or in the flight (average 27.2 against 28.1 ms, not significant; the p95 of 63.4 against 65.6 ms is marked, one row of about 35).
- **Standing among mobs the new build is slower:** 39.7 against 36.6 ms (+8 %). A second interleaved series points at the async defaults (below).
- **Against plain Leaf** the flight, the join and the void share keep the gap of the previous round. The bases gap (−0.8 ms, the size it had before) now passes the rule because the runs spread less. The standing gap (47.9 against 39.7 ms) does not: Leaf's three runs spread ±5.4 ms and it was still generating while the players stood.
- **CPU time** is the price against plain Leaf and does not change with the vanilla build (1 433 against 1 438 s).

<details>
<summary><b>The standing cost: async pathfinding and async mob spawning</b></summary>

A second interleaved series ran the new build with its defaults again (`dd-mixed-deep-b`), the new build with the overlay `async-previous-defaults` (async pathfinding and async mob spawning on, as in 26.3-12), and 26.3-12. 3 runs each, mixed load:

| Figure | New build (series 2) | New build, async options on | 26.3-12 |
| --- | ---: | ---: | ---: |
| Standing tick time avg | 40.5 ±1.1 | 37.5 ±0.1 (p = 0.051) | 36.6 ±0.7 \* (p = 0.016) |
| Standing p95 | 66.8 ±6.1 | 64.8 ±1.3 | 63.2 ±2.5 |
| Flight tick time avg / p95 | 28.3 / 67.1 | 27.5 / 62.8 \* (p95, p = 0.037) | 28.1 / 65.6 |
| Heap after GC | 1 155 MB | 1 126 MB \* | 1 121 MB \* |
| CPU time | 1 443 ±14 s | 1 431 ±15 s | 1 438 ±13 s |

The first series' new build (39.7 ms standing) and this one (40.5 ms) agree (p = 0.41). With the two async options on, the standing time falls by 3.0 of the 3.9 ms gap to 26.3-12. That difference misses the rule (p = 0.051), so the async defaults are the likely cause, not a proven one. The remaining 0.9 ms to 26.3-12 is not significant either: the restored vanilla code (hash, goal order, entity order, in-wall and line-of-sight checks every tick) costs at most about 1 ms here and cannot be separated from the noise. The new build also keeps about 30 MB more heap after GC in the mixed load; with the async options on it falls to 26.3-12's level.

Async pathfinding and mob spawning stay off because they are not vanilla ([ground rule 1](../../AGENTS.md)). Whether to offer a documented "fast" profile with them on is open ([roadmap](../ROADMAP.md)).

</details>

<details>
<summary><b>Method</b></summary>

Bench host: Intel i7-7700 (4 cores, 8 threads), WSL2 Ubuntu 24.04, Temurin 25, `-Xms2G -Xmx2G -XX:+UseCompactObjectHeaders`, 2026-10-28/29. Jars: the new build (`kindling-deep-1.jar`, built from `work/kindling-hardening`), 26.3-12 (`rc-final.jar`), Leaf `66f99f8e`. Every server ran with its own defaults and no overlay, except the variants that name one. Raw labels `dd-bases-*` and `dd-mixed-*` (11 variants, 33 runs, not checked in); the `compare.py` output is in `kindling-bench/runs/phaseB-compare.txt` and `phaseD-compare.txt`. Bases: `players=8,plots-per-player=4,warmup-seconds=60,steady-seconds=90`; mixed: `players=8,mobs-per-player=150,travel-seconds=90,steady-seconds=90`, survival fake players that never answer chunk batches. The chart is drawn by `kindling-bench/chart.py` from `dd-mixed-leaf`, `dd-mixed-deep`, `dd-bases-leaf` and `dd-bases-deep`.

</details>

<details>
<summary><b>All numbers: mixed load</b></summary>

Mean ±half range, 3 runs; p against the new build (`dd-mixed-deep`); `*` = p < 0.05 and a gap of more than twice the larger half range.

| Figure | New build | 26.3-12 | Leaf `66f99f8e` |
| --- | ---: | ---: | ---: |
| Flight tick time avg | 27.2 ±0.8 | 28.1 ±1.0 (0.285) | 37.0 ±0.7 (0.000 \*) |
| Flight tick time p95 | 63.4 ±0.7 | 65.6 ±0.6 (0.016 \*) | 172.0 ±2.5 (0.000 \*) |
| Flight GC count / pause | 62.7 / 3 493 ms | 69.0 / 4 283 ms | 40.0 \* / 1 514 ms \* |
| Flight main thread running / waiting | 44.6 / 3.9 s | 44.7 / 4.7 s | 18.5 \* / 0.1 s \* |
| Machine idle in the flight | 19.8 % | 19.5 % | 64.0 % \* |
| Standing tick time avg / p95 | 39.7 ±0.5 / 64.9 | 36.6 ±0.7 \* / 63.2 | 47.9 ±5.4 / 75.7 |
| Standing GC pause / main wait | 881 / 220 ms | 567 / 688 ms | 2 046 \* / 107 \* ms |
| Warm-up | 47.4 s | 47.3 s | 64.4 s \* |
| Join until the full view | 40.7 ±1.2 s | 40.7 ±1.5 s | 56.7 ±1.5 s \* |
| Chunk packets in the flight | 5 696 | 5 737 | 371 \* |
| Lead avg (chunks) / lead ≤ 2 chunks | 3.5 / 45.5 % | 3.6 / 43.2 % | 0.8 \* / 88.7 % \* |
| Void / own chunk missing / worst ray void | 1.5 / 0.0 / 1.6 % | 1.3 / 0.0 / 1.5 % | 85.5 \* / 83.6 \* / 85.3 \* % |
| New chunks per second | 99.0 | 98.5 | 51.4 \* |
| Flown blocks | 1 800 | 1 800 | 1 800 |
| Entities at the end | 2 967 | 2 932 \* | 2 900 |
| Heap after GC | 1 153 MB | 1 121 MB \* | 1 121 MB \* |
| Peak RSS / idle RSS | 2 845 / 2 828 MB | 2 872 / 2 863 MB | 2 776 \* / 2 773 \* MB |
| GC count / pause | 158 / 7 303 ms | 154 / 7 067 ms | 120 \* / 5 008 \* ms |
| CPU time | 1 433 ±13 s | 1 438 ±13 s | 968 ±30 s \* |

</details>

<details>
<summary><b>All numbers: survival bases</b></summary>

Same notation; the new build is `dd-bases-deep`.

| Figure | New build | 26.3-12 | Leaf `66f99f8e` |
| --- | ---: | ---: | ---: |
| Tick time avg | 11.5 ±0.1 | 11.5 ±0.3 (0.797) | 12.3 ±0.2 (0.008 \*) |
| p95 / p99 | 16.2 ±0.8 / 21.9 ±1.9 | 16.6 / 24.1 | 17.5 / 24.6 |
| GC pause (bases phase) / main wait | 97 / 16 ms | 112 / 14 ms | 112 / 20 ms |
| Entities | 202 | 197 | 184 |
| Heap after GC | 485.3 MB | 484.7 MB | 474.7 MB \* |
| Peak RSS | 2 719 MB | 2 697 MB | 2 773 MB |
| GC count | 45.3 | 45.0 | 36.0 \* |
| CPU time | 368.3 ±6.8 s | 365.1 ±5.2 s | 294.2 ±2.2 s \* |

The 15 counters (brewing, minecart and chest items, composter, crafter, double chest, dropped items, furnace, hopper chain / idle / minecart / stuck, piston extensions, redstone pulses, sorter) were identical in all runs of the three builds.

**With a move event listener** (`move-event-listener`, sleeping hoppers on): new build 11.8 ±0.2 ms, 26.3-12 11.7 ±0.1 ms (p = 0.63), new build without a listener 11.5 ms (p = 0.18); p99 22.5 against 20.4 ms (p = 0.065); CPU time 370.0 against 370.6 s. All counters identical. The hopper fix is not visible in the tick time: the two builds do not differ.

</details>

<details>
<summary><b>The biome zoom shortcut</b></summary>

New build with `performance.biome-zoom-shortcut.enabled` on (overlay `biome-zoom-shortcut`) against off, mixed load, 3 runs each:

| Figure | Off | On | p |
| --- | ---: | ---: | ---: |
| Flight tick time avg / p95 | 27.2 ±0.8 / 63.4 ±0.7 | 27.7 ±0.8 / 64.8 ±3.0 | 0.49 / 0.55 |
| Standing tick time avg | 39.7 ±0.5 | 39.9 ±0.7 | 0.83 |
| Time in the void | 1.5 ±1.1 % | 4.4 ±2.0 % | 0.13 |
| CPU time | 1 433 ±13 s | 1 428 ±1 s | 0.61 |
| Peak RSS | 2 845 ±15 MB | 2 774 ±23 MB \* | 0.018 |

No tick time row differs. The RSS rows are marked, but a shortcut that allocates nothing cannot plausibly lower resident memory by 70 MB; with 3 runs and about 40 rows compared, a marked row can be chance, and the heap after GC (1 153 against 1 155 MB) does not move. **No memory gain is claimed.** Verdict 🤝, the shortcut stays off.

</details>

<details>
<summary><b>Caveats and open points</b></summary>

- **Standing against plain Leaf** is not a measure of the async or sleeping defaults: Leaf was still generating while the players stood (its three runs spread ±5.4 ms). A standing comparison on a pregenerated world was not run.
- **CPU time.** +48 % in the mixed load and +25 % in the bases, unchanged by the vanilla build. Five chunk workers instead of two, the async threads and the extra chunks sent are not separated ([roadmap](../ROADMAP.md)).
- **The hopper cooldown and the Lithium ports** are still not separated: the new build runs the bases at the release's 11.5 ms.
- **Three runs per variant.** A gap below about 1 ms of tick time cannot be excluded; the restored vanilla code may cost up to that much.
- The round measured speed only. Correctness against vanilla: [vanilla-parity.md](vanilla-parity.md#vanilla-diff-against-mojangs-jar).

</details>

## Previous round: the build of 2026-10-28 against 26.3-11 and plain Leaf

> [!NOTE]
> Superseded by the [final round above](#final-round-the-vanilla-build-of-2026-10-29-against-263-12-and-plain-leaf), kept for the comparison with 26.3-11. 🏆 **Against plain Leaf in the mixed flight: 85.8 % → 2.0 % of the time over void, tick time p95 174.9 → 65.2 ms** · costs 53 % more CPU time; in the bases the tick time does not differ and CPU time is 26 % higher · against plain Leaf `66f99f8e` and release 26.3-11 (no row differs from it) · 3 runs each · 2026-10-28

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

Plain Leaf against the Kindling defaults of that day (2 GB heap, compact object headers, async tracker, pathfinding and saving; async pathfinding is no longer a default, so a fresh Kindling no longer matches this row) and against Leaf's synchronous optimisations added on top.

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
- The [final round](#final-round-the-vanilla-build-of-2026-10-29-against-263-12-and-plain-leaf) compared Kindling with plain Leaf in this load with three runs on the bench host. There the chunk defaults decide the flight, and plain Leaf was still generating while standing, so the async options are not measured on their own against Leaf. Against Kindling's own earlier build the async options are measured since 2026-10-29: without them the standing tick time is [8 % higher](#final-round-the-vanilla-build-of-2026-10-29-against-263-12-and-plain-leaf).

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
> Defaults only apply when Leaf writes `config/leaf-global.yml` fresh. A server with an existing file keeps its values; set `async.async-entity-tracker.enabled`, `async.async-playerdata-save.enabled` and `performance.sleeping-block-entity` to `true` there. The figures above were measured with `async.async-pathfinding.enabled` on, which a fresh configuration no longer writes.

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
