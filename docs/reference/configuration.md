# Configuration

Kindling's performance settings live under `performance:` in `config/leaf-global.yml`, except [`network.preview-chunks`](#networkpreview-chunks). Every other key there is Leaf's and works as in Leaf. Changes take effect after a restart unless a setting says otherwise.

Why each performance default was chosen, and what it measured: [MEASUREMENTS.md](../MEASUREMENTS.md).

## Fresh-server branding

When the selected properties file does not exist, Kindling initializes its `motd` to `§6Kindling§r §8|§r §aA Minecraft Server` and copies the bundled [server icon](../branding/pixel-leaf/server-icon.png) to `server-icon.png` in the working directory. The MOTD displays a gold Kindling name, a dark gray separator and green server text. The normal settings loader fills in the remaining properties. This also applies to `--initSettings` and a custom `--config` path.

An existing properties file, including an empty one, disables this initialization. An existing icon is never replaced. Switching from Leaf does not change the MOTD or install an icon. Edit `motd` in the properties file or replace or remove `server-icon.png` as usual; restarting does not restore the branding while the properties file exists. Initialization failures are logged and do not prevent the normal settings loader from running.

## Overview

| Key | Default | Platform |
| --- | --- | --- |
| [`directional-chunk-loading`](#directional-chunk-loading) | on | all |
| [`compiled-material-rules`](#compiled-material-rules) | on | all |
| [`compiled-density`](#compiled-density) | on | all |
| [`worker-demand`](#worker-demand) | on | all |
| [`worker-priority`](#worker-priority) | on | Linux (lower Java priority elsewhere) |
| [`pregeneration`](#pregeneration) | manual runs only | all |
| [`pregeneration.frontier`](#pregenerationfrontier) | off | all |
| [`doctor`](#doctor) | on | all (live readings Linux only) |
| [`worker-governor`](#worker-governor) | off | Linux |
| [`idle-memory`](#idle-memory) | off | all (native trim glibc Linux only) |
| [`biome-zoom-shortcut`](#biome-zoom-shortcut) | off | all |
| [`minecart-push-limit`](#minecart-push-limit) | off | all |
| [`network.preview-chunks`](#networkpreview-chunks) | off | all |

Paper's `chunk-system.worker-threads` in `config/paper-global.yml` keeps its meaning. At `-1` (the default) Kindling picks five eighths of the processors, at most five and never fewer than Paper would (5 on 8 cores).

Paper's automatic `player-max-concurrent-chunk-generates` (0, in `config/paper-global.yml`) is capped at 32 per player, so the directional order can still re-sort the queue. An explicit value is used as it is; a negative value is unlimited.

Unreadable stored chunks are copied to `<world>/kindling-quarantine/` before they are generated anew, up to 256 MiB per folder. `-Dkindling.verifyPaletteDecoder=true` decodes every section both ways and logs differences.

## directional-chunk-loading

```yaml
performance:
  directional-chunk-loading:
    enabled: true
    look-ahead-ticks: 60
```

| Key | Default | Values | Effect |
| --- | --- | --- | --- |
| `enabled` | `true` | boolean | Loads the chunks ahead of a moving player first. |
| `look-ahead-ticks` | `60` | 0 or more | How far ahead, in ticks of the player's movement, the order looks. |

Only the order of the queue changes. Which chunks are loaded stays the same.

## compiled-material-rules

```yaml
performance:
  compiled-material-rules:
    enabled: true
```

| Key | Default | Effect |
| --- | --- | --- |
| `enabled` | `true` | Picks surface, ore vein and underground blocks through a class written once per rule tree instead of vanilla's tree of lambdas. `false` uses vanilla's evaluation. |

The class is written when the first chunk of a world is generated. A rule tree that cannot be compiled, or a method too large for the JIT, falls back to vanilla's evaluation with a warning. The system property `-Dkindling.verifyMaterialRules=true` evaluates every block both ways, logs differences and keeps vanilla's result. It is slow and meant for checking.

## compiled-density

```yaml
performance:
  compiled-density:
    enabled: true
```

| Key | Default | Effect |
| --- | --- | --- |
| `enabled` | `true` | Samples the terrain density for each cell corner without the branches that corner cannot take. `false` samples vanilla's full tree. |

Applies to worlds loaded after the change. The system property `-Dkindling.verifyDensity=true` compares every call with vanilla's sampler and keeps vanilla's result.

## worker-demand

```yaml
performance:
  worker-demand:
    enabled: true
    quiet-seconds: 10
```

| Key | Default | Values | Effect |
| --- | --- | --- | --- |
| `enabled` | `true` | boolean | Runs every chunk worker only while new terrain waits. `false` keeps every worker running. |
| `quiet-seconds` | `10` | 1 to 3600 | How long fewer than 8 generation steps must wait before Paper's worker count runs again. |

Every second the server reads how many generation steps waited for a worker on average. Above 16, every worker runs (`chunk-system.worker-threads` or Kindling's default). After `quiet-seconds` below 8, the count falls back to Paper's default for the machine, a quarter of the processors. Loading saved chunks does not count, and the few new chunks at the view edge of a pregenerated world stay below 16, so such a world runs with Paper's count. Has no effect while [`worker-governor`](#worker-governor) is on.

## worker-priority

```yaml
performance:
  worker-priority:
    enabled: true
    nice: 10
```

| Key | Default | Values | Effect |
| --- | --- | --- | --- |
| `enabled` | `true` | boolean | Chunk and I/O worker threads lower their own OS priority, so they give way to the main thread. |
| `nice` | `10` | 0 to 19 | The nice value on Linux. Higher is lower priority. |

- HotSpot ignores Java thread priorities on Linux, so without this the workers compete with the main thread on equal terms.
- On Linux each worker calls `setpriority` with its thread id from `gettid` (glibc 2.30 or musl 1.2.2 and newer). On Windows and macOS it lowers its Java thread priority instead.
- A priority is never raised. If the call fails, the server logs it once and the workers keep their priority.
- Threads that a worker starts inherit its nice value.
- Applies to threads started after the setting is read: restart after changing it.
- The call is a restricted method of the Foreign Function & Memory API. The Paperclip jar's manifest carries `Enable-Native-Access: ALL-UNNAMED`, so `java -jar` prints no warning. Started another way, add `--enable-native-access=ALL-UNNAMED`.

## pregeneration

```yaml
performance:
  pregeneration:
    max-mspt: 40.0
    max-parallel: 32
    min-free-disk-gb: 2
    max-main-thread-wait: 0
    automatic:
      enabled: false
      overworld-radius: 5000
      nether-radius: 1000
      end-radius: 3000
```

| Key | Default | Values | Effect |
| --- | --- | --- | --- |
| `max-mspt` | `40.0` | 1.0 or more | While the average tick takes longer, a run halves its chunks in flight and starts none. |
| `max-parallel` | `32` | 1 or more | Upper bound for chunks in flight. |
| `min-free-disk-gb` | `2` | 0 or more | A run stops by itself when finishing would leave less free space on the world's disk. |
| `max-main-thread-wait` | `0` | 0.0 to 1.0 | Linux: also throttles while the main thread waited for a CPU longer than this share of the last minute. `0` turns it off. |
| `automatic.enabled` | `false` | boolean | Starts the radii below on every startup until they are done. |
| `automatic.overworld-radius` | `5000` | blocks, 0 for none | Radius around the world spawn. |
| `automatic.nether-radius` | `1000` | blocks, 0 for none | |
| `automatic.end-radius` | `3000` | blocks, 0 for none | |

- Progress is kept in `<world>/pregeneration.properties` and belongs to the world's seed. A world regenerated under the same name starts over.
- While a run works, Paper's `pause-when-empty-seconds` does not pause the server.
- `max-main-thread-wait` needs the [doctor's](#doctor) host monitor. Without its data (Windows, `host-monitor: false`) only `max-mspt` throttles.
- An automatic run cut short by the world border counts as done. If the border grows, it runs again.
- `max-main-thread-wait` is off by default: at 0.10 it halved pregeneration on the bench host, and on a VPS an almost idle main thread already waits 10 to 14 %, so a share of the last minute fires at baseline. Higher values are not tested.

Commands: [`/pregen`](commands.md#pregen).

## pregeneration.frontier

```yaml
performance:
  pregeneration:
    frontier:
      enabled: false
      margin-chunks: 32
```

| Key | Default | Values | Effect |
| --- | --- | --- | --- |
| `enabled` | `false` | boolean | While nobody is online, generates a margin around the tiles players have visited. |
| `margin-chunks` | `32` | 0 to 512 | Margin in chunks, rounded up to whole 16x16-chunk tiles. 32 means 5x5 tiles around each visited tile. |

- While someone is online, the server notes the tile each player stands in, once per second.
- With nobody online it generates the missing tiles within the margin, nearest first, at the chunk system's idle priority. It keeps the server awake while it works and pauses on the first join.
- It leaves a world alone while a `/pregen` run works on it, stops at `min-free-disk-gb` and is throttled by `max-mspt`, `max-parallel` and `max-main-thread-wait`.
- **The world folder grows.** With the default margin, each isolated visited tile adds up to 25 tiles (6 400 chunks, about 100 MiB at the disk guard's assumption of 16 KiB per chunk, not measured for this feature).
- State: `<world>/pregeneration-frontier.properties`.

## doctor

```yaml
performance:
  doctor:
    startup-check: true
    host-monitor: true
```

| Key | Default | Effect |
| --- | --- | --- |
| `startup-check` | `true` | Logs each `WARN` of the doctor, with its fix, on the server's first tick. |
| `host-monitor` | `true` | Linux: samples `/proc` and the cgroup files every 5 seconds for the doctor's live readings, the pregenerator's wait throttle and the worker governor. |

Command: [`/kindling doctor`](commands.md#kindling-doctor).

## worker-governor

```yaml
performance:
  worker-governor:
    enabled: false
    quiet-seconds: 60
```

| Key | Default | Values | Effect |
| --- | --- | --- | --- |
| `enabled` | `false` | boolean | Linux, needs `doctor.host-monitor`: lowers the chunk worker count while the host crowds the main thread. |
| `quiet-seconds` | `60` | 5 to 3600 | How long the host must stay quiet before a worker is added back. |

- After each 5-second host sample, the governor sorts the pressure on the main thread into four levels with the doctor's thresholds.
- Two high samples in a row take one worker away. `quiet-seconds` of quiet samples add one back. A worker that comes back and goes again within one quiet period doubles the wait, up to 8 times.
- The count stays between a quarter of the processors (at least 1) and the configured or default count. A removed worker finishes its current task first.
- A Paper config reload sets the configured count again until the next sample.
- `/kindling doctor` shows its state in the live section.

## idle-memory

```yaml
performance:
  idle-memory:
    enabled: false
    after-seconds: 300
```

| Key | Default | Values | Effect |
| --- | --- | --- | --- |
| `enabled` | `false` | boolean | Gives heap and native memory back while nobody is online. |
| `after-seconds` | `300` | 1 or more | How long the server must be empty first. |

- Runs once per idle period. Nothing runs while a player is online or a pregeneration works, and the period starts over when either begins.
- Runs a full collection through the JVM's diagnostic command, so it works with `-XX:+DisableExplicitGC`. The free-heap ratios are lowered to 5 and 10 percent for that collection.
- Then trims the native heap on glibc Linux with Java 25 or newer, unless `-XX:TrimNativeHeapInterval` is set. Logs heap and resident memory before and after.
- A heap with `-Xms` equal to `-Xmx` cannot shrink; `/kindling doctor` says so. `-Xms512M -Xmx2G` lets the collection give heap back.
- The tick spike when players rejoin after the heap shrank is not measured.

## biome-zoom-shortcut

```yaml
performance:
  biome-zoom-shortcut:
    enabled: false
```

| Key | Default | Effect |
| --- | --- | --- |
| `enabled` | `false` | Where the eight cells the biome zoom chooses from lie in one chunk column and hold one identical biome, returns that biome without the zoom's distance math. |

It only reads, and returns what the zoom would pick. Otherwise the exact zoom runs.

## minecart-push-limit

```yaml
performance:
  minecart-push-limit:
    enabled: false
    max-pushes: 4
```

| Key | Default | Values | Effect |
| --- | --- | --- | --- |
| `enabled` | `false` | boolean | Lets each minecart push at most `max-pushes` other minecarts per tick. |
| `max-pushes` | `4` | 1 or more | The other minecarts one minecart pushes per tick; it skips the others in its reach that tick, though they can still push it in their own tick. A push a plugin cancels counts too. |

Opt-in, because it changes gameplay: a crowd of minecarts spreads out more slowly than in vanilla. A cart with no more than `max-pushes` other carts in reach behaves as in vanilla. Pushes of players, mobs and items are not limited. It applies to vanilla's minecart movement; worlds with the experimental `minecart_improvements` feature use another movement and are not affected. Meant against minecart lag machines ([guide](../guides/lag-machines.md#minecarts-limit-the-pushes), [measurement](../measurements/lag-machines.md#minecart-lag-machines)).

## network.preview-chunks

The one Kindling setting under `network:` rather than `performance:`.

```yaml
network:
  preview-chunks:
    enabled: false
    radius: 32
    threads: 2
    max-pending-per-player: 256
    send-per-tick: 40
    keep-real-chunks: true
    fill-gaps: true
    trees: true
    saved-chunks: true
    cache-size: 2048
    travel-view-distance: 0
    travel-speed: 15.0
```

| Key | Default | Values | Effect |
| --- | --- | --- | --- |
| `enabled` | `false` | boolean | Players with a render distance above the view distance see previews out to `radius`, without the server loading or saving those chunks. |
| `radius` | `32` | 2 to 32 | How far previews reach, in chunks. |
| `threads` | `2` | 1 or more | Background threads computing previews for moving players. Players standing still share one more thread. |
| `max-pending-per-player` | `256` | 1 or more | Previews queued per player at most. |
| `send-per-tick` | `40` | 1 or more | Previews sent per player and tick at most. |
| `keep-real-chunks` | `true` | boolean | Real chunks a player leaves stay on the client as their own preview while within `radius`. |
| `fill-gaps` | `true` | boolean | Chunks within the view distance that are not loaded yet get a preview until the real one arrives, while the player moves, except the two chunks around the player. |
| `trees` | `true` | boolean | Seed previews get vanilla's surface features: lakes, rocks, icebergs, ice spikes, desert wells, trees, grass and flowers; in the Nether fungi, roots and the basalt deltas without their columns; in the End small islands and chorus plants. |
| `saved-chunks` | `true` | boolean | A chunk that is already saved is previewed as saved: its trees, structures and builds, from a little below its surface up (in the Nether and the End, the whole column). |
| `cache-size` | `2048` | 0 or more | Seed preview packets kept for reuse across players. |
| `travel-view-distance` | `0` (off) | 0, or 3 to 32 | A player moving faster than `travel-speed` for two seconds gets at most this view and simulation distance until it has been slower for five seconds; previews show the rest. |
| `travel-speed` | `15.0` | 1 or more | Horizontal blocks a second that count as travelling. |

- Previews are not real chunks. They are never ticked or saved, and they get no block updates.
- A seed preview shows no structures (villages, temples), ores or caves, and some trees stand elsewhere than in the real chunk ([how faithful](../measurements/preview-chunks.md#how-faithful-seed-previews-are)).
- Saved previews leave out chests, signs, banners and item frames. They are read at the lowest I/O priority through the chunk system's read path. Changes show after the next save. Worlds with Paper's Anti-Xray keep the seed previews; packet-based anti-xray plugins are not detected.
- Plugins still see the usual chunk unload event for real chunks kept as previews.
- Every world with vanilla's noise generator gets previews, the Nether and the End included; flat and void worlds do not. In the overworld a seed preview is the terrain of a band around the surface with stone below; in the Nether and the End it is the whole column, so caves and the void under the islands stay open. Nether previews carry the block light of their lava instead of sky light.
- The Nether's fog hides everything farther than 96 blocks (6 chunks) on vanilla clients, whatever the render distance. There previews only show within that distance: where `fill-gaps` or `travel-view-distance` stand in for chunks not loaded yet, or below a view distance of 6.
- A saved preview of the Nether or the End carries the whole column. Blocks hidden on all six sides (ores and rooms inside the rock) are sent as netherrack or end stone, so the preview shows what can be seen from somewhere, as a real chunk would show it.
- `travel-view-distance` changes what the server does around a fast traveller, which is why it is off by default. Chunks between the lowered and the usual distance are not loaded, so their mobs and blocks do not tick, nothing spawns there and the player does not see their entities. Chunks the player only flies past are never generated. A player faster than the chunks within the lowered distance are generated reaches chunks that are only previews. Plugins asking for the player's view, simulation or send distance get the lowered one.

## Vanilla settings worth changing

| Key in `server.properties` | Vanilla default | Effect |
| --- | --- | --- |
| `region-file-compression` | `deflate` | `lz4` saves chunks with much less CPU time and about twice the space per chunk; `none` is also accepted. Every chunk records its own compression, so older chunks stay readable after a switch. Kindling keeps the vanilla default: [when LZ4 pays off](../guides/region-compression.md), [measurement](../measurements/chunks.md#region-file-compression). |

## Leaf settings Kindling changes

A fresh `config/leaf-global.yml` (and fresh Gale files) get these defaults. An existing file keeps its values; [Switch from Leaf](../guides/switch-from-leaf.md) lists what to set by hand.

| Key | Kindling default | Why |
| --- | --- | --- |
| `async.async-entity-tracker.enabled` | `true` | On, with Kindling's fixes. |
| `async.async-playerdata-save.enabled` | `true` | On, with Kindling's fixes. |
| `performance.sleeping-block-entity` | `true` | On, acting as Paper does for plugins. |
| `async.async-mob-spawning.enabled` | `false` | Not vanilla: spawn counts can be stale. |
| `performance.optimize-player-movement` | `false` | Not vanilla. |
| `performance.skip-map-item-data-updates-if-map-does-not-have-craftmaprenderer` | `false` | Not vanilla. |

`async.async-pathfinding.enabled` stays `false`, as in Leaf: with it on, a mob follows a new path a tick or more late.

Gale's `max-projectile-chunk-loads`, `check-stuck-in-wall`, `villager-item-repickup`, `update-entity-line-of-sight` and `entity-wake-up-duration-ratio-standard-deviation` are written with vanilla's values. The exact values: [Vanilla differences](vanilla-differences.md).
