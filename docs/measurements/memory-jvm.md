# 🧠 JVM, start time and memory

[← Overview](../MEASUREMENTS.md)

Which JVM flags Kindling recommends, how much faster a start gets with the AOT cache, and what gives memory back while nobody is online.

**Contents**

- [JVM and heap](#jvm-and-heap)
- [Start time and native memory (Cubyx VPS)](#start-time-and-native-memory-cubyx-vps)
- [Idle memory](#idle-memory)

---

## JVM and heap

> [!NOTE]
> 🏆 **A 3 GB heap instead of 2 GB: full collections during play 5–6 → 2 per run, GC pauses 8.1 → 5.0 s (p = 0.007), CPU time −3.8 % (p = 0.013), flight tick time 32.4 → 30.5 ms (p = 0.034); 1 GB more resident memory** · 8 players exploring new terrain, then 1 200 mobs · 3 runs each · 2026-10-29
>
> 🧪 Older: peak RSS 3 590 → 2 549 MB (−29 %) with a 2 GB heap at the same tick time · against Leaf with a 3 GB heap · 2 runs each · 2026-10-26

Kindling recommends G1 with `-XX:+UseCompactObjectHeaders` and a 3 GB heap where the memory is there; `/kindling doctor` checks for them.

A server jar cannot bring its own JVM flags. Measured and recommended:

- ✅ `-XX:+UseCompactObjectHeaders` (Java 25): live heap −5 %, GC time −17 %, no tick time cost.
- ✅ Heap 3 GB with G1 where the memory is there. Under full load (8 players, 2 900 entities) the server holds about 1.0 to 1.2 GB of live data, and generating new terrain allocates fast. With 2 GB, G1 ran out of room to copy into and fell back to full collections: 5 to 6 per run while the players explored, each stopping the server for 0.7 to 0.8 s (the longest pause per run 689 to 838 ms). With 3 GB that dropped to 2 per run, the GC pauses fell from 8.1 to 5.0 s and the CPU time by 3.8 %, at 1 GB more resident memory (2.8 → 3.8 GB). The two full collections that remain still pause 640 to 744 ms.
- 2 GB still works on a machine with less memory; expect sub-second freezes while players explore new terrain, and pregenerate the area players use. The earlier advice of 2 GB (−29 % memory at the same tick time) came from two runs whose figures did not include the longest pause or the number of full collections.
- The process needs about 500 MB beyond the heap at `-Xmx2G` (metaspace, thread stacks, code cache, direct buffers; `kindling-bench`). `/kindling doctor` therefore wants `-Xmx` plus 600 MB to fit into the memory the server can use (container limit included), and counts every other server on the machine on top.
- ❌ Not recommended:
  - ZGC: −22 % peak memory and returns memory to the OS, but +11 % CPU and worse tick spikes.
  - Generational Shenandoah: slower, barely less memory.
  - G1 with a small `-Xms`: G1 returns practically nothing while the server ticks, because `G1PeriodicGCInterval` only acts after GC-free pauses.
  - `-XX:G1HeapRegionSize=4M` with 2 GB: more full collections (8 to 9 per run), not fewer; large arrays are not what fills the heap.

**What the heap holds under load.** Class histograms after a full collection (one run, 3 GB heap, the same load) show mostly chunk data: about 210 MB of `long[]` (block storage), 160 MB of `Object[]` (palettes), 117 MB of `byte[]` (light) and 580 000 chunk sections. While the players stood among the mobs, 6 491 chunks were full and 11 181 were generated only part of the way (`ProtoChunk`): 63 % of the chunk objects are the ring of partly generated neighbours that a full chunk needs around each player, about 9 chunks beyond the full area. That is vanilla's chunk level design, and the largest single share of the heap that is not gameplay; it has not been reduced.

<details>
<summary><b>Method</b></summary>

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

</details>

## Start time and native memory (Cubyx VPS)

> [!NOTE]
> 🧪 **AOT cache: billiards starts in 9.5 to 10.7 s instead of 16.9 to 26.9 s, smp in 19.2 s instead of 23 to 27 s** · against starts without the cache · one start each, production servers on the Cubyx VPS · undated

The AOT cache shortens the start; a native heap trim gives memory back.

Measured on the Cubyx VPS with the production servers, one start each:

- **AOT cache** (`-XX:AOTCache`, Java 25): billiards started in 9.5 to 10.7 s instead of 16.9 to 26.9 s, smp in 19.2 s instead of 23 to 27 s. The cache only works with a direct class path start; `java -jar` with Paperclip loads the server in its own class loader, where the cache does not apply.
- **Native heap trim**: one `jcmd <pid> System.trim_native_heap` freed 101 MB on billiards-1 and 103 MB on smp-1, so `/kindling doctor` points at `-XX:TrimNativeHeapInterval` (the interval it names is a suggestion; only the one-off trim was measured). Trimming while players are online can delay safepoints, so Kindling's own memory return runs only [while nobody is online](#idle-memory).

## Idle memory

> [!NOTE]
> ⏸️ **Resident memory with nobody online 2 492 → 1 032 MiB (about 1.45 GiB less, −59 %), load figures unchanged** · off by default · against the setting off · 3 runs each · 2026-10-28

`performance.idle-memory` (`enabled: false` by default, `after-seconds: 300`) runs a full garbage collection with the free-heap ratios lowered to 5 and 10 percent for that one collection, then trims the native heap, after `after-seconds` without players. Nothing runs while a player is online or a pregeneration is working. A heap with `-Xms` equal to `-Xmx` cannot shrink; `/kindling doctor` says so, and `-Xms512M -Xmx2G` lets the idle collection give heap back.

| Figure | Off | On |
| --- | ---: | ---: |
| Resident memory when the last player left | 2 481 ±56 MiB | 2 500 ±64 MiB |
| 50 s later | not recorded | 1 021 ±22 MiB |
| 70 s later | 2 492 ±52 MiB | **1 032 ±16 MiB** |
| Committed heap under load / at the end | 1 853 / 1 853 MiB | 1 816 / **512** MiB |
| Time until the feature fired | | 41 to 43 s after the last player left |
| Flight tick time avg / standing | 32.5 ±1.0 / 11.8 ms | 32.5 ±0.7 / 11.8 ms (p = 0.96) |
| Pause of the idle collection | | 204 ms (one run with GC logging) |

<details>
<summary><b>Method</b></summary>

`performance.idle-memory` runs a full collection with lowered free-heap ratios and a native heap trim after `after-seconds` without players. Bench host, 4 fake players, `-Xms512M -Xmx2G -XX:+DisableExplicitGC`, 20 s warm-up, 40 s flight, 30 s standing, 75 s idle, `after-seconds: 30`; 3 runs on and 3 off, interleaved, plus 2 of the previous build and 1 with GC logging. The bench kicks its fake players but never removes them from the player list, so a small helper plugin (kept in `runs/idle-memory/`, not in the repository) completes their disconnect as a real client would; without it the feature cannot fire. Resident memory comes from `/proc` once a second.

</details>

<details>
<summary><b>All numbers</b></summary>

About 1.45 GiB less resident memory (−59 %) with nobody online; the load figures are unchanged. The one significant row of the comparison, the whole run's GC pause (1 946 ±36 against 2 062 ±43 ms, p = 0.028), is the 204 ms collection itself.

</details>

<details>
<summary><b>Caveats and open points</b></summary>

It only works when `-Xms` is below `-Xmx`: with `-Xms2G -Xmx2G`, a small test program in an earlier session kept 1.68 GB resident whatever was run (the trim freed 14 to 25 MB). Lowering the ratios saved a further 330 MiB over a plain full collection in that program (306 against 650 MiB with `-Xms256M`). **Not measured:** the tick spike when players rejoin after the heap shrank (the bench cannot rejoin players), the pregeneration case (the benched build still ran the collection while a pregeneration kept the server awake; the later fix for that was not benched) and a real production server.

</details>

---

[← Overview](../MEASUREMENTS.md)
