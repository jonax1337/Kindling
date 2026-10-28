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
[Features](#what-kindling-adds-on-top-of-leaf) ·
[Numbers](#numbers) ·
[Getting started](#getting-started) ·
[Configuration](#configuration) ·
[Building](#building) ·
[Measurements](docs/MEASUREMENTS.md)

</div>

---

Kindling makes the parts that hurt on a cheap VPS cheaper: fast players outrunning world generation, pregeneration, loading chunks from disk, and memory.

Every change had to win against a reproducible load test ([`kindling-bench`](kindling-bench/README.md): fake players flying, landing between mobs, idling, standing in survival bases full of hoppers and redstone). Changes that only win by altering gameplay (mob spawn throttling, distant entity tick skipping) are not defaults.

> [!WARNING]
> Kindling is young and has one production user so far; every build is published as a pre-release. Take a backup of your worlds before switching, as with any server software.

## What Kindling adds on top of Leaf

| | What it does | Why |
| --- | --- | --- |
| 🗺️ **Built-in pregenerator** | `/pregen start <world> <radius> [<x> <z>]`, `pause`, `resume`, `cancel`, `status`; optional automatic start per dimension | No Chunky needed. Runs at the chunk system's lowest priority, so chunks players need always come first; throttles itself by tick time; resumes after a restart. Works in 16 × 16 chunk tiles and keeps the half-generated neighbours in memory instead of saving and re-reading them: **+59 % chunks per minute, −41 % CPU per chunk**, 5 ms per tick while it runs ([details](docs/MEASUREMENTS.md#pregeneration)). |
| 🧭 **Directional chunk loading** | Chunks ahead of a moving player are queued before chunks behind them | Five players flying elytras outward in five directions for 90 s: time spent over ungenerated void drops from 9 % to **0 %** at the same CPU (with 5 chunk workers; 12-thread Windows machine pinned to 8 threads, two runs each, measured on one line straight ahead of each player). It is not a guarantee: on a 4-core, 8-thread Linux host the same kind of flight at 1.7 blocks/tick spent about half of the time over the void, because chunk generation there manages about 86 chunks/s against the roughly 225 needed ([details](docs/MEASUREMENTS.md#elytra-test-new-chunks-ahead-of-fast-players)). |
| ⚙️ **Chunk workers sized for fast players** | Five eighths of the cores, at most five, never fewer than Paper (1 → 2 on 4 cores, 2 → 5 on 8, 3 → 5 on 12) | Paper's quarter of the cores lets players fly into the void (24 % of the time with eight fliers on 12 cores); five keep them out of it, more only add load ([details](docs/MEASUREMENTS.md#default-number-of-chunk-workers)). |
| 📦 **Chunk loading without DataFixerUpper** | Block and biome palettes of saved chunks are read straight from NBT instead of through Mojang's codec library | Same result (anything unusual still goes through the codec; check it with `-Dkindling.verifyPaletteDecoder=true`), a smaller share of the chunk workers' time ([details](docs/MEASUREMENTS.md#loading-chunks-from-disk)). |
| 🛟 **Copies of unreadable chunks** | When a stored chunk cannot be decompressed, read as NBT, or upgraded and parsed into a chunk (for example damaged compressed bytes, or block states without a palette), its stored bytes and the exception are copied to `<world>/kindling-quarantine/<dimension>/r.<x>.<z>/c.<x>.<z>-<n>.bin` and one `WARN` line names the path | Vanilla and Paper log the error and generate the chunk anew, and the next save overwrites what was stored. Nothing else changes: the chunk is handled as before, each chunk is copied once per run, and nothing is copied once the folder holds 256 MB. Only chunk data (not entities or POI) in the Anvil format is copied, and only when the read or parse throws an exception; chunks the region file reports as missing, for example with a truncated header, are not copied. Checked on the bench host with damaged region files ([details](docs/MEASUREMENTS.md#copies-of-unreadable-chunks)). A chunk saved by a newer Minecraft version stops the server before this runs (Paper's own guard), and a block that does not exist loads as the default block without an error, so neither is copied. |
| ⏱️ **Cheaper lagging check** | The tick loop's lagging check computes only the 5-second TPS average it uses | Same value; on an idle server the TPS averaging fell from 13.6 % to 3.4 % of the main thread ([details](docs/MEASUREMENTS.md#the-lagging-check-of-the-tick-loop)). Also proposed to Leaf as [#970](https://github.com/Winds-Studio/Leaf/pull/970). |
| 🧵 **Async defaults** | Leaf's async entity tracker, async pathfinding, async player data saving and async mob spawning are on by default. Plain Leaf lets them differ from vanilla in places; Kindling fixed those: a villager takes a bed or job site only if it really got it, partial paths and the brain's path are kept, mob costs are read on the main thread; a pending player save is never discarded, `save-all flush` and the stop wait for the writes, and a failed encode writes nothing; a failed async mob count no longer stops spawning (Leaf pull request #937); the tracker's sync flags are not lost | Their speed against plain Leaf is not measured on its own: the [comparison below](#numbers) also changes the chunk defaults, and plain Leaf was still generating while standing (an older two-run figure of −13 % standing does not meet the three-run rule). The fixes cost nothing measurable (standing 37.0 ±0.7 against 36.8 ±0.5 ms, 3 runs each). Villager parity: on release 26.3-11 async pathfinding gave 5 and 3 shared beds, after the fix 0 and equal claims. The async saving is covered by unit tests only, not yet by a server test ([details](docs/MEASUREMENTS.md#async-pathfinding-tracker-and-saving-after-the-fixes)). |
| 💤 **Sleeping block entities** | Leaf's sleeping block entities (from Lithium) are on by default: idle hoppers, furnaces, brewing stands, crafters and chests stop ticking until something can happen. Kindling makes them act as on Paper where Leaf's port did not: plugins that rewrite a transfer (`InventoryMoveItemEvent#setItem` duplicated 652 items in the parity test), tuned `spigot.yml` hopper rates (906 instead of 2 751 items on a hopper chain), blocks set without block updates, stands and furnaces a plugin holds back, and Paper's cooldown after a failed transfer. A hopper takes Lithium's shortcut only when no plugin listens to `InventoryMoveItemEvent`; with `ticks-per.hopper-check` above 1, hoppers stay awake, because a sleeping hopper cannot keep Paper's check interval | −8 % average tick time in survival bases with identical results (items, smelting, brewing, crafting, redstone): 12.3 ±0.4 → 11.3 ±0.1 ms, 5 runs each on the current build, p = 0.001. That replaces the −12 to −15 % of two runs on older builds. No gain is claimed standing among mobs (an earlier −11 % did not meet the rule). On/off parity holds in every counter, and the fixed build is not slower than the release ([details](docs/MEASUREMENTS.md#sleeping-block-entities-after-the-fixes)). |
| 🔌 **Lithium ports** | The redstone wire power calculation, sensors a mob does not use (baby sensors on adults, the goat's item sensor), an early exit in the rain tick and a lazier framed map name; all with vanilla's result | Bases scenario, 3 runs each: tick time avg 12.0 ±0.2 → **11.2 ±0.1 ms** (p = 0.016), p95 17.0 → **15.3 ms**, p99 24.4 → **19.8 ms**, all 15 counters identical. The A/B does not separate the four ports; no scenario covers the goat sensor, the rain early exit or the maps. Against release 26.3-11 the gain does not show: the current build runs the bases at the release's 11.7 ms, and what offsets it was not measured ([details](docs/MEASUREMENTS.md#lithium-ports)). |
| 🐑 **Cheaper natural spawning** | Entity types without a spawn cost in any biome skip the biome lookup; a one-entry memo avoids resolving the same position up to three times per attempt | Vanilla-identical. No measurable tick time change (3 runs each) and equal spawn counts per kind; the one significant row (main thread CPU in the flight, −3.6 %) is not claimed ([details](docs/MEASUREMENTS.md#natural-spawning-skipped-spawn-cost-lookups)). |
| 🧠 **Measured JVM advice** | G1 + `-XX:+UseCompactObjectHeaders`, right-sized heap | −29 % resident memory at the same tick time ([details](docs/MEASUREMENTS.md#jvm-and-heap)). |
| 🏷️ **Own `/version`** | Shows the Kindling build, commit and date, and checks Kindling's GitHub releases for a newer build | Tells at a glance whether the server is up to date, with a download link. |

Everything else is Leaf, unchanged: Paper, Gale, Pufferfish and Purpur patches, Leaf's configuration (`config/leaf-global.yml`), its commands and its plugin compatibility. Swapping Leaf for Kindling needs no config migration. Kindling reports the brand id `winds-studio:leaf`, so plugins that check for Leaf keep working.

## Numbers

**Kindling against plain Leaf on the same base.** Leaf `66f99f8e` against the current build, each with its own defaults, on a 4-core, 8-thread Intel i7-7700 under Linux with Java 25. Measured 2026-10-28, 3 runs each, interleaved. Bold marks the better value where the difference is significant by the bench's rule ([details](docs/MEASUREMENTS.md#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf)).

| Load | Figure | Leaf | Kindling |
| --- | --- | ---: | ---: |
| 8 players fly outward for 90 s at 1 block/tick, then stand among 1 200 mobs | Time over ungenerated void in the flight | 85.8 % | **2.0 %** |
| | Tick time in the flight, avg / p95 | 37.7 / 174.9 ms | **27.8 / 65.2 ms** |
| | New chunks per second | 51 | **98** |
| | Join until the full view | 57.4 s | **39.9 s** |
| | Tick time standing, avg | 44.4 ms | **35.4 ms** |
| | CPU time of the run | **927 s** | 1 416 s |
| 8 players among 32 survival bases | Tick time avg / p95 | 12.5 / 17.8 ms | 11.7 / 17.0 ms (not significant) |
| | CPU time of the run | **298 s** | 376 s |

What the table does and does not say:

- **The flight is where Kindling wins.** With Paper's two chunk workers and its automatic generation limit, plain Leaf's fliers outran world generation after 10 to 20 s and got almost no chunks for the rest of the flight. Kindling's five workers, the generation cap and directional loading kept them over generated land. The table shows the three together.
- **The standing figure flatters Kindling.** Plain Leaf was still generating the players' surroundings while they stood, so the −20 % is not the gain of the async defaults alone.
- **The bases are a draw.** Kindling is not faster there, although sleeping block entities on their own save 8 % of the bases' tick time within Kindling (12.3 → 11.3 ms, 5 runs each).
- **Kindling uses more CPU time.** In the flight that is mostly work plain Leaf did not get done (twice the chunks, 16 times the chunk packets). In the bases it buys no tick time. The candidates are the five chunk workers instead of two and the async threads; they were not measured apart.
- **Release 26.3-11 measured the same as the current build** in both loads.

**Older tables (2026-10-26/27).** Measured with `kindling-bench` against Leaf `4c3d5689` on Java 25, the elytra and pregeneration tests pinned to 8 logical cores as a stand-in for an 8 vCPU VPS, two runs each (the means are shown). They predate the bench's rule of three runs and a significance test, and only the large gaps (void time, chunks per minute, CPU per chunk) are far beyond the gap between two runs; a difference of a few percent of tick time is not a finding under it. Everything measured since 2026-10-28 (three or more runs, `compare.py` marks real differences) is in [docs/MEASUREMENTS.md](docs/MEASUREMENTS.md), together with the method and the raw tables.

**Five elytra players, 90 s outward** (12-thread Windows machine pinned to 8 threads; the void is counted on one line straight ahead; on a 4-core, 8-thread Linux host the same kind of flight spent about half of the time in the void, see above)

| Variant | Time in the void | Tick time avg / p95 |
| --- | ---: | ---: |
| Leaf, defaults (2 chunk workers) | 92 % | 19.8 / 95 ms |
| Kindling, 5 chunk workers + directional loading | **0 %** | 19.2 / 35 ms |
| Kindling, same, while the pregenerator runs | **0 %** | 19.5 / 37 ms |

**Pregenerator alone, 2.5 minutes**

| Variant | Chunks generated | Tick time avg / p95 | CPU per chunk | GC time |
| --- | ---: | ---: | ---: | ---: |
| Kindling 26.3-1 (ring by ring) | 19 040 | 7.2 / 13.8 ms | 47 ms | 8.0 s |
| Kindling since 26.3-2 (tiles, palette decoder) | **30 315** | **5.1 / 7.8 ms** | **28 ms** | **1.9 s** |

## Getting started

1. Download the newest `kindling-26.3-<build>.jar` from [Releases](https://github.com/jonax1337/Kindling/releases).
2. Use it like a Leaf or Paper jar:
   ```bash
   java -Xms2G -Xmx2G -XX:+UseCompactObjectHeaders -jar kindling-26.3-<build>.jar --nogui
   ```
3. Leave `chunk-system.worker-threads` in `config/paper-global.yml` at `-1`: Kindling then picks the measured default (5 on 8 cores). If several servers share the machine and generate at the same time, lower it per server.
4. Pregenerate the area your players will use: `/pregen start world 5000`. Watch it with `/pregen status`.

> [!TIP]
> Kindling's defaults only apply when Leaf writes `config/leaf-global.yml` fresh. Coming from Leaf with an existing file, set `async.async-entity-tracker.enabled`, `async.async-pathfinding.enabled`, `async.async-playerdata-save.enabled` and `performance.sleeping-block-entity` to `true` yourself.

## Configuration

### Pregeneration (`config/leaf-global.yml`)

```yaml
performance:
  pregeneration:
    max-mspt: 40.0        # while the average tick takes longer: halves its chunks in flight, starts none
    max-parallel: 32      # upper bound for chunks in flight
    min-free-disk-gb: 2   # stops by itself when finishing would leave less free on the world's disk
    max-main-thread-wait: 0     # Linux: also throttles while the main thread waited for a CPU longer than this share of the last minute (e.g. 0.20); 0 (default) turns it off. 0.10 was measured and is not the default, see below
    automatic:
      enabled: false      # true: start the radii below on every startup until done
      overworld-radius: 5000   # blocks around the world spawn, 0 for none
      nether-radius: 1000
      end-radius: 3000
```

`/pregen start` centres on the world spawn unless both `<x> <z>` are given, rounds the radius down to whole chunks, cuts it to the world border and says what it started. Progress is kept in `<world>/pregeneration.properties` and belongs to the world's seed: a world regenerated under the same name starts over. `/pregen status` shows why a run is slow or waiting (`throttled: 43 ms average > 40 ms`, `throttled: the main thread waited for a CPU 18 % of the last minute > 10 %`, `paused`, `paused: the disk would run out`) and the free disk space against what the rest of the run still needs (an estimate from the space the disk lost per chunk so far). While a run is working, `pause-when-empty-seconds` does not pause the server. The main thread's wait for a CPU comes from the doctor's host monitor (below); where it has no data (Windows, `host-monitor: false`) only `max-mspt` throttles. A run that throws pauses itself and the server goes on; a world that is unloaded and loaded again resumes; the state file is written on a thread of its own and, at a clean stop, with the exact index. Permission: `kindling.command.pregen` (operators by default).

**Why the wait throttle is off.** Measured with limit 0.10: pregeneration alone fell from 140 to 65 chunks/s on the bench host (−55 %, 3 runs each), and with five players flying beside it the pregenerator made 2 to 3 chunks of progress in 90 s while the lead halved (3.0 → 1.6 chunks); the tick time p95 did improve (65.9 → 48.1 ms). On the Cubyx VPS the main thread of a nearly idle test server already waited 11 to 14 % of its time, so 0.10 throttled at baseline there. A value such as 0.20 was not tested. The pregenerator's speed itself depends on the machine: the bench host managed about 140 chunks/s with 5 workers, the four VPS runs (3 workers, the throttle at 0.10 active) made 4.0 to 16.2 chunks/s ([details](docs/MEASUREMENTS.md#worker-priority-and-the-main-threads-wait-for-a-cpu)).

### Doctor (`config/leaf-global.yml`)

`/kindling doctor` (permission `kindling.command.doctor`, operators by default) reads this machine and server and lists what costs performance or memory: processors and container CPU limit, `-Xmx` against the memory (heap plus about 600 MB must fit), the garbage collector, `-XX:+UseCompactObjectHeaders`, the AOT cache, chunk worker threads, the malloc arena limit (`MALLOC_ARENA_MAX` or `GLIBC_TUNABLES`), `-XX:TrimNativeHeapInterval`, transparent huge pages, view and simulation distance against the processors, network compression behind a proxy (a warning when the proxy runs on this machine or its place is unknown, a hint when it runs elsewhere), free disk space of the worlds (a warning below 5 GiB or 10 %), and Kindling's recommended options in `leaf-global.yml` (a fresh file has them on, an older file keeps its values). The output is grouped into `WARN`, `INFO` and `OK`; every `WARN` and `INFO` that has something to change names its fix and says whether the advice rests on a Kindling measurement or is general advice. Anything the system cannot tell (for example the Linux-only values on Windows) is skipped instead of guessed.

On Linux the doctor also answers "is the lag my server or my host?" in a `LIVE` section, in plain sentences over the last minute (with the last 15 minutes in brackets): how long the main thread waited for a CPU (a warning above 10 % of the time it had work to do and at least 20 ms per second: other threads or processes on the machine compete with it), how long the chunk workers waited, how much CPU time the host took from this virtual machine (steal, a warning above 5 %) and whether a container CPU limit throttled the server (a warning from 1 % of the periods). It also reads the nice value of a chunk worker thread back and says whether `worker-priority` (below) is on and took effect. A background thread samples these every 5 seconds from `/proc` and the cgroup files; it needs a minute to say something about the last minute. The readings matched `/proc` read from outside on the bench host within about half a percentage point; on the Cubyx VPS only an idle test server was compared, over different windows. On a virtual machine a nearly idle main thread already shows 10 to 14 % wait (wake-up latency, measured on the VPS with nobody online) but only about 3 ms per second, so the doctor names that as wake-up latency instead of warning about contention.

```yaml
performance:
  doctor:
    startup-check: true   # log the warnings once the server has started
    host-monitor: true    # Linux only: sample the host every 5 seconds for the LIVE section
```

With `startup-check` the server logs each `WARN` of that report (one line with its fix) on its first tick. Hints and OK lines only show in the command.

### Worker priority (`config/leaf-global.yml`)

```yaml
performance:
  worker-priority:
    enabled: false   # true: chunk and I/O worker threads lower their own priority when they start
    nice: 10         # 0 to 19, higher is lower priority
```

Paper raises the main thread's Java priority and lowers its own workers', but HotSpot ignores Java thread priorities on Linux (with the default `-XX:ThreadPriorityPolicy=0`), so there the chunk workers compete with the main thread on equal terms. With `enabled`, each chunk and I/O worker thread gives itself the nice value `nice` through the C library (`setpriority` with its thread id from `gettid`, which needs glibc 2.30 or musl 1.2.2); on Windows and macOS it lowers its Java thread priority instead. A priority is never raised: a thread that already runs at a higher nice value keeps it. If the call fails, the server logs it once and the workers keep their priority. Threads that a worker starts inherit its nice value. The setting applies to threads started after it is read, so restart the server after changing it. Measured on the bench host (Linux, 3 runs each): the main thread's wait roughly halves in an elytra flight, with pregeneration alone, with pregeneration beside a flight and in the mixed load (p <= 0.012), pregeneration speed and chunk lead do not change beyond the run-to-run gap, and the mixed flight tick time improves (27.3 → 24.8 ms avg, 64.4 → 57.6 ms p95); the mixed load also showed a rise in void time (3.1 → 6.3 %, p = 0.10, not significant), which can flatter the tick time. On a VPS that was 85 to 90 % idle it made no measurable difference. It stays off until a run under real load on the VPS confirms it ([details](docs/MEASUREMENTS.md#worker-priority-and-the-main-threads-wait-for-a-cpu)).

The call is a restricted method of the Foreign Function & Memory API. The Paperclip jar's manifest carries `Enable-Native-Access: ALL-UNNAMED`, which also covers the server classes Paperclip loads, so `java -jar` prints no warning. Started any other way (for example `java -cp … io.papermc.paperclip.Main`), Java prints a "restricted method" warning the first time; `--enable-native-access=ALL-UNNAMED` silences it.

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

After `after-seconds` without players, once per idle period, the server runs a full garbage collection (through the JVM's diagnostic command, so it works with `-XX:+DisableExplicitGC`) with the free-heap ratios lowered to 5 and 10 percent for that one collection, then trims the native heap on glibc Linux with Java 25 (unless `-XX:TrimNativeHeapInterval` is set), and logs the heap and resident memory before and after. Nothing runs while a player is online or a pregeneration is working, and the period starts over when either begins. A heap with `-Xms` equal to `-Xmx` cannot shrink; `/kindling doctor` says so, and `-Xms512M -Xmx2G` lets the idle collection give heap back.

Measured on the bench PC (WSL2, Java 25.0.5, G1, a small program that peaked at 1.7 GB resident and kept 150 MB live, `-Xmx2G`): with `-Xms2G` the resident memory stayed at 1.68 GB (the trim freed 14 to 25 MB); with `-Xms256M` a plain full collection brought it to 650 MB, and with the lowered ratios to 306 MB. A server run followed (bench host, `-Xms512M -Xmx2G`, 4 fake players, 3 runs each): 70 s after the last player left, resident memory was 1 032 ±16 MiB with the setting on and 2 492 ±52 MiB off (about 1.45 GiB less), the committed heap fell from 1.8 GiB to 512 MiB, the collection paused 204 ms (one run), and tick times under load did not change (p = 0.96). The tick spike when players rejoin after the heap shrank was not measured ([details](docs/MEASUREMENTS.md#idle-memory)).

## Building

Java 25 and Git are required (on Windows also `git config --global core.longpaths true`).

```bash
./gradlew applyAllPatches
./gradlew createPaperclipJar   # leaf-server/build/libs/leaf-paperclip-26.3.local-SNAPSHOT.jar
```

| To learn | Read |
| --- | --- |
| Why each change exists, and what was left out | [docs/MEASUREMENTS.md](docs/MEASUREMENTS.md) |
| How Kindling follows Leaf | [docs/UPSTREAM.md](docs/UPSTREAM.md) |
| How to contribute | [CONTRIBUTING.md](CONTRIBUTING.md) |
| How to measure a change | [kindling-bench/README.md](kindling-bench/README.md) |
| Logos, banners and brand assets | [Brand kit](docs/branding/README.md) |

## License and credits

Kindling inherits the licenses of its upstream projects: patches under MIT unless their header says otherwise, binaries under GPL-3.0-only. See [LICENSE.md](LICENSE.md).

Kindling would be nothing without [Leaf](https://github.com/Winds-Studio/Leaf) by Winds Studio and the projects Leaf builds on: [Paper](https://github.com/PaperMC/Paper), [Gale](https://github.com/GaleMC/Gale), [Pufferfish](https://github.com/pufferfish-gg/Pufferfish), [Purpur](https://github.com/PurpurMC/Purpur) and the forks credited in Leaf's README. Please report problems that plain Leaf has as well to Leaf, not here.
