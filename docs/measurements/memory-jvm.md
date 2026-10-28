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
> 🧪 **Peak RSS 3 590 → 2 549 MB (−29 %) at the same tick time with a 2 GB heap and compact object headers** · against Leaf with a 3 GB heap (mixed load table) · 2 runs each · 2026-10-26

Kindling recommends G1 with `-XX:+UseCompactObjectHeaders` and a right-sized heap; `/kindling doctor` checks for them.

A server jar cannot bring its own JVM flags. Measured and recommended:

- ✅ `-XX:+UseCompactObjectHeaders` (Java 25): live heap −5 %, GC time −17 %, no tick time cost.
- ✅ Heap 2 GB instead of 3 GB with G1: under full load (8 players, 2 900 entities) the server holds about 1.2 GB of live data; with 2 GB the process uses 1 GB less memory (−29 %) at the same tick time.
- The process needs about 500 MB beyond the heap at `-Xmx2G` (metaspace, thread stacks, code cache, direct buffers; `kindling-bench`). `/kindling doctor` therefore wants `-Xmx` plus 600 MB to fit into the memory the server can use (container limit included), and counts every other server on the machine on top.
- ❌ Not recommended:
  - ZGC: −22 % peak memory and returns memory to the OS, but +11 % CPU and worse tick spikes.
  - Generational Shenandoah: slower, barely less memory.
  - G1 with a small `-Xms`: G1 returns practically nothing while the server ticks, because `G1PeriodicGCInterval` only acts after GC-free pauses.

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
