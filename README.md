<div align="center">

<h1>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/branding/holzschnitt/banner-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="docs/branding/holzschnitt/banner-light.svg">
    <img alt="Kindling" src="docs/branding/holzschnitt/banner-light.svg" width="1280">
  </picture>
</h1>

**A [Leaf](https://github.com/Winds-Studio/Leaf) fork for small and mid-sized survival servers on shared hardware.**<br>
Vanilla behaviour, the full vanilla world, and every change measured.

[![Release](https://img.shields.io/github/v/release/jonax1337/Kindling?include_prereleases&label=release&style=for-the-badge&labelColor=19291f&color=f56b27)](https://github.com/jonax1337/Kindling/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/jonax1337/Kindling/build.yml?branch=ver%2F26.3&label=build&style=for-the-badge&labelColor=19291f)](https://github.com/jonax1337/Kindling/actions/workflows/build.yml)
![Minecraft](https://img.shields.io/badge/Minecraft-26.3-f56b27?style=for-the-badge&labelColor=19291f)
![Java](https://img.shields.io/badge/Java-25-556457?style=for-the-badge&labelColor=19291f)
[![Based on Leaf](https://img.shields.io/badge/based%20on-Leaf-556457?style=for-the-badge&labelColor=19291f)](https://github.com/Winds-Studio/Leaf)
[![License](https://img.shields.io/badge/license-MIT%20%2F%20GPL--3.0-556457?style=for-the-badge&labelColor=19291f)](LICENSE.md)

[Website](https://jonax1337.github.io/Kindling/) ·
[At a glance](#-at-a-glance-kindling-against-plain-leaf) ·
[Features](#-what-kindling-adds-on-top-of-leaf) ·
[Getting started](#-getting-started) ·
[Configuration](#-configuration) ·
[Changelog](CHANGELOG.md) ·
[Measurements](docs/MEASUREMENTS.md)

</div>

---

Kindling makes the parts that hurt on a cheap VPS cheaper: fast players outrunning world generation, pregeneration, loading chunks from disk, and memory. Every change has to win against a reproducible load test ([`kindling-bench`](kindling-bench/README.md)), and changes that only win by altering gameplay (mob spawn throttling, distant entity tick skipping) are not defaults.

> [!WARNING]
> Kindling is young and has one production user so far; every build is published as a pre-release. Take a backup of your worlds before switching, as with any server software.

## 📊 At a glance: Kindling against plain Leaf

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/media/kindling-vs-leaf-dark.svg">
  <img alt="Bar chart of Kindling's change against plain Leaf per figure: time over the void in the flight −98 %, flight tick time p95 −63 %, join until the full view −31 %, flight tick time average −26 %, standing tick time −20 %, survival bases tick time −6 % (not significant), CPU time of the bases run +26 %, CPU time of the flight-and-mobs run +53 %." src="docs/media/kindling-vs-leaf-light.svg" width="760">
</picture>

| | Leaf | Kindling | |
| --- | ---: | ---: | :---: |
| 🛩️ Fliers over ungenerated void | 85.8 % | **2.0 %** | 🏆 |
| ⏱️ Tick time while flying, average / p95 | 37.7 / 174.9 ms | **27.8 / 65.2 ms** | 🏆 |
| 🚪 Join until the full view | 57.4 s | **39.9 s** | 🏆 |
| 🐄 Tick time standing among 1 200 mobs | 44.4 ms | **35.4 ms** | 🏆 \* |
| 🏠 Tick time in survival bases | 12.5 ms | 11.7 ms | 🤝 |
| 🔥 CPU time, flight run / bases run | **927 / 298 s** | 1 416 / 376 s | 🔻 |

Leaf `66f99f8e` against Kindling 26.3-12, each with its own defaults, 3 runs each on a 4-core, 8-thread Intel i7-7700 under Linux. 🏆 Kindling better · 🤝 no significant difference · 🔻 Kindling costs more. \* Leaf was still generating the players' surroundings while they stood, which flatters Kindling.

**In short:** Kindling wins where players outrun world generation. In survival bases it is a draw, and it uses more CPU time. Release 26.3-11 measured the same as 26.3-12. [Details and method →](docs/measurements/vs-leaf.md#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf)

## ✨ What Kindling adds on top of Leaf

| | Feature | What you get | Measured |
| --- | --- | --- | --- |
| 🗺️ | **Built-in pregenerator** | `/pregen start <world> <radius>`, `pause`, `resume`, `status`; steps back when ticks get slow, resumes after a restart. No Chunky needed | 🧪 +59 % chunks per minute, −41 % CPU per chunk against its first version · [more](docs/measurements/pregeneration.md#pregeneration) |
| 🧭 | **Directional chunk loading** | The chunks ahead of a moving player are loaded first | 🏆 with the two rows below: 2.0 % instead of 85.8 % of a flight over the void against plain Leaf · [more](docs/measurements/chunks.md#elytra-test-new-chunks-ahead-of-fast-players) |
| ⚙️ | **Chunk workers sized for fast players** | Five eighths of the cores, at most five, never fewer than Paper (2 → 5 on 8 cores) | 🧪 0 % instead of 24 % void with eight fliers on 12 cores, against Paper's count · [more](docs/measurements/chunks.md#default-number-of-chunk-workers) |
| 🚦 | **Shorter generation queues** | Paper's automatic number of concurrent generations per player is capped at 32, so the order keeps up | 🧪 0.9 % instead of 38.5 % void on the 8-thread bench host, same CPU time · [more](docs/measurements/chunks.md#concurrent-generations-per-player) |
| 📦 | **Chunk loading without DataFixerUpper** | Saved block and biome palettes are read straight from NBT | 🛡️ same result, checked block by block; less chunk-worker time · [more](docs/measurements/chunks.md#loading-chunks-from-disk) |
| 🛟 | **Copies of unreadable chunks** | A stored chunk that cannot be read is copied to `<world>/kindling-quarantine/` before it is generated anew | 🛡️ checked with damaged region files · [more](docs/measurements/chunks.md#copies-of-unreadable-chunks) |
| 💤 | **Sleeping block entities, plugin-safe** | Idle hoppers, furnaces, brewing stands, crafters and chests stop ticking; Kindling makes Leaf's port act as Paper | 🏆 −8 % tick time in survival bases with identical items (on against off) · 🛡️ no duplicated items · [more](docs/measurements/ticking.md#sleeping-block-entities-after-the-fixes) |
| 🔌 | **Lithium ports** | Redstone wire power, unused mob sensors, the rain tick and map names, with vanilla's results | 🏆 −7 % in the bases in their own A/B · 🤝 not visible against 26.3-11 · [more](docs/measurements/ticking.md#lithium-ports) |
| 🐑 | **Cheaper natural spawning** | Entity types without a spawn cost skip the biome lookup | 🤝 no measurable change, equal spawn counts · [more](docs/measurements/ticking.md#natural-spawning-skipped-spawn-cost-lookups) |
| ⏱️ | **Cheaper lagging check** | Computes only the 5-second TPS average the check uses; proposed to Leaf as [#970](https://github.com/Winds-Studio/Leaf/pull/970) | 🧪 13.6 % → 3.4 % of an idle main thread (one profile each) · [more](docs/measurements/ticking.md#the-lagging-check-of-the-tick-loop) |
| 🧵 | **Async defaults, vanilla-safe** | Leaf's async entity tracker, pathfinding, player data saving and mob spawning are on, with Kindling's fixes (one mirrors Leaf PR #937) | 🛡️ shared villager beds 5 and 3 → 0; the fixes cost nothing measurable · [more](docs/measurements/async.md#async-pathfinding-tracker-and-saving-after-the-fixes) |
| 🩺 | **`/kindling doctor`** | Checks the machine and the settings and names the fix; on Linux also live CPU wait, steal and throttling | 🛡️ live readings agree with `/proc` within half a point · [more](docs/measurements/host.md#the-doctors-live-readings) |
| 🧠 | **Measured JVM advice** | G1 with `-XX:+UseCompactObjectHeaders` and a 2 GB heap | 🧪 −29 % resident memory at the same tick time · [more](docs/measurements/memory-jvm.md#jvm-and-heap) |
| 💾 | **Idle memory return** | Opt-in: gives memory back while nobody is online | ⏸️ about 1.45 GiB less resident memory with nobody online · [more](docs/measurements/memory-jvm.md#idle-memory) |
| 🪫 | **Worker priority** | Opt-in: chunk and I/O workers run at a lower OS priority on Linux | ⏸️ halves the main thread's wait for a CPU on the bench host · [more](docs/measurements/host.md#worker-priority-and-the-main-threads-wait-for-a-cpu) |
| 🏷️ | **Own `/version`** | Shows the Kindling build, commit and date, and checks for a newer release | — |

🏆 better, significant · 🤝 no measurable difference · 🛡️ correctness, no speed claim · 🧪 fewer than three runs or one profile, below the [three-run rule](docs/MEASUREMENTS.md#how-to-read-the-numbers) · ⏸️ opt-in, off by default. Every measurement names what it was compared with; the overview is in [docs/MEASUREMENTS.md](docs/MEASUREMENTS.md).

Everything else is Leaf, unchanged: Paper, Gale, Pufferfish and Purpur patches, Leaf's configuration (`config/leaf-global.yml`), its commands and its plugin compatibility. Swapping Leaf for Kindling needs no config migration. Kindling reports the brand id `winds-studio:leaf`, so plugins that check for Leaf keep working.

## 🚀 Getting started

1. Download the newest `kindling-26.3-<build>.jar` from [Releases](https://github.com/jonax1337/Kindling/releases).
2. Use it like a Leaf or Paper jar:
   ```bash
   java -Xms2G -Xmx2G -XX:+UseCompactObjectHeaders -jar kindling-26.3-<build>.jar --nogui
   ```
3. Leave `chunk-system.worker-threads` in `config/paper-global.yml` at `-1`: Kindling then picks the measured default (5 on 8 cores). If several servers share the machine and generate at the same time, lower it per server.
4. Pregenerate the area your players will use: `/pregen start world 5000`. Watch it with `/pregen status`.

> [!TIP]
> Kindling's defaults only apply when Leaf writes `config/leaf-global.yml` fresh. Coming from Leaf with an existing file, set `async.async-entity-tracker.enabled`, `async.async-pathfinding.enabled`, `async.async-playerdata-save.enabled` and `performance.sleeping-block-entity` to `true` yourself.

## 🔧 Configuration

All of Kindling's settings live in `config/leaf-global.yml`. What changed in which build: [CHANGELOG.md](CHANGELOG.md).

### Pregeneration

```yaml
performance:
  pregeneration:
    max-mspt: 40.0        # while the average tick takes longer: halves its chunks in flight, starts none
    max-parallel: 32      # upper bound for chunks in flight
    min-free-disk-gb: 2   # stops by itself when finishing would leave less free on the world's disk
    max-main-thread-wait: 0     # Linux: also throttles while the main thread waited for a CPU longer than this share of the last minute (e.g. 0.20); 0 (default) turns it off
    automatic:
      enabled: false      # true: start the radii below on every startup until done
      overworld-radius: 5000   # blocks around the world spawn, 0 for none
      nether-radius: 1000
      end-radius: 3000
```

`/pregen start <world> <radius> [<x> <z>]` centres on the world spawn unless both `<x> <z>` are given, rounds the radius down to whole chunks, cuts it to the world border and says what it started. Permission: `kindling.command.pregen` (operators by default).

<details>
<summary><b>Status, state file and safety</b></summary>

- `/pregen status` shows why a run is slow or waiting (`throttled: 43 ms average > 40 ms`, `throttled: the main thread waited for a CPU 18 % of the last minute > 10 %`, `paused`, `paused: the disk would run out`) and the free disk space against what the rest of the run still needs (an estimate from the space the disk lost per chunk so far).
- Progress is kept in `<world>/pregeneration.properties` and belongs to the world's seed: a world regenerated under the same name starts over.
- While a run is working, `pause-when-empty-seconds` does not pause the server.
- The main thread's wait for a CPU comes from the doctor's host monitor (below); where it has no data (Windows, `host-monitor: false`) only `max-mspt` throttles.
- A run that throws pauses itself and the server goes on; a world that is unloaded and loaded again resumes; the state file is written on a thread of its own and, at a clean stop, with the exact index.

</details>

The wait throttle (`max-main-thread-wait`) is off by default: at 0.10 it cut pregeneration by more than half on the bench host, and on a VPS a nearly idle main thread already waits 11 to 14 % of its time, so it fired at baseline. 0.20 was not tested ([why](docs/measurements/host.md#worker-priority-and-the-main-threads-wait-for-a-cpu)).

### Doctor

```yaml
performance:
  doctor:
    startup-check: true   # log the warnings once the server has started
    host-monitor: true    # Linux only: sample the host every 5 seconds for the LIVE section
```

`/kindling doctor` (permission `kindling.command.doctor`, operators by default) reads this machine and server and lists what costs performance or memory, grouped into `WARN`, `INFO` and `OK`. Every `WARN` and `INFO` that has something to change names its fix and says whether the advice rests on a Kindling measurement or is general advice. Anything the system cannot tell (for example the Linux-only values on Windows) is skipped instead of guessed. With `startup-check` the server logs each `WARN` (one line with its fix) on its first tick; hints and OK lines only show in the command.

<details>
<summary><b>What it checks</b></summary>

- processors and the container CPU limit;
- `-Xmx` against the memory (heap plus about 600 MB must fit);
- the garbage collector, `-XX:+UseCompactObjectHeaders` and the AOT cache;
- chunk worker threads;
- the malloc arena limit (`MALLOC_ARENA_MAX` or `GLIBC_TUNABLES`), `-XX:TrimNativeHeapInterval` and transparent huge pages;
- view and simulation distance against the processors;
- network compression behind a proxy (a warning when the proxy runs on this machine or its place is unknown, a hint when it runs elsewhere);
- free disk space of the worlds (a warning below 5 GiB or 10 %);
- Kindling's recommended options in `leaf-global.yml` (a fresh file has them on, an older file keeps its values).

</details>

<details>
<summary><b>The LIVE section (Linux): is the lag my server or my host?</b></summary>

In plain sentences over the last minute (with the last 15 minutes in brackets):

- how long the main thread waited for a CPU: a warning above 10 % of the time it had work to do and at least 20 ms per second, because then other threads or processes on the machine compete with it;
- how long the chunk workers waited;
- how much CPU time the host took from this virtual machine (steal, a warning above 5 %);
- whether a container CPU limit throttled the server (a warning from 1 % of the periods);
- the nice value of a chunk worker thread, and whether `worker-priority` (below) is on and took effect.

A background thread samples these every 5 seconds from `/proc` and the cgroup files; it needs a minute to say something about the last minute. On a virtual machine a nearly idle main thread already shows 10 to 14 % wait (wake-up latency) but only about 3 ms per second, so the doctor names that as wake-up latency instead of warning about contention. How well the readings agree with `/proc`: [host measurements](docs/measurements/host.md).

</details>

### Worker priority

```yaml
performance:
  worker-priority:
    enabled: false   # true: chunk and I/O worker threads lower their own priority when they start
    nice: 10         # 0 to 19, higher is lower priority
```

Paper raises the main thread's Java priority and lowers its own workers', but HotSpot ignores Java thread priorities on Linux (with the default `-XX:ThreadPriorityPolicy=0`), so there the chunk workers compete with the main thread on equal terms. With `enabled`, each chunk and I/O worker thread lowers its own priority. It stays off until a run under real load on a VPS confirms the bench host's result ([measurements](docs/measurements/host.md#worker-priority-and-the-main-threads-wait-for-a-cpu)).

<details>
<summary><b>How it works</b></summary>

- On Linux each worker gives itself the nice value `nice` through the C library (`setpriority` with its thread id from `gettid`, which needs glibc 2.30 or musl 1.2.2); on Windows and macOS it lowers its Java thread priority instead.
- A priority is never raised: a thread that already runs at a higher nice value keeps it.
- If the call fails, the server logs it once and the workers keep their priority.
- Threads that a worker starts inherit its nice value. The setting applies to threads started after it is read, so restart the server after changing it.
- The call is a restricted method of the Foreign Function & Memory API. The Paperclip jar's manifest carries `Enable-Native-Access: ALL-UNNAMED`, which also covers the server classes Paperclip loads, so `java -jar` prints no warning. Started any other way (for example `java -cp … io.papermc.paperclip.Main`), Java prints a "restricted method" warning the first time; `--enable-native-access=ALL-UNNAMED` silences it.

</details>

### Directional chunk loading

```yaml
performance:
  directional-chunk-loading:
    enabled: true
    look-ahead-ticks: 60
```

It only reorders the queue: which chunks get loaded does not change, only the order.

### Idle memory

```yaml
performance:
  idle-memory:
    enabled: false      # true: give memory back while nobody is online
    after-seconds: 300  # how long the server must be empty first
```

After `after-seconds` without players the server gives heap and native memory back, once per idle period. A heap with `-Xms` equal to `-Xmx` cannot shrink; `/kindling doctor` says so, and `-Xms512M -Xmx2G` lets the idle collection give heap back. The tick spike when players rejoin after the heap shrank was not measured ([measurements](docs/measurements/memory-jvm.md#idle-memory)).

<details>
<summary><b>How it works</b></summary>

- It runs a full garbage collection through the JVM's diagnostic command, so it works with `-XX:+DisableExplicitGC`, with the free-heap ratios lowered to 5 and 10 percent for that one collection.
- Then it trims the native heap on glibc Linux with Java 25 (unless `-XX:TrimNativeHeapInterval` is set), and logs the heap and resident memory before and after.
- Nothing runs while a player is online or a pregeneration is working, and the period starts over when either begins.

</details>

## 📦 Building

Java 25 and Git are required (on Windows also `git config --global core.longpaths true`).

```bash
./gradlew applyAllPatches
./gradlew createPaperclipJar   # leaf-server/build/libs/leaf-paperclip-26.3.local-SNAPSHOT.jar
```

## 📚 Documentation

| To learn | Read |
| --- | --- |
| What changed in each build | [CHANGELOG.md](CHANGELOG.md) |
| Why each change exists, and what it measured | [docs/MEASUREMENTS.md](docs/MEASUREMENTS.md) |
| What is open, and what was dropped | [docs/ROADMAP.md](docs/ROADMAP.md) |
| How Kindling follows Leaf | [docs/UPSTREAM.md](docs/UPSTREAM.md) |
| How to contribute | [CONTRIBUTING.md](CONTRIBUTING.md) |
| How to measure a change | [kindling-bench/README.md](kindling-bench/README.md) |
| Logos, banners and brand assets | [Brand kit](docs/branding/README.md) |

## License and credits

Kindling inherits the licenses of its upstream projects: patches under MIT unless their header says otherwise, binaries under GPL-3.0-only. See [LICENSE.md](LICENSE.md).

Kindling would be nothing without [Leaf](https://github.com/Winds-Studio/Leaf) by Winds Studio and the projects Leaf builds on: [Paper](https://github.com/PaperMC/Paper), [Gale](https://github.com/GaleMC/Gale), [Pufferfish](https://github.com/pufferfish-gg/Pufferfish), [Purpur](https://github.com/PurpurMC/Purpur) and the forks credited in Leaf's README. Please report problems that plain Leaf has as well to Leaf, not here.
