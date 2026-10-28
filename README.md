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
| 🧭 **Directional chunk loading** | Chunks ahead of a moving player are queued before chunks behind them | Five players flying elytras outward in five directions: time spent flying into ungenerated void drops from 9 % to **0 %** at the same CPU (with 5 chunk workers, [details](docs/MEASUREMENTS.md#elytra-test-new-chunks-ahead-of-fast-players)). |
| ⚙️ **Chunk workers sized for fast players** | Five eighths of the cores, at most five, never fewer than Paper (1 → 2 on 4 cores, 2 → 5 on 8, 3 → 5 on 12) | Paper's quarter of the cores lets players fly into the void (24 % of the time with eight fliers on 12 cores); five keep them out of it, more only add load ([details](docs/MEASUREMENTS.md#default-number-of-chunk-workers)). |
| 📦 **Chunk loading without DataFixerUpper** | Block and biome palettes of saved chunks are read straight from NBT instead of through Mojang's codec library | Same result (anything unusual still goes through the codec; check it with `-Dkindling.verifyPaletteDecoder=true`), a smaller share of the chunk workers' time ([details](docs/MEASUREMENTS.md#loading-chunks-from-disk)). |
| 🛟 **Copies of unreadable chunks** | When a stored chunk cannot be decompressed, read as NBT, or upgraded and parsed into a chunk (for example because a newer Minecraft version saved it), its stored bytes and the exception are copied to `<world>/kindling-quarantine/<dimension>/r.<x>.<z>/c.<x>.<z>-<n>.bin` and one `WARN` line names the path | Vanilla and Paper log the error and generate the chunk anew, and the next save overwrites what was stored. Nothing else changes: the chunk is handled as before, each chunk is copied once per run, and nothing is copied once the folder holds 256 MB. Only chunk data (not entities or POI) in the Anvil format is copied, and only when the read or parse throws an exception; chunks the region file reports as missing, for example with a truncated header, are not copied. |
| ⏱️ **Cheaper lagging check** | The tick loop's lagging check computes only the 5-second TPS average it uses | Same value; on an idle server the TPS averaging fell from 13.6 % to 3.4 % of the main thread ([details](docs/MEASUREMENTS.md#the-lagging-check-of-the-tick-loop)). Also proposed to Leaf as [#970](https://github.com/Winds-Studio/Leaf/pull/970). |
| 🧵 **Async defaults** | Leaf's async entity tracker, async pathfinding and async player data saving are on by default | −3 to −13 % tick time under load, same behaviour as vanilla ([details](docs/MEASUREMENTS.md#mixed-load)). |
| 💤 **Sleeping block entities** | Leaf's sleeping block entities (from Lithium) are on by default: idle hoppers, furnaces, brewing stands, crafters and chests stop ticking until something can happen. Kindling makes them act as on Paper where Leaf's port did not: with plugins that hold back or rewrite transfers and fuel, with tuned `spigot.yml` hopper rates and with blocks set without block updates. With `ticks-per.hopper-check` above 1, hoppers stay awake, because a sleeping hopper cannot keep Paper's check interval | −12 to −15 % tick time in survival bases with identical results (items, smelting, brewing, crafting, redstone), −11 % standing among mobs ([details](docs/MEASUREMENTS.md#survival-bases-sleeping-block-entities)). |
| 🧠 **Measured JVM advice** | G1 + `-XX:+UseCompactObjectHeaders`, right-sized heap | −29 % resident memory at the same tick time ([details](docs/MEASUREMENTS.md#jvm-and-heap)). |
| 🏷️ **Own `/version`** | Shows the Kindling build, commit and date, and checks Kindling's GitHub releases for a newer build | Tells at a glance whether the server is up to date, with a download link. |

Everything else is Leaf, unchanged: Paper, Gale, Pufferfish and Purpur patches, Leaf's configuration (`config/leaf-global.yml`), its commands and its plugin compatibility. Swapping Leaf for Kindling needs no config migration. Kindling reports the brand id `winds-studio:leaf`, so plugins that check for Leaf keep working.

## Numbers

Measured with `kindling-bench` against Leaf `4c3d5689` on Java 25, the elytra and pregeneration tests pinned to 8 logical cores as a stand-in for an 8 vCPU VPS. Full method and raw tables: [docs/MEASUREMENTS.md](docs/MEASUREMENTS.md).

**Five elytra players, 90 s outward**

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

**Survival bases, 8 players among 32 bases of hoppers, furnaces, redstone, sorters, minecarts and brewing stands** (against Kindling 26.3-4, 12 cores)

| Variant | Tick time avg / p95 | Items moved, smelted, brewed, crafted |
| --- | ---: | --- |
| Kindling 26.3-4 | 9.3 / 12.2 ms | |
| Kindling with sleeping block entities on by default | **8.2 / 11.8 ms** | identical |

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
    max-main-thread-wait: 0     # Linux: also throttles while the main thread waited for a CPU longer than this share of the last minute (e.g. 0.20); 0 turns it off
    automatic:
      enabled: false      # true: start the radii below on every startup until done
      overworld-radius: 5000   # blocks around the world spawn, 0 for none
      nether-radius: 1000
      end-radius: 3000
```

`/pregen start` centres on the world spawn unless both `<x> <z>` are given, rounds the radius down to whole chunks, cuts it to the world border and says what it started. Progress is kept in `<world>/pregeneration.properties` and belongs to the world's seed: a world regenerated under the same name starts over. `/pregen status` shows why a run is slow or waiting (`throttled: 43 ms average > 40 ms`, `throttled: the main thread waited for a CPU 18 % of the last minute > 10 %`, `paused`, `paused: the disk would run out`) and the free disk space against what the rest of the run still needs (an estimate from the space the disk lost per chunk so far). While a run is working, `pause-when-empty-seconds` does not pause the server. The main thread's wait for a CPU comes from the doctor's host monitor (below); where it has no data (Windows, `host-monitor: false`) only `max-mspt` throttles. Permission: `kindling.command.pregen` (operators by default).

### Doctor (`config/leaf-global.yml`)

`/kindling doctor` (permission `kindling.command.doctor`, operators by default) reads this machine and server and lists what costs performance or memory: processors and container CPU limit, `-Xmx` against the memory (heap plus about 600 MB must fit), the garbage collector, `-XX:+UseCompactObjectHeaders`, the AOT cache, chunk worker threads, the malloc arena limit (`MALLOC_ARENA_MAX` or `GLIBC_TUNABLES`), `-XX:TrimNativeHeapInterval`, transparent huge pages, view and simulation distance against the processors, network compression behind a proxy (a warning when the proxy runs on this machine or its place is unknown, a hint when it runs elsewhere), free disk space of the worlds (a warning below 5 GiB or 10 %), and Kindling's recommended options in `leaf-global.yml` (a fresh file has them on, an older file keeps its values). The output is grouped into `WARN`, `INFO` and `OK`; every `WARN` and `INFO` that has something to change names its fix and says whether the advice rests on a Kindling measurement or is general advice. Anything the system cannot tell (for example the Linux-only values on Windows) is skipped instead of guessed.

On Linux the doctor also answers "is the lag my server or my host?" in a `LIVE` section, in plain sentences over the last minute (with the last 15 minutes in brackets): how long the main thread waited for a CPU (a warning above 10 % of the time it had work to do: other threads or processes on the machine compete with it), how long the chunk workers waited, how much CPU time the host took from this virtual machine (steal, a warning above 5 %) and whether a container CPU limit throttled the server (a warning from 1 % of the periods). It also reads the nice value of a chunk worker thread back and says whether `worker-priority` (below) is on and took effect. A background thread samples these every 5 seconds from `/proc` and the cgroup files; it needs a minute to say something about the last minute.

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

Paper raises the main thread's Java priority and lowers its own workers', but HotSpot ignores Java thread priorities on Linux (with the default `-XX:ThreadPriorityPolicy=0`), so there the chunk workers compete with the main thread on equal terms. With `enabled`, each chunk and I/O worker thread gives itself the nice value `nice` through the C library (`setpriority` with its thread id from `gettid`, which needs glibc 2.30 or musl 1.2.2); on Windows and macOS it lowers its Java thread priority instead. A priority is never raised: a thread that already runs at a higher nice value keeps it. If the call fails, the server logs it once and the workers keep their priority. Threads that a worker starts inherit its nice value. The setting applies to threads started after it is read, so restart the server after changing it. It is off until the bench shows that it helps.

The call is a restricted method of the Foreign Function & Memory API. The Paperclip jar's manifest carries `Enable-Native-Access: ALL-UNNAMED`, which also covers the server classes Paperclip loads, so `java -jar` prints no warning. Started any other way (for example `java -cp … io.papermc.paperclip.Main`), Java prints a "restricted method" warning the first time; `--enable-native-access=ALL-UNNAMED` silences it.

### Directional chunk loading

```yaml
performance:
  directional-chunk-loading:
    enabled: true
    look-ahead-ticks: 60
```

It only reorders the queue: which chunks get loaded does not change, only the order.

### Client-paced chunk sending

```yaml
performance:
  client-paced-chunk-sending:
    enabled: false            # true: pace each player's chunks by the client's answers, as vanilla does
    max-chunks-per-tick: 32   # 1 to 64, the most a fast client gets per tick
```

Paper's chunk system sends every player its chunks at a fixed rate (`chunk-loading-basic.player-max-chunk-send-rate`) and never asks the client how fast it can take them: a slow client collects megabytes in its send buffer, with keep-alive and movement packets queued behind the chunks, and a fast client never gets more than the fixed rate. With `enabled`, the chunks a player gets in one tick go out as one vanilla chunk batch, and the number per tick follows vanilla's own rules: the client answers each batch with the rate it can take (vanilla starts at 9 chunks per tick and allows 0.01 to 64), and the server waits while ten batches are unanswered (one before the client's first answer). Paper's send rate still applies on top if it is above 0 (its default is 75 chunks per second); set it to -1 to let the client's rate decide. A client that does not answer its first batch within 3 seconds (an old, modded or fake client) gets the fixed rate until it answers. Which chunks are sent, in which order, and unloading do not change; plugins that watch packets see vanilla's batch packets around the chunks. It is off until the bench has measured it.

### Idle memory

```yaml
performance:
  idle-memory:
    enabled: false      # true: give memory back while nobody is online
    after-seconds: 300  # how long the server must be empty first
```

After `after-seconds` without players, once per idle period, the server runs a full garbage collection (through the JVM's diagnostic command, so it works with `-XX:+DisableExplicitGC`) with the free-heap ratios lowered to 5 and 10 percent for that one collection, then trims the native heap on glibc Linux with Java 25 (unless `-XX:TrimNativeHeapInterval` is set), and logs the heap and resident memory before and after. Nothing runs while a player is online, and the period starts over when someone joins. A heap with `-Xms` equal to `-Xmx` cannot shrink; `/kindling doctor` says so, and `-Xms512M -Xmx2G` lets the idle collection give heap back.

Measured on the bench PC (WSL2, Java 25.0.5, G1, a small program that peaked at 1.7 GB resident and kept 150 MB live, `-Xmx2G`): with `-Xms2G` the resident memory stayed at 1.68 GB (the trim freed 14 to 25 MB); with `-Xms256M` a plain full collection brought it to 650 MB, and with the lowered ratios to 306 MB. This is a synthetic test, not a server run.

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
