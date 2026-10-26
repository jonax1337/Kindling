# Kindling

**Kindling** is a [Leaf](https://github.com/Winds-Studio/Leaf) fork for small and mid-sized survival servers on shared hardware. It keeps vanilla behaviour and the full vanilla world, and makes the parts that hurt on a cheap VPS cheaper: fast players outrunning world generation, pregeneration, and memory.

Every change here had to win against a reproducible load test (`kindling-bench`, fake players flying, landing between mobs, idling). Changes that only win by altering gameplay (mob spawn throttling, distant entity tick skipping) are not defaults.

> [!WARNING]
> Kindling is young and has one production user so far. Take a backup of your worlds before switching, as with any server software.

## What Kindling adds on top of Leaf

| | What it does | Why |
| --- | --- | --- |
| **Built-in pregenerator** | `/pregen start <world> <radius>`, `pause`, `resume`, `cancel`, `status`; optional automatic start per dimension | No Chunky needed. Runs at the chunk system's lowest priority, so chunks players need always come first; throttles itself by tick time; resumes after a restart. ~140 chunks/s on 8 cores at 6.5 ms per tick. |
| **Directional chunk loading** | Chunks ahead of a moving player are queued before chunks behind them | Five players flying elytras outward in five directions: time spent flying into ungenerated void drops from 9 % to **0 %** at the same CPU (with 5 chunk workers). |
| **Async defaults** | Leaf's async entity tracker, async pathfinding and async player data saving are on by default | −5 to −13 % tick time under load, same behaviour as vanilla. |
| **Measured JVM advice** | G1 + `-XX:+UseCompactObjectHeaders`, right-sized heap | −29 % resident memory at the same tick time ([details](docs/MEASUREMENTS.md#jvm-and-heap)). |

Everything else is Leaf, unchanged: Paper, Gale, Pufferfish and Purpur patches, Leaf's configuration (`config/leaf-global.yml`), its commands and its plugin compatibility. Swapping Leaf for Kindling needs no config migration. Kindling reports the brand id `winds-studio:leaf`, so plugins that check for Leaf keep working.

## Numbers

Measured with `kindling-bench` against Leaf `4c3d5689` on Java 25, the elytra and pregeneration tests pinned to 8 logical cores as a stand-in for an 8 vCPU VPS. Full method and raw tables: [docs/MEASUREMENTS.md](docs/MEASUREMENTS.md).

| Five elytra players, 90 s outward | Time in the void | Tick time avg / p95 |
| --- | --- | --- |
| Leaf, defaults (2 chunk workers) | 92 % | 19.8 / 95 ms |
| Kindling, 5 chunk workers + directional loading | **0 %** | 19.2 / 35 ms |
| Kindling, same, while the pregenerator runs | **0 %** | 19.5 / 37 ms |

## Getting started

1. Download the newest `kindling-26.3-<build>.jar` from [Releases](https://github.com/jonax1337/KindlingMC/releases).
2. Use it like a Leaf or Paper jar: `java -Xms2G -Xmx2G -XX:+UseCompactObjectHeaders -jar kindling-26.3-<build>.jar --nogui`.
3. For fast players (elytras, boats on ice), raise the chunk workers in `config/paper-global.yml`: `chunk-system.worker-threads: 5` on 8 cores. Paper's default is a quarter of the cores, which cannot keep up with even one elytra.
4. Pregenerate the area your players will use: `/pregen start world 5000`. Watch it with `/pregen status`.

### Pregeneration settings (`config/leaf-global.yml`)

```yaml
performance:
  pregeneration:
    max-mspt: 40.0        # slows down whenever the average tick takes longer
    max-parallel: 32      # upper bound for chunks in flight
    automatic:
      enabled: false      # true: start the radii below on every startup until done
      overworld-radius: 5000
      nether-radius: 1000
      end-radius: 3000
```

Progress is kept in `<world>/pregeneration.properties`. Permission: `kindling.command.pregen` (operators by default).

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

How Kindling follows Leaf: [docs/UPSTREAM.md](docs/UPSTREAM.md). How to contribute and how to measure a change: [CONTRIBUTING.md](CONTRIBUTING.md) and [kindling-bench/README.md](kindling-bench/README.md).

## License and credits

Kindling inherits the licenses of its upstream projects: patches under MIT unless their header says otherwise, binaries under GPL-3.0-only. See [LICENSE.md](LICENSE.md).

Kindling would be nothing without [Leaf](https://github.com/Winds-Studio/Leaf) by Winds Studio and the projects Leaf builds on: [Paper](https://github.com/PaperMC/Paper), [Gale](https://github.com/GaleMC/Gale), [Pufferfish](https://github.com/pufferfish-gg/Pufferfish), [Purpur](https://github.com/PurpurMC/Purpur) and the forks credited in Leaf's README. Please report problems that plain Leaf has as well to Leaf, not here.
