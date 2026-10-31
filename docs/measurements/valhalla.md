# ☕ JDK 28 and value classes

[← Overview](../MEASUREMENTS.md)

Whether Kindling runs on the JDK 28 early-access build, what `--enable-preview` costs on its own, and what turning `Vec3`, `AABB` and `ChunkPos` into value classes (Project Valhalla, [JEP 401](https://openjdk.org/jeps/401)) does to allocation and tick time. None of it is adopted: Kindling stays on Java 25.

**Contents**

- [JDK 28 early access](#jdk-28-early-access)
- [The preview flag alone](#the-preview-flag-alone)
- [Vec3, AABB and ChunkPos as value classes](#vec3-aabb-and-chunkpos-as-value-classes)

---

## JDK 28 early access

> [!NOTE]
> 🤝 **The release jar ticks the same on JDK 28 early access as on JDK 25 (standing among 1 200 mobs 40.6 → 40.2 ms, bases 11.5 → 11.8 ms, neither marked); standing GC pauses 365 → 283 ms and collections per run 99 → 88, both marked** · release 26.3-16 on Temurin 25.0.5 against OpenJDK `28-ea+21` · 3 interleaved runs each · bench host · 2026-10-31

Paper refuses to start on an early-access JDK ("Unsupported Java detected") unless the JVM gets `-DPaper.IgnoreJavaVersion=true`. With it, the release jar ran both loads without an exception on JDK 28, and every bases counter was identical on both JDKs. Full collections other than the bench's own `System.gc()` stayed at 0 to 2 per mixed run, and none in the bases.

**Who allocates.** The bench's allocation figures (`main_alloc_mb`, `all_alloc_mb`) split the heap allocation of each phase between the main thread and all threads. While the eight players explore, 81 % of it is on threads other than the main thread: 31.5 GB in all against 6.0 GB on the main thread in the 90 s flight (JDK 25). The fake players drop their packets unencoded, so this is the server's own work off the main thread, chunk generation, loading and light among it; it is not split further by thread. While the players stand among the mobs, 98 % is on the main thread (12.6 of 12.8 GB, about 140 MB/s). A saving in main-thread allocation can therefore reach the standing phase and the bases, not the flight.

<details>
<summary><b>All numbers</b></summary>

`compare.py` cells: mean ±half range of 3 runs, p of Welch's t-test against JDK 25, `*` by [the rule](../MEASUREMENTS.md#how-to-read-the-numbers).

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
<summary><b>Method</b></summary>

`kindling-bench` on the bench host, the mixed scenario with its defaults (8 creative players flying 90 s outward, then 150 mobs each, 90 s standing) and `type=bases`, the release jar 26.3-16 for all three variants. JVM flags for all: `-XX:ActiveProcessorCount=8 -XX:+UseCompactObjectHeaders -DPaper.IgnoreJavaVersion=true -Xlog:gc:file=gc.log:uptime`, a 3 GB heap; the third variant adds `--enable-preview`. JDK 25 is Temurin `25.0.5+8-LTS`, JDK 28 the OpenJDK early-access build `28-ea+21-1707`; each run records the runtime as `java_version`. Three rounds, each running every variant once per scenario before the next round (labels `vh-{mixed,bases}-{a,b,c}-rN`, merged per variant). Full collections are the `Pause Full` lines of `gc.log` without the two `System.gc()` the bench requests itself.

</details>

## The preview flag alone

> [!NOTE]
> 🔻 **`--enable-preview` on its own, with no value class of Kindling's, makes the bases 11.8 → 12.7 ms (+7.6 %, p = 0.010); the mixed load's tick times do not move** · the same release jar on JDK 28 EA without the flag · 3 interleaved runs each · bench host · 2026-10-31

With the flag, the JDK's own value-based classes such as `Integer` become value classes; on this build `==` compares two separately boxed `Integer`s by value (checked with a small probe). The bases' main thread allocated about 9.8 GB per run with the flag. Without it, two of three runs allocated 8.3 GB, as on JDK 25, and one 9.3 GB, so that row is not marked against JDK 28 without the flag (p = 0.082), but it is against JDK 25 (p < 0.001). Where the extra time and allocation come from is not established. It is not the `Integer` cache: `Integer.valueOf` in the early-access sources still returns cached instances. A JFR allocation profile with and without the flag would show it.

In the mixed load the flag changes no tick time. JDK 28 without it sent more chunks in its flight than with it (9 057 against 8 502 packets, 137.9 against 130.5 new chunks per second, both marked), and the CPU time was 2.3 % higher with the flag (1 281 → 1 311 s, p = 0.044); against JDK 25 neither difference is marked, so they read as the spread of the flight rather than as a cost of the flag.

Since the next section runs both of its variants with the flag, this cost is the same on both sides there and drops out of that comparison.

## Vec3, AABB and ChunkPos as value classes

> [!NOTE]
> ❌ **As value classes, `Vec3`, `AABB` and `ChunkPos` cut the main thread's allocation while players stand among 1 200 mobs by 27 % (12.6 → 9.2 GB in 90 s, p < 0.001); tick times, GC pauses and full collections stay the same. Not adopted** · the same sources without the change, both built with `javac` 28 and `--enable-preview` and run on JDK 28 EA with it · 3 interleaved runs each · bench host · 2026-10-31

The change is three declarations in the 26.3-16 sources: `public value class Vec3`, `public value class AABB` and `public value record ChunkPos`. A value class has no identity: the JIT can keep its instances in registers instead of allocating them, and `==` compares field values. Both builds compiled cleanly, the class files of the three types carry no `ACC_IDENTITY` flag (`Entity` still does), and in 12 runs plus a smoke run neither build logged an exception. All 15 bases counters were identical.

**Why the tick does not get faster.** A short-lived object costs the main thread almost nothing: allocating one bumps a pointer, and a young collection spends its time on the objects that survive, not on the dead ones. While the players stood, the GC paused 273 ms over 1 800 ticks without the change and 221 ms with it (p = 0.50), 0.15 against 0.12 ms per tick of 40.8 ms; even no GC at all could not save more than about 0.4 %. The rest of the tick is the [standing profile](ticking.md#where-the-time-goes-main-thread-profile): 75 % entity ticks with no single frame above 3.1 %. The bases barely allocate these types (main thread 9 747 → 9 685 MB, p = 0.15), and their tick time did not move either (13.0 → 12.7 ms, p = 0.33). Not measured, but likely: JEP 401 lays a field of a value class out flat only where the JVM can read and write it atomically, and `Vec3` (three doubles) and `AABB` (six) are too large for that, so `Entity` still points to separate objects and only the temporaries go away.

**Why it is not adopted.**

- Value classes are a preview feature of JDK 28 (release planned for March 2027). Every server would have to run an early-access or preview JDK with `--enable-preview`, against Kindling's Java 25 baseline.
- The flag alone costs 7.6 % in the bases ([above](#the-preview-flag-alone)), more than anything the value classes could give back there.
- Plugins see the change: code that synchronizes on a `Vec3`, holds one in an identity map or a weak reference, or compares two with `==` behaves differently or throws `IdentityException`. In Kindling's own sources one such comparison exists, `v.predictedDelta != Vec3.ZERO` in Leaf's async tracker (`TrackerInput`), where a newly built zero vector now counts as `Vec3.ZERO`; it was not examined further.

The change can be measured again on a later JDK build without new code. Types that were looked at and left as they are: `BlockPos` and `SectionPos` (`MutableBlockPos` extends `BlockPos`, and `Vec3i` has mutable fields), `Identifier` and `TagKey` (both are interned through Guava's weak interners, and a weak reference to a value object throws), `BlockState` and the `HitResult` hierarchy.

<details>
<summary><b>All numbers</b></summary>

Without and with the value classes, both on JDK 28 EA with `--enable-preview`; p of Welch's t-test, `*` by [the rule](../MEASUREMENTS.md#how-to-read-the-numbers).

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
<summary><b>Method</b></summary>

Both builds come from the 26.3-16 sources (`02d01838`) with the root `build.gradle.kts` set to toolchain and `release` 28 and `--enable-preview` for `javac` and the tests; one of them adds `value` to the three declarations in the applied Minecraft sources. Neither is a patch of this repository. Built with `createPaperclipJar` against the early-access JDK (`-Porg.gradle.java.installations.paths=<jdk 28>`). The runs used the flags of [the JDK section](#jdk-28-early-access) plus `--enable-preview`, the same scenarios and three interleaved rounds (labels `vh-{mixed,bases}-{d0,d1}-rN`), after a short smoke run of the value-class build that would have stopped the series on a failed start or any exception.

</details>

<details>
<summary><b>Caveats</b></summary>

- **The flight rows do not decide anything here.** How many chunks a flight delivers varies between runs (7 593 to 9 325 packets per run, 8 327 to 9 133 on average per variant across both series), and the flight's main-thread time follows it. The build without value classes against the release jar, both with the flag, shows the same: equal standing tick time (39.9 against 40.8 ms, p = 0.19), but 7 % more chunk packets in its flight and a slower flight (32.9 → 37.0 ms, marked).
- **The one marked row against the value classes is most likely the other build's spread.** The join until full view took 34.9 s with the value classes, as the release jar with the flag (34.9 s) and both JDKs without it (34.1 s); the build without the value classes was the fast one at 32.6 s.
- One machine (the bench host), three runs per variant. During one flight run of the JDK section (`vh-mixed-c-r1`) about 170 MB were copied onto the host for about five seconds.
- Plugins were not tested with the value classes.

</details>
