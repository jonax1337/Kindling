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
| ⏱️ **Cheaper lagging check** | The tick loop's lagging check computes only the 5-second TPS average it uses | Same value; on an idle server the TPS averaging fell from 13.6 % to 3.4 % of the main thread ([details](docs/MEASUREMENTS.md#the-lagging-check-of-the-tick-loop)). Also proposed to Leaf as [#970](https://github.com/Winds-Studio/Leaf/pull/970). |
| 🧵 **Async defaults** | Leaf's async entity tracker, async pathfinding and async player data saving are on by default | −3 to −13 % tick time under load, same behaviour as vanilla ([details](docs/MEASUREMENTS.md#mixed-load)). |
| 💤 **Sleeping block entities** | Leaf's sleeping block entities (from Lithium) are on by default: idle hoppers, furnaces, brewing stands, crafters and chests stop ticking until something can happen. Kindling keeps a hopper that a plugin holds back asking that plugin, as on Paper | −12 to −15 % tick time in survival bases with identical results (items, smelting, brewing, crafting, redstone), −11 % standing among mobs ([details](docs/MEASUREMENTS.md#survival-bases-sleeping-block-entities)). |
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
    max-mspt: 40.0        # slows down whenever the average tick takes longer
    max-parallel: 32      # upper bound for chunks in flight
    automatic:
      enabled: false      # true: start the radii below on every startup until done
      overworld-radius: 5000   # blocks around the world spawn, 0 for none
      nether-radius: 1000
      end-radius: 3000
```

`/pregen start` centres on the world spawn unless `<x> <z>` are given. Progress is kept in `<world>/pregeneration.properties`. Permission: `kindling.command.pregen` (operators by default).

### Doctor (`config/leaf-global.yml`)

`/kindling doctor` (permission `kindling.command.doctor`, operators by default) reads this machine and server and lists what costs performance or memory: processors and container CPU limit, `-Xmx` against the memory, the garbage collector, `-XX:+UseCompactObjectHeaders`, the AOT cache, chunk worker threads, `MALLOC_ARENA_MAX`, `-XX:TrimNativeHeapInterval`, transparent huge pages, view and simulation distance against the processors, and network compression behind a proxy. The output is grouped into `WARN`, `INFO` and `OK`; every `WARN` and `INFO` names its fix and says whether the advice rests on a Kindling measurement or is general advice. Anything the system cannot tell (for example the Linux-only values on Windows) is skipped instead of guessed.

```yaml
performance:
  doctor:
    startup-check: true   # log the warnings once the server has started
```

With `startup-check` the server logs each `WARN` of that report (one line with its fix) on its first tick. Hints and OK lines only show in the command.

### Directional chunk loading

```yaml
performance:
  directional-chunk-loading:
    enabled: true
    look-ahead-ticks: 60
```

It only reorders the queue: which chunks get loaded does not change, only the order.

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
