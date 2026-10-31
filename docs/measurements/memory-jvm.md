# JVM and memory

[← Overview](../MEASUREMENTS.md)

Which JVM flags Kindling recommends, what the heap holds under load, how much faster a start gets with the AOT cache, what gives memory back while nobody is online, and whether JDK 28 or value classes (Project Valhalla, [JEP 401](https://openjdk.org/jeps/401)) would help. Kindling stays on Java 25.

**Contents**

- [JVM and heap](#jvm-and-heap)
- [The ring of partly generated chunks](#the-ring-of-partly-generated-chunks)
- [AOT cache and native memory](#aot-cache-and-native-memory)
- [Idle memory](#idle-memory)
- [JDK 28 early access](#jdk-28-early-access)
- [The preview flag](#the-preview-flag)
- [Vec3, AABB and ChunkPos as value classes](#vec3-aabb-and-chunkpos-as-value-classes)

## JVM and heap

**Faster.** A 3 GB heap instead of 2 GB: full collections during play 5–6 → 2 per run, GC pauses 8.1 → 5.0 s (p = 0.007), CPU time −3.8 % (p = 0.013), flight tick time 32.4 → 30.5 ms (p = 0.034), at 1 GB more resident memory.
Against the same build with a 2 GB heap, 8 players exploring new terrain and then 1 200 mobs, 3 runs each.

Kindling recommends G1 with `-XX:+UseCompactObjectHeaders` and a 3 GB heap where the memory is there; `/kindling doctor` checks for them. A server jar cannot bring its own JVM flags. Measured and recommended:

- `-XX:+UseCompactObjectHeaders` (Java 25): live heap −5 %, GC time −17 %, no tick time cost.
- Heap 3 GB with G1 where the memory is there. Under full load (8 players, 2 900 entities) the server holds about 1.0 to 1.2 GB of live data, and generating new terrain allocates fast. With 2 GB, G1 ran out of room to copy into and fell back to full collections: 5 to 6 per run while the players explored, each stopping the server for 0.7 to 0.8 s (the longest pause per run 689 to 838 ms). With 3 GB that dropped to 2 per run, the GC pauses fell from 8.1 to 5.0 s and the CPU time by 3.8 %, at 1 GB more resident memory (2.8 → 3.8 GB). The two full collections that remain still pause 640 to 744 ms.
- **Trade-off** (against 2 GB without these flags; better in GC pauses, worse in CPU time and standing tick time): with 2 GB, add `-XX:G1ReservePercent=25 -XX:-G1UseAdaptiveIHOP -XX:InitiatingHeapOccupancyPercent=35`: G1 keeps a quarter of the heap free to copy into and starts marking at 35 % occupancy, so it never ran out of room. Full collections per run 6, 6, 7 → 0, 0, 0, the longest pause 762–849 → 120–202 ms, GC pauses 8.4 → 5.4 s (p = 0.005), at the same memory. The cost: CPU time 1 228 → 1 288 s (+4.9 %, p = 0.030), and the standing tick time rose from 35.8 to 37.3 ms (p = 0.044), since G1 marks more often (GC count 200 → 282). Aikar's well-known flag set halved the full collections (3 to 4 per run) without that cost, but its pauses stayed at 735–850 ms. These runs used the build with compiled material rules, 3 runs each.
- 2 GB without these flags still works; expect sub-second freezes while players explore new terrain, and pregenerate the area players use.
- The process needs about 500 MB beyond the heap at `-Xmx2G` (metaspace, thread stacks, code cache, direct buffers; [`kindling-bench`](../../kindling-bench/README.md)). `/kindling doctor` therefore wants `-Xmx` plus 600 MB to fit into the memory the server can use (container limit included), and counts every other server on the machine on top.

Not recommended:

- ZGC: −22 % peak memory and returns memory to the OS, but +11 % CPU and worse tick spikes.
- Generational Shenandoah: slower, barely less memory.
- G1 with a small `-Xms`: G1 returns practically nothing while the server ticks, because `G1PeriodicGCInterval` only acts after GC-free pauses.
- `-XX:G1HeapRegionSize=4M` with 2 GB: more full collections (8 to 9 per run), not fewer; large arrays are not what fills the heap.

**What the heap holds under load.** Class histograms after a full collection (one run, 3 GB heap, the same load) show mostly chunk data: about 210 MB of `long[]` (block storage), 160 MB of `Object[]` (palettes), 117 MB of `byte[]` (light) and 580 000 chunk sections. While the players stood among the mobs, 6 491 chunks were full and 11 181 were generated only part of the way (`ProtoChunk`): 63 % of the chunk objects are the ring of partly generated neighbours that a full chunk needs around each player, about 9 chunks beyond the full area. That is vanilla's chunk level design. [The next section](#the-ring-of-partly-generated-chunks) measures what they hold.

<details>
<summary>Method and all numbers</summary>

Mixed scenario of `kindling-bench` (8 creative players flying 90 s outward at 1 block per tick, then 150 mobs each, 90 s standing), build `f78a401b`, `-XX:+UseCompactObjectHeaders` and `-Xlog:gc,gc+humongous=debug`, variants `-Xmx2G`, `-Xmx2G -XX:G1HeapRegionSize=4M` and `-Xmx3G` (each with the same `-Xms`), 3 runs each, bench host. Full collections and the longest pause come from the GC log (pauses of `System.gc()`, which the bench calls itself, left out); the other rows from `compare.py` (labels `bt-2g`, `bt-2g-r4m`, `bt-3g`). Every full collection followed an evacuation failure (`Evacuation Failure: Allocation`, a few `Pinned`). The heap contents come from `jdk.ObjectCountAfterGC` in a JFR recording of a 2 GB run and from `jcmd GC.class_histogram` at 100, 140 and 230 s of a 3 GB run.

| | 2 GB | 2 GB, 4 MB regions | 3 GB |
| --- | --- | --- | --- |
| Full collections during play per run | 6, 5, 6 | 9, 8, 9 | 2, 2, 2 |
| Longest pause per run | 838, 689, 757 ms | 696, 647, 720 ms | 640, 660, 744 ms |
| GC pause time | 8 137 ±454 ms | 9 263 ±557 ms | 5 035 ±79 ms (p = 0.007) |
| Flight tick time avg | 32.4 ±0.2 ms | 32.7 ±1.3 ms | 30.5 ±0.7 ms (p = 0.034) |
| Standing tick time avg | 35.6 ±0.4 ms | 35.1 ±0.9 ms | 35.8 ±0.1 ms (p = 0.41) |
| CPU time | 1 252.6 ±10.9 s | 1 246.4 ±4.8 s | 1 205.5 ±4.5 s (p = 0.013) |
| Peak RSS | 2 799 MB | 2 802 MB | 3 841 MB |

G1 flags at 2 GB, on the build with compiled material rules (it generates faster, so its 2 GB baseline differs from the table above), same scenario, 3 runs each, labels `g1-2g`, `g1-aikar`, `g1-reserve`. Aikar's set as published, without `-XX:+DisableExplicitGC` (the bench calls `System.gc()` itself). The reserve variant is `-XX:G1ReservePercent=25 -XX:-G1UseAdaptiveIHOP -XX:InitiatingHeapOccupancyPercent=35`.

| | 2 GB | Aikar's flags | Reserve 25 %, marking at 35 % |
| --- | --- | --- | --- |
| Full collections during play per run | 6, 6, 7 | 3, 4, 3 | 0, 0, 0 |
| Longest pause per run | 849, 762, 795 ms | 850, 810, 735 ms | 120, 180, 202 ms |
| GC pause time | 8 437 ±506 ms | 6 519 ±290 ms (p = 0.009) | 5 351 ±172 ms (p = 0.005) |
| Flight tick time avg | 37.9 ±0.6 ms | 36.5 ±0.3 ms (p = 0.038) | 38.0 ±1.2 ms (p = 0.92) |
| Standing tick time avg | 35.8 ±0.6 ms | 36.0 ±0.1 ms (p = 0.54) | 37.3 ±0.2 ms (p = 0.044) |
| CPU time | 1 228.1 ±5.1 s | 1 228.7 ±1.0 s (p = 0.86) | 1 287.7 ±19.9 s (p = 0.030) |
| Peak RSS | 2 829 MB | 2 805 MB | 2 822 MB |

</details>

## The ring of partly generated chunks

**Indicative.** A fifth of the live heap under load is the fixed overhead of chunk sections, and 82 % of the sections hold only air; 8 % of the live heap is one eagerly allocated hash map per section.
Live class histograms (`jcmd GC.class_histogram`) and a chunk census every 400 ticks in the mixed load, 3 GB heap, a build of release 26.3-15 with measurement counters, bench host; one run with histograms, three with the census.

The 26.3 generation pyramid asks for `STRUCTURE_STARTS` at radius 8 around every later step and only radius 1 for the rest, so most of the ring never gets block data. The census of the overworld while the players stood counts 17 672 chunks with 424 128 sections, of which 347 610 (82 %) hold only air; 8 234 of the chunks sit at `structure_starts` with air only (same picture from tick 2 800 on, and in all three runs).

Every `ChunkAccess` allocates all 24 sections in its constructor, and each section carries two `PalettedContainer`s (with `Data`, a `FastHashMapPalette` and a `ZeroBitStorage` each), a Moonrise `ShortList` for its ticking blocks with a `Short2ShortOpenHashMap` and two `short[]`, and 52 Starlight nibble arrays per chunk. In the live histogram of the flight run (taken from outside with `jcmd`, so after a full collection) the fixed overhead of the sections is 229 MB (22 %) of a 1 057 MB live heap 120 s into the flight and 251 MB (20 %) of 1 234 MB while standing, about 427 bytes per section.

With 82 % of the sections holding only air, about 190 to 205 MB of the live heap is the fixed overhead of empty sections, most of it in the 9 204 ring chunks without any block data. Two exact ways to take it back: a shared empty section state for chunks below `BIOMES`, materialised on the first write (the lion's share, not built), and a `ShortList` that allocates its map on the first add instead of in the constructor (the 82 MB row; the list is empty in every air-only section and in most others; Moonrise code, built as Paper patch `0075` and measured in the next subsection).

<details>
<summary>Method and all numbers</summary>

The measurement build logs a census of every level's chunk holders (`ChunkHolderManager.getChunkHolders`, generation status, full status, class, sections and `hasOnlyAir`) every 400 ticks when started with `-Dkindling.measure=true`; it is not a patch. The histograms come from `jcmd <pid> GC.class_histogram` run from a watcher next to `run_bench.py` 120 s and 230 s after the JVM started (the flight runs from about +60 s to +150 s, the standing phase from +160 s), so each histogram forced one full collection into the run; the tick times of that run are not comparable with the bench rounds. The first two flight runs attached the watcher to the Python process by mistake and have no histogram; their census agrees with the third run. The per-section overhead sums the instances of the classes named in the table; the `Object[]` palette arrays are not attributed, so the fixed overhead is a lower bound.

Census of the overworld while the players stood (tick 4 000):

| Chunks | Status | Sections | of which air only |
| ---: | --- | ---: | ---: |
| 8 234 | `structure_starts`, `ProtoChunk` | 197 616 | 197 616 (100 %) |
| 970 | `biomes`, `ProtoChunk` | 23 280 | 23 280 (100 %) |
| 958 + 944 | `terrain`, `initialize_light`, `ProtoChunk` | 45 648 | 28 781 (63 %) |
| 6 566 | `full`, `LevelChunk` (2 312 entity ticking, 576 block ticking, 2 112 full, 1 566 inaccessible) | 157 584 | 97 933 (62 %) |
| 17 672 | all | 424 128 | 347 610 (82 %) |

Live histogram of the flight run (`measure-flight-c`):

| | Flight, +120 s | Standing, +230 s |
| --- | ---: | ---: |
| Live heap | 1 057 MB | 1 234 MB |
| Chunk sections (all dimensions) | 535 425 | 579 816 |
| Fixed overhead of the sections (section, two containers with data, palette and bit storage, the short list with its map and arrays, nibble arrays) | 229 MB (22 %) | 251 MB (20 %) |
| per section | 427 B | 433 B |
| of which the `ShortList` with its `Short2ShortOpenHashMap` and `short[]` | 82 MB (7.8 %) | 94 MB (7.6 %) |
| of which the two `PalettedContainer`s with `Data`, `FastHashMapPalette` and `ZeroBitStorage` | 101 MB | 109 MB |
| of which the 885 000 Starlight nibble arrays | 28 MB | 29 MB |
| Block storage `long[]` / light `byte[]` / palettes `Object[]` | 176 / 101 / 140 MB | 223 / 123 / 170 MB |

</details>

### The lazy ticking-list map

**Faster.** Heap after collection at the end of the run 1 198 → 1 141 MB (−57 MB, −4.8 %, p < 0.001); GC pauses, tick time and CPU time stay the same.
Against the release 26.3-15 jar, mixed load, 2 GB heap, 3 interleaved runs each (labels `cand-a` and `cand-b`; method in [the ticking page](ticking.md)).

The saving is less than the 82 MB of the histogram because the end-of-run heap holds fewer chunks than the flight did. It does not change the number of full collections at 2 GB: those come from the allocation rate and the young generation, not from 57 MB of old objects. The patch stays as a memory saving; the 3 GB advice and the 2 GB G1 flags stay as they are. The histogram also counts 419 000 live `AABB` (23 MB) and 707 000 `BlockPos`; those are the young generation's survivors of the allocation rate measured in [the main-thread profile](ticking.md).

<details>
<summary>All numbers</summary>

| Figure | 26.3-15 | + lazy map | p |
| --- | ---: | ---: | ---: |
| Heap after collection (end of the run) | 1 198 ±1 MB | 1 141 ±1 MB | < 0.001 \* |
| Full collections per run (`gc.log`) | 8, 8, 8 | 9, 7, 9 | |
| GC pauses | 8.3 ±0.3 s | 8.3 ±0.7 s | 0.98 |
| Travel tick time avg | 37.4 ±0.0 | 37.3 ±0.5 | 0.81 |
| CPU time | 1 246 ±8 s | 1 245 ±7 s | 0.93 |

</details>

## AOT cache and native memory

**Indicative.** AOT cache: billiards starts in 9.5 to 10.7 s instead of 16.9 to 26.9 s, smp in 19.2 s instead of 23 to 27 s.
Against starts without the cache, one start each, the production servers on a production VPS.

The AOT cache shortens the start; a native heap trim gives memory back. Measured on a production VPS with its servers, one start each:

- **AOT cache** (`-XX:AOTCache`, Java 25): the numbers above. The cache only works with a direct class path start; `java -jar` with Paperclip loads the server in its own class loader, where the cache does not apply.
- **Native heap trim**: one `jcmd <pid> System.trim_native_heap` freed 101 MB on billiards-1 and 103 MB on smp-1, so `/kindling doctor` points at `-XX:TrimNativeHeapInterval` (the interval it names is a suggestion; only the one-off trim was measured). Trimming while players are online can delay safepoints, so Kindling's own memory return runs only [while nobody is online](#idle-memory).

## Idle memory

**Faster.** Resident memory with nobody online 2 492 → 1 032 MiB (about 1.45 GiB less, −59 %), load figures unchanged. Opt-in, off by default.
Against the setting off, 3 runs each.

`performance.idle-memory` (`enabled: false` by default, `after-seconds: 300`; see the [configuration reference](../reference/configuration.md)) runs a full garbage collection with the free-heap ratios lowered to 5 and 10 percent for that one collection, then trims the native heap, after `after-seconds` without players. Nothing runs while a player is online or a pregeneration is working. A heap with `-Xms` equal to `-Xmx` cannot shrink; `/kindling doctor` says so, and `-Xms512M -Xmx2G` lets the idle collection give heap back.

<details>
<summary>Method and all numbers</summary>

Bench host, 4 fake players, `-Xms512M -Xmx2G -XX:+DisableExplicitGC`, 20 s warm-up, 40 s flight, 30 s standing, 75 s idle, `after-seconds: 30`; 3 runs on and 3 off, interleaved, plus 2 runs of the build that introduced the setting and 1 with GC logging. The bench kicks its fake players but never removes them from the player list, so a small helper plugin (kept in `runs/idle-memory/`, not in the repository) completes their disconnect as a real client would; without it the feature cannot fire. Resident memory comes from `/proc` once a second.

| Figure | Off | On |
| --- | ---: | ---: |
| Resident memory when the last player left | 2 481 ±56 MiB | 2 500 ±64 MiB |
| 50 s later | not recorded | 1 021 ±22 MiB |
| 70 s later | 2 492 ±52 MiB | **1 032 ±16 MiB** |
| Committed heap under load / at the end | 1 853 / 1 853 MiB | 1 816 / **512** MiB |
| Time until the feature fired | | 41 to 43 s after the last player left |
| Flight tick time avg / standing | 32.5 ±1.0 / 11.8 ms | 32.5 ±0.7 / 11.8 ms (p = 0.96) |
| Pause of the idle collection | | 204 ms (one run with GC logging) |

The one significant row of the comparison, the whole run's GC pause (1 946 ±36 against 2 062 ±43 ms, p = 0.028), is the 204 ms collection itself.

</details>

<details>
<summary>Caveats and open points</summary>

It only works when `-Xms` is below `-Xmx`: with `-Xms2G -Xmx2G`, a small test program kept 1.68 GB resident whatever was run (the trim freed 14 to 25 MB). Lowering the ratios saved a further 330 MiB over a plain full collection in that program (306 against 650 MiB with `-Xms256M`). **Not measured:** the tick spike when players rejoin after the heap shrank (the bench cannot rejoin players), the pregeneration case (the benched build still ran the collection while a pregeneration kept the server awake; the fix for that was not benched) and a real production server.

</details>

## JDK 28 early access

**Same.** The release jar ticks the same on JDK 28 early access as on JDK 25: standing among 1 200 mobs 40.6 → 40.2 ms, bases 11.5 → 11.8 ms, neither marked. Standing GC pauses 365 → 283 ms and collections per run 99 → 88 are marked.
Release 26.3-16 on Temurin 25.0.5 against OpenJDK `28-ea+21`, 3 interleaved runs each, bench host.

Paper refuses to start on an early-access JDK ("Unsupported Java detected") unless the JVM gets `-DPaper.IgnoreJavaVersion=true`. With it, the release jar ran both loads without an exception on JDK 28, and every bases counter was identical on both JDKs. Full collections other than the bench's own `System.gc()` stayed at 0 to 2 per mixed run, and none in the bases.

**Who allocates.** The bench's allocation figures (`main_alloc_mb`, `all_alloc_mb`) split the heap allocation of each phase between the main thread and all threads. While the eight players explore, 81 % of it is on threads other than the main thread: 31.5 GB in all against 6.0 GB on the main thread in the 90 s flight (JDK 25). The fake players drop their packets unencoded, so this is the server's own work off the main thread, chunk generation, loading and light among it; it is not split further by thread. While the players stand among the mobs, 98 % is on the main thread (12.6 of 12.8 GB, about 140 MB/s). A saving in main-thread allocation can therefore reach the standing phase and the bases, not the flight.

<details>
<summary>All numbers</summary>

`compare.py` cells: mean ±half range of 3 runs, p of Welch's t-test against JDK 25, `*` by [the rule](../MEASUREMENTS.md). The third column belongs to [the preview flag](#the-preview-flag).

| Figure | JDK 25.0.5 | JDK 28 EA | JDK 28 EA, `--enable-preview` |
| --- | ---: | ---: | ---: |
| Flight tick time avg | 32.2 ±2.9 ms | 34.0 ±1.2 ms (p = 0.41) | 32.9 ±0.9 ms (p = 0.71) |
| Flight tick time p95 | 57.3 ±3.5 ms | 58.0 ±2.1 ms (p = 0.78) | 58.1 ±0.6 ms (p = 0.73) |
| Standing tick time avg | 40.6 ±0.6 ms | 40.2 ±0.9 ms (p = 0.58) | 39.9 ±0.7 ms (p = 0.34) |
| Standing tick time p95 | 48.8 ±1.6 ms | 47.5 ±1.1 ms (p = 0.36) | 47.3 ±1.2 ms (p = 0.30) |
| Standing GC pauses | 365 ±31 ms | 283 ±15 ms (p = 0.033 \*) | 258 ±50 ms (p = 0.044 \*) |
| GC count, whole run | 99.0 ±3.0 | 88.0 ±3.0 (p = 0.011 \*) | 90.0 ±4.5 (p = 0.055) |
| GC pauses, whole run | 4 323 ±393 ms | 3 868 ±351 ms (p = 0.22) | 4 205 ±470 ms (p = 0.76) |
| Full collections per run (`gc.log`, without `System.gc()`) | 1, 0, 1 | 1, 1, 0 | 0, 1, 2 |
| Flight allocation, main thread / all threads | 6 023 / 31 457 MB | 6 453 / 31 958 MB | 6 153 / 31 612 MB |
| Standing allocation, main thread / all threads | 12 550 / 12 822 MB | 12 555 / 12 822 MB | 12 424 / 12 690 MB |
| New chunks per second in the flight | 129.4 ±5.5 | 137.9 ±2.0 (p = 0.12) | 130.5 ±0.5 (p = 0.77) |
| Join until full view | 34 091 ±1 390 ms | 34 108 ±449 ms (p = 0.99) | 34 882 ±1 339 ms (p = 0.52) |
| CPU time | 1 278 ±20 s | 1 281 ±12 s (p = 0.85) | 1 311 ±13 s (p = 0.11) |
| Peak RSS | 3 863 MB | 3 987 MB (p = 0.15) | 3 852 MB (p = 0.82) |
| Bases tick time avg | 11.5 ±0.1 ms | 11.8 ±0.2 ms (p = 0.16) | 12.7 ±0.2 ms (p = 0.006 \*) |
| Bases tick time p95 | 15.9 ±0.2 ms | 16.7 ±0.8 ms (p = 0.24) | 18.1 ±0.3 ms (p = 0.002 \*) |
| Bases allocation, main thread (per run) | 8 318, 8 341, 8 377 MB | 8 311, 9 333, 8 267 MB (p = 0.49) | 9 774, 9 801, 9 749 MB (p < 0.001 \*) |
| Bases counters | 15 | 15 identical | 15 identical |

</details>

<details>
<summary>Method</summary>

`kindling-bench` on the bench host, the mixed scenario with its defaults (8 creative players flying 90 s outward, then 150 mobs each, 90 s standing) and `type=bases`, the release jar 26.3-16 for all three variants. JVM flags for all: `-XX:ActiveProcessorCount=8 -XX:+UseCompactObjectHeaders -DPaper.IgnoreJavaVersion=true -Xlog:gc:file=gc.log:uptime`, a 3 GB heap; the third variant adds `--enable-preview`. JDK 25 is Temurin `25.0.5+8-LTS`, JDK 28 the OpenJDK early-access build `28-ea+21-1707`; each run records the runtime as `java_version`. Three rounds, each running every variant once per scenario before the next round (labels `vh-{mixed,bases}-{a,b,c}-rN`, merged per variant). Full collections are the `Pause Full` lines of `gc.log` without the two `System.gc()` the bench requests itself.

</details>

## The preview flag

**Slower.** `--enable-preview` on its own, with no value class of Kindling's, makes the bases tick time 11.8 → 12.7 ms (+7.6 %, p = 0.010); the mixed load's tick times do not move.
Against the same release jar on JDK 28 EA without the flag, 3 interleaved runs each, bench host; the numbers are in [the JDK 28 table](#jdk-28-early-access).

With the flag, the JDK's own value-based classes such as `Integer` become value classes; on this build `==` compares two separately boxed `Integer`s by value (checked with a small probe). The bases' main thread allocated about 9.8 GB per run with the flag. Without it, two of three runs allocated 8.3 GB, as on JDK 25, and one 9.3 GB, so that row is not marked against JDK 28 without the flag (p = 0.082), but it is against JDK 25 (p < 0.001). Where the extra time and allocation come from is not established. It is not the `Integer` cache: `Integer.valueOf` in the early-access sources still returns cached instances. A JFR allocation profile with and without the flag would show it.

In the mixed load the flag changes no tick time. JDK 28 without it sent more chunks in its flight than with it (9 057 against 8 502 packets, 137.9 against 130.5 new chunks per second, both marked), and the CPU time was 2.3 % higher with the flag (1 281 → 1 311 s, p = 0.044); against JDK 25 neither difference is marked, so they read as the spread of the flight rather than as a cost of the flag.

Since the next section runs both of its variants with the flag, this cost is the same on both sides there and drops out of that comparison.

## Vec3, AABB and ChunkPos as value classes

**Dropped.** As value classes, `Vec3`, `AABB` and `ChunkPos` cut the main thread's allocation while players stand among 1 200 mobs by 27 % (12.6 → 9.2 GB in 90 s, p < 0.001); tick times, GC pauses and full collections stay the same. Not adopted.
Against the same sources without the change, both built with `javac` 28 and `--enable-preview` and run on JDK 28 EA with it, 3 interleaved runs each, bench host.

The change is three declarations in the 26.3-16 sources: `public value class Vec3`, `public value class AABB` and `public value record ChunkPos`. A value class has no identity: the JIT can keep its instances in registers instead of allocating them, and `==` compares field values. Both builds compiled cleanly, the class files of the three types carry no `ACC_IDENTITY` flag (`Entity` still does), and in 12 runs plus a smoke run neither build logged an exception. All 15 bases counters were identical.

**Why the tick does not get faster.** A short-lived object costs the main thread almost nothing: allocating one bumps a pointer, and a young collection spends its time on the objects that survive, not on the dead ones. While the players stood, the GC paused 273 ms over 1 800 ticks without the change and 221 ms with it (p = 0.50), 0.15 against 0.12 ms per tick of 40.8 ms; even no GC at all could not save more than about 0.4 %. The rest of the tick is the standing profile on [the ticking page](ticking.md): 75 % entity ticks with no single frame above 3.1 %. The bases barely allocate these types (main thread 9 747 → 9 685 MB, p = 0.15), and their tick time did not move either (13.0 → 12.7 ms, p = 0.33). Not measured, but likely: JEP 401 lays a field of a value class out flat only where the JVM can read and write it atomically, and `Vec3` (three doubles) and `AABB` (six) are too large for that, so `Entity` still points to separate objects and only the temporaries go away.

**Why it is not adopted.**

- Value classes are a preview feature of JDK 28, which is not released yet. Every server would have to run an early-access or preview JDK with `--enable-preview`, against Kindling's Java 25 baseline.
- The flag alone costs 7.6 % in the bases ([the preview flag](#the-preview-flag)), more than anything the value classes could give back there.
- Plugins see the change: code that synchronizes on a `Vec3`, holds one in an identity map or a weak reference, or compares two with `==` behaves differently or throws `IdentityException`. In Kindling's own sources one such comparison exists, `v.predictedDelta != Vec3.ZERO` in Leaf's async tracker (`TrackerInput`), where a newly built zero vector now counts as `Vec3.ZERO`; it was not examined further.

The change can be measured again on a later JDK build without new code. Types that were looked at and left as they are: `BlockPos` and `SectionPos` (`MutableBlockPos` extends `BlockPos`, and `Vec3i` has mutable fields), `Identifier` and `TagKey` (both are interned through Guava's weak interners, and a weak reference to a value object throws), `BlockState` and the `HitResult` hierarchy.

<details>
<summary>All numbers</summary>

Without and with the value classes, both on JDK 28 EA with `--enable-preview`; p of Welch's t-test, `*` by [the rule](../MEASUREMENTS.md).

| Figure | Without | With | p |
| --- | ---: | ---: | ---: |
| Standing allocation, main thread | 12 596 ±113 MB | 9 183 ±123 MB | < 0.001 \* |
| Standing allocation, all threads | 12 857 ±113 MB | 9 552 ±128 MB | < 0.001 \* |
| Standing tick time avg (per run) | 40.8 ±0.4 ms (40.5, 40.7, 41.3) | 41.0 ±0.7 ms (41.1, 40.3, 41.6) | 0.74 |
| Standing tick time p95 | 48.0 ±0.3 ms | 48.6 ±1.4 ms | 0.62 |
| Standing collections per run | 11, 12, 15 | 11, 10, 11 | |
| Standing GC pauses | 273 ±102 ms | 221 ±62 ms | 0.50 |
| Flight allocation, main thread | 6 612 ±126 MB | 5 400 ±527 MB | 0.051 |
| Flight allocation, all threads | 32 452 ±1 072 MB | 31 006 ±1 862 MB | 0.33 |
| Flight tick time avg | 37.0 ±1.5 ms | 34.6 ±3.0 ms | 0.31 |
| Flight tick time p95 | 66.5 ±6.9 ms | 62.6 ±1.9 ms | 0.45 |
| Chunk packets in the flight (per run) | 9 325, 9 107, 8 967 | 9 279, 8 401, 7 708 | 0.28 |
| Join until full view | 32 576 ±409 ms | 34 914 ±794 ms | 0.029 \* |
| GC count / GC pauses, whole run | 90.7 / 4 420 ms | 91.0 / 4 230 ms | 0.91 / 0.70 |
| Full collections per run (`gc.log`, without `System.gc()`) | 2, 2, 0 | 0, 2, 1 | |
| CPU time | 1 311 ±12 s | 1 329 ±16 s | 0.19 |
| Peak RSS | 3 743 MB | 3 768 MB | 0.11 |
| Bases tick time avg / p95 / p99 | 13.0 / 18.7 / 23.8 ms | 12.7 / 18.0 / 21.9 ms | 0.33 / 0.31 / 0.20 |
| Bases allocation, main thread | 9 747 ±45 MB | 9 685 ±37 MB | 0.15 |
| Bases heap after collection | 465.0 ±0.0 MB | 469.0 ±1.0 MB | 0.020 \* |
| Bases counters | 15 | 15 identical | |

</details>

<details>
<summary>Method</summary>

Both builds come from the 26.3-16 sources (`02d01838`) with the root `build.gradle.kts` set to toolchain and `release` 28 and `--enable-preview` for `javac` and the tests; one of them adds `value` to the three declarations in the applied Minecraft sources. Neither is a patch of this repository. Built with `createPaperclipJar` against the early-access JDK (`-Porg.gradle.java.installations.paths=<jdk 28>`). The runs used the flags of [the JDK 28 section](#jdk-28-early-access) plus `--enable-preview`, the same scenarios and three interleaved rounds (labels `vh-{mixed,bases}-{d0,d1}-rN`), after a short smoke run of the value-class build that would have stopped the series on a failed start or any exception.

</details>

<details>
<summary>Caveats</summary>

- **The flight rows do not decide anything here.** How many chunks a flight delivers varies between runs (7 593 to 9 325 packets per run, 8 327 to 9 133 on average per variant across both series), and the flight's main-thread time follows it. The build without value classes against the release jar, both with the flag, shows the same: equal standing tick time (39.9 against 40.8 ms, p = 0.19), but 7 % more chunk packets in its flight and a slower flight (32.9 → 37.0 ms, marked).
- **The one marked row against the value classes is most likely the other build's spread.** The join until full view took 34.9 s with the value classes, as the release jar with the flag (34.9 s) and both JDKs without it (34.1 s); the build without the value classes was the fast one at 32.6 s.
- One machine (the bench host), three runs per variant. During one flight run of the JDK 28 section (`vh-mixed-c-r1`) about 170 MB were copied onto the host for about five seconds.
- Plugins were not tested with the value classes.

</details>
