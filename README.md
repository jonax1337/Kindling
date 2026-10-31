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
[Getting started](docs/getting-started.md) ·
[Documentation](docs/README.md) ·
[Measurements](docs/MEASUREMENTS.md) ·
[Changelog](CHANGELOG.md)

</div>

---

Kindling makes the parts that hurt on a cheap VPS cheaper: players outrunning world generation, pregeneration, loading chunks from disk, and memory. Every change has to win a reproducible load test ([`kindling-bench`](kindling-bench/README.md)). Changes that only win by altering gameplay are never defaults.

> [!WARNING]
> Kindling is young and every build is a pre-release. Back up your worlds before switching, as with any server software.

## Against Paper and Leaf

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/media/kindling-vs-leaf-dark.svg">
  <img alt="Bar chart of Kindling's change against plain Leaf per figure: time over the void in the flight −100 %, flight tick time p95 −54 %, join until the full view −46 %, flight tick time average +12 %, standing tick time −11 %, survival bases tick time −9 %, CPU time of the bases run +15 %, CPU time of the flight-and-mobs run +43 %." src="docs/media/kindling-vs-leaf-light.svg" width="760">
</picture>

| Scenario | Paper | Leaf | Kindling |
| --- | ---: | ---: | ---: |
| New chunks per second, 8 players exploring / 5 elytras | 68 / 44 | 65 / 40 | **160 / 144** |
| Time over ungenerated void, 8 players / 5 elytras | 82 / 93 % | 82 / 93 % | **0 / 7.5 %** |
| Join until the full view | 50.2 s | 50.1 s | **27.2 s** |
| Tick time while flying, p95 / average | 280 / 32.8 ms | 130 / **32.4** ms | **60** / 36.3 ms |
| Tick time among 1 200 standing mobs | 51.6 ms | 39.1 ms | **34.6 ms** |
| Tick time in survival bases | 13.2 ms | 11.8 ms | **10.8 ms** |
| Tick time among working villagers | 14.4 ms | 8.5 ms | 8.4 ms |
| Tick time flying over a pregenerated world | 30.2 ms | **25.5 ms** | 27.2 ms |
| CPU time, bases / exploring | 242 / 845 s | **246** / **810 s** | 282 / 1 160 s |

Paper build 223, plain Leaf `66f99f8e` and Kindling, each with its own defaults; 3 runs each on a 4-core, 8-thread i7-7700 under Linux. Bold marks the better of Leaf and Kindling where `compare.py` marks the difference.

Where players reach new terrain, Kindling generates 2.5 to 3.6 times the chunks and the time over void falls to 0 % (7.5 % for fast elytras); the average flight tick rises because the server sends that many more chunks. Without new terrain it ticks bases and mobs faster and villagers the same. It uses more CPU, most of it in the chunk workers. Over a pregenerated world it ticked 7 % slower than Leaf here; with worker demand, on by default, the gap is 5 % at Leaf's CPU time ([details](docs/measurements/host.md#worker-demand)). [Method and all numbers](docs/measurements/vs-leaf.md).

## Features

| Feature | Default |
| --- | --- |
| **Directional chunk loading:** chunks ahead of a moving player load first | on |
| **Chunk workers sized for fast players:** five eighths of the cores, at most five, never fewer than Paper; generation queues capped at 32 per player | on |
| **Worker demand:** every chunk worker runs only while new terrain waits | on |
| **Worker priority:** chunk and I/O workers give way to the main thread (Linux) | on |
| **Compiled world generation:** material rules and terrain density without vanilla's lambda trees and dead branches, with vanilla's results | on |
| **Faster chunk loading:** saved palettes read straight from NBT, without DataFixerUpper | on |
| **Sleeping block entities:** idle hoppers, furnaces, brewing stands, crafters and chests stop ticking, plugin-safe | on |
| **Lithium ports:** redstone wire power, unused mob sensors, rain tick, map names | on |
| **Built-in pregenerator:** `/pregen`, steps back when ticks get slow, resumes after a restart | manual |
| **Copies of unreadable chunks** to `kindling-quarantine/` before they are generated anew | on |
| **Vanilla parity:** Leaf and Gale changes to vanilla behaviour restored ([list](docs/reference/vanilla-differences.md)) | on |
| **`/kindling doctor`:** checks the machine and settings and names the fix | on |
| **`/kindling stress`:** load your own server in a throwaway world and watch MSPT | command |
| **Frontier pregeneration**, **worker governor**, **idle memory return**, **biome zoom shortcut** | opt-in |

What each feature measured: [Measurements](docs/MEASUREMENTS.md). Everything else is Leaf, unchanged: Paper, Gale, Pufferfish and Purpur patches, Leaf's configuration and its plugin compatibility. Kindling reports the brand id `winds-studio:leaf`.

## Quick start

```bash
java -Xms3G -Xmx3G -XX:+UseCompactObjectHeaders -jar kindling-26.3-<build>.jar --nogui
```

Download the jar from [Releases](https://github.com/jonax1337/Kindling/releases). New to Kindling: [Getting started](docs/getting-started.md). Coming from Leaf: [Switch from Leaf](docs/guides/switch-from-leaf.md), because an existing `leaf-global.yml` keeps Leaf's defaults.

## Documentation

| | |
| --- | --- |
| [Getting started](docs/getting-started.md) | First server, first pregeneration |
| [Guides](docs/README.md#guides) | Switch from Leaf, memory and JVM flags, pregeneration, finding lag |
| [Configuration](docs/reference/configuration.md) and [commands](docs/reference/commands.md) | Every setting and command |
| [Vanilla differences](docs/reference/vanilla-differences.md) | What is restored and what still differs |
| [Measurements](docs/MEASUREMENTS.md) | Why each change exists and what it measured |
| [Changelog](CHANGELOG.md) and [roadmap](docs/ROADMAP.md) | What changed, what is next |
| [Contributing](CONTRIBUTING.md) | Building, patches, the bench |

## License and credits

Patches under MIT unless their header says otherwise, binaries under GPL-3.0-only; see [LICENSE.md](LICENSE.md).

Kindling builds on [Leaf](https://github.com/Winds-Studio/Leaf) by Winds Studio and the projects Leaf builds on: [Paper](https://github.com/PaperMC/Paper), [Gale](https://github.com/GaleMC/Gale), [Pufferfish](https://github.com/pufferfish-gg/Pufferfish), [Purpur](https://github.com/PurpurMC/Purpur) and the forks credited in Leaf's README. Please report problems that plain Leaf has as well to Leaf.
