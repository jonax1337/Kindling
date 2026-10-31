# Changelog

Releases, newest first. Every build is a pre-release; the jars are on the [Releases](https://github.com/jonax1337/Kindling/releases) page. Build 26.3-6 was skipped and does not exist.

## 26.3-16 - 2026-10-31

Chunk workers that follow the demand for new terrain, and a full comparison with Paper and plain Leaf.

### Changed

- `performance.worker-priority` is on by default: elytra flight tick time p95 43.9 → 37.9 ms against the setting off ([details](docs/measurements/host.md)).

### Performance

- Terrain density skips the branches a cell corner cannot take, `performance.compiled-density.enabled` (on by default, patch `0344`): new chunks per second +0.8 % against the setting off ([details](docs/measurements/chunks.md)).
- Every chunk worker runs only while new terrain waits for one, `performance.worker-demand` (on by default, patch `0345`): flight tick 35.6 → 33.3 ms against the setting off ([details](docs/measurements/host.md)).
- Moonrise's per-section list of randomly ticking blocks allocates its index map on the first add (Paper patch `0075`): live heap after collection −57 MB against the same build without it ([details](docs/measurements/memory-jvm.md)).
- Natural spawn tries allocate no per-chunk temporaries (patch `0343`): flight tick time is unchanged (37.4 → 37.1 ms, p = 0.47) ([details](docs/measurements/ticking.md)).

### Measured

- Paper, plain Leaf and Kindling with their own defaults in five scenarios: 2.5 to 3.6 times the new chunks per second, but a pregenerated world ticks 7 % slower than Leaf ([details](docs/measurements/vs-leaf.md)).
- Profiles under load: natural spawning is a third of the main thread while players explore, the density compiler spends 7 to 10 % of the workers' CPU on branches it throws away, and 82 % of the chunk sections in memory hold only air ([details](docs/MEASUREMENTS.md)).

## 26.3-15 - 2026-10-29

Faster world generation with the same blocks, and heap advice that counts the freezes.

### Added

- `/kindling doctor` advises 3 GB for the default distances and names the measured freezes.

### Changed

- The recommended heap is 3 GB: GC pauses 8.1 → 5.0 s against 2 GB ([details](docs/measurements/memory-jvm.md)).

### Performance

- Material rules are evaluated through a class written once per rule tree, `performance.compiled-material-rules.enabled` (on by default, patch `0341`): a pregeneration made 8.4 % more chunks in the same time against the setting off ([details](docs/measurements/chunks.md)).

### Fixed

- Entities created off the tick threads get a random of their own (patch `0342`): refused mobs from duplicate UUIDs 4 → 0 in four pregeneration runs ([details](docs/measurements/vanilla-parity.md)).

### Measured

- For 2 GB heaps the README names `-XX:G1ReservePercent=25 -XX:-G1UseAdaptiveIHOP -XX:InitiatingHeapOccupancyPercent=35`: full collections per run 6–7 → 0 ([details](docs/measurements/memory-jvm.md)).
- Under load 63 % of the chunk objects in memory are partly generated neighbours, a known limit recorded in the [roadmap](docs/ROADMAP.md).

## 26.3-14 - 2026-10-29

An in-game stress test, to watch your own server under load.

### Added

- `/kindling stress players <n> [stand|fly] | mobs <n> | bases <n> | stop` puts the server under load in a throwaway world with live MSPT and TPS on a boss bar, and stops itself after 15 minutes or 20 seconds above 250 mspt.
- The stress world is deleted afterwards, the fake players are not saved, and players taken into it are sent back after a logout or restart.

## 26.3-13 - 2026-10-29

The vanilla release: where Leaf or Gale changed vanilla behaviour without a switch, Kindling restores it, and Leaf's async pathfinding and mob spawning are opt-in again. See [the parity list](docs/measurements/vanilla-parity.md).

### Added

- `/kindling doctor` gets a "vanilla parity" finding that lists settings of an older file that differ from vanilla, and no longer recommends async pathfinding.
- Opt-in `performance.biome-zoom-shortcut.enabled` skips the biome zoom's distance math where the candidate cells hold one biome: flight tick 27.2 against 27.7 ms on or off, p = 0.49.
- Opt-in `performance.worker-governor.enabled` lowers the chunk worker count while the host crowds the main thread (Linux, needs the host monitor); not measured.
- Opt-in `performance.pregeneration.frontier.enabled` generates a margin around the tiles players visited while the server is empty; not measured.
- `kindling-bench` gets `vanilla-diff`, a differential test against Mojang's server jar, the scenario key `move-event-listener` and the overlays `parity-sleeping-listener-on` and `parity-sleeping-listener-off`.

### Changed

- Fresh configurations write vanilla values, among them `async.async-pathfinding.enabled` and `async.async-mob-spawning.enabled` as `false`; existing files keep their values.

### Fixed

- `Vec3i` and `BlockPos` hash like vanilla again (Leaf patch 0260), so redstone dust update order is vanilla's.
- Mob goals keep their insertion order and drop duplicates as in Paper (Leaf patch 0236).
- Entities in a chunk section keep their insertion order on removal (Leaf patch 0195), and inside-block checks visit blocks in the direction of travel (Leaf patch 0194).
- The biome zoom chooses exactly vanilla's cell (Leaf patch 0087 differed in 2 of 200 million tested positions).
- Lightning, zombie villager cure reputation, `Paper.FailedSpawnAttempts`, the brownian walk, `World#getTileEntityCount` and shapeless recipe matching act like vanilla or Paper again (Leaf patches 0012, 0215, 0182, 0180, 0008 and Leaf paper patch 0063).
- Velocity scaling by the block speed factor, `Entity.distanceTo` and the attribute modifier summation order match vanilla again (Leaf patches 0070, 0153 and 0149).
- The zero-movement skip no longer clears the vertical collision flags and ground state of players and their vehicles (Leaf patch 0051), and `getTicksLived()` counts like Paper (Leaf patch 0259).
- A game event listener added or removed while an event is delivered no longer throws `ConcurrentModificationException` (Leaf patch 0093), and listeners far above or below the build height are no longer dropped.
- Async pathfinding, when switched on, sets the navigation target and trims paths where vanilla does, and every path completes.
- An error in async path computation gives an empty path, and a path queued after shutdown is computed on the calling thread.
- Sleeping hoppers stay asleep through Paper's temporary stack count while a plugin listens to `InventoryMoveItemEvent`, and furnaces and brewing stands wake when a stack is changed in place.
- The pregenerator is hardened further: a run cut short by the border counts as done, a failed run stays resumable, and a full disk is re-checked while an empty server sleeps (patch `0329`).
- The pregenerator's state file is never written by two threads at once, `/pregen start` before the first tick keeps other worlds' progress, and a saved radius above 3 750 000 chunks is dropped as damaged.
- Copies of unreadable chunks also cover chunks the region file gives up on, and retry a failed copy (patch `0340`).
- The vanilla diff's comparer reads Minecraft 26 block palette entries and unwraps the wrapper compounds of mixed NBT lists.

### Measured

- Against release 26.3-12 the bases and the flight tick the same, and standing among 1 200 mobs is 8 % slower (39.7 against 36.6 ms), most likely because async pathfinding and mob spawning are off now ([details](docs/measurements/vs-leaf.md)).
- Against plain Leaf `66f99f8e` time over void 85.5 % → 1.5 % and flight tick time p95 172.0 → 63.4 ms, at +48 % CPU time in the mixed load ([details](docs/measurements/vs-leaf.md)).
- The first vanilla diff: this build differs from Mojang's jar in 0 blocks over 72 contraption cells, release 26.3-12 in 8.
- The vanilla-diff worldgen mode is not conclusive: vanilla against vanilla already differs in 410 of 624 chunks.
- The parity scenarios on the new build pass.

## 26.3-12 - 2026-10-28

The hardening release: vanilla-parity fixes for the async and sleeping features, ports from Lithium, a safer pregenerator and a doctor that sees the host. It ticks as fast as 26.3-11.

### Added

- `/kindling doctor` gets live host diagnostics (Linux): CPU wait of the main thread and chunk workers, host steal, container throttling, and whether `worker-priority` took effect.
- Opt-in `performance.worker-priority` lowers the priority of chunk and I/O worker threads on Linux: main-thread wait roughly halves on the bench host. A wait throttle for the pregenerator is also opt-in, because it stops pregeneration during a flight.
- Opt-in `performance.idle-memory` gives heap and native memory back while nobody is online: about 1.45 GiB less resident memory 70 s after the last player left.
- Copies of unreadable chunks go to `<world>/kindling-quarantine/` with one `WARN` naming the path; the chunk is handled as before.

### Changed

- Lithium ports (redstone wire power, unused sensors, an early exit in the rain tick, lazier map names) with vanilla's results: bases 12.0 → 11.2 ms average tick.
- Natural spawning skips needless spawn cost biome lookups, with vanilla's results (patch `0325`).
- Client-paced chunk sending was built, measured and taken out again before the release.

### Fixed

- Sleeping block entities act as on Paper: no duplicated items when a plugin rewrites a hopper transfer (652 in the parity test), tuned `spigot.yml` hopper rates are honoured (906 instead of 2 751 items), held-back brewing stands and furnaces are served again, and a failed hopper transfer waits out Paper's cooldown.
- Async pathfinding and the async tracker act as vanilla: villagers no longer share beds or job sites (5 and 3 shared beds before, 0 after), and partial paths and the tracker's sync flags are kept.
- Async player data saving never discards a pending save and keeps writes in order (unit tests only, not yet a server test).
- Async player data saving is safe, and a failed async mob count no longer stops spawning until restart (Leaf pull request #937, mirrored).
- The pregenerator is hardened: bounded tick work, radius cut to the world border, failures contained, worlds that unload and return, an exact index at shutdown, a pause when the disk would run out (`min-free-disk-gb`) and a validated `/pregen start`.

### Measured

- Sleeping block entities re-measured: −8 % average tick time in survival bases with identical results (12.3 → 11.3 ms); the earlier −12 to −15 % no longer stands.
- The new parity scenarios reproduced four of the five documented sleeping-entity bugs on 26.3-11.
- Stricter measurement: three or more runs per variant with Welch's test and a warm-up that waits for generation to calm down.
- Against plain Leaf `66f99f8e`, eight fliers spent 2.0 % instead of 85.8 % of the flight over the void, and the bases were a draw at 26 % more CPU time.

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-12) · [Measurements](docs/MEASUREMENTS.md)

## 26.3-11 - 2026-10-27

A pregenerator status fix.

### Fixed

- `/pregen status` shows the current rate and the chunks really in flight.

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-11) · [Measurements](docs/MEASUREMENTS.md)

## 26.3-10 - 2026-10-27

Fast fliers stay over generated land on modest hardware.

### Changed

- Paper's automatic limit of concurrent chunk generations per player is capped at 32 (an explicit value in `paper-global.yml` still wins): eight fliers spent 0.9 % instead of 38.5 % over the void, at the same CPU, two runs each, not a speed claim ([details](docs/measurements/chunks.md)).

### Measured

- Flight ticks get more expensive because players now fly over chunks that exist, and stay below 50 ms at p95 (48.2 ms); standing ticks, memory and CPU time did not get worse.

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-10) · [Measurements](docs/measurements/chunks.md)

## 26.3-9 - 2026-10-27

The doctor arrives, the pregenerator survives damaged state, and Leaf's options are measured.

### Added

- `/kindling doctor` and a start-up check (`performance.doctor.startup-check`) list what costs performance or memory and name the fix.
- Parity CI: `parity.yml` and the weekly Leaf merge run the survival bases scenario and compare 15 counters, identical on Windows and Linux.

### Fixed

- Pregeneration state is written atomically and durably; a damaged file is kept as `.corrupt` and only the affected worlds start over.
- The pregenerator's state file is read strictly, the doctor's compact-headers numbers are corrected and it raises fewer false warnings.

### Measured

- Leaf's synchronous options measured one by one: none is both faster and the same behaviour, so none became a default, two runs each ([details](docs/measurements/vs-leaf.md)).

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-9) · [Measurements](docs/measurements/vs-leaf.md)

## 26.3-8 - 2026-10-27

A rename fix.

### Fixed

- `/version` and the docs point at the renamed repository.

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-8) · [Measurements](docs/MEASUREMENTS.md)

## 26.3-7 - 2026-10-27

Sleeping block entities on by default.

### Changed

- Leaf's port of Lithium's sleeping block entities is on by default: idle hoppers, furnaces, brewing stands, crafters and chests stop ticking until something can happen.
- A hopper keeps asking plugins (`InventoryMoveItemEvent`, `HopperInventorySearchEvent`, `InventoryPickupItemEvent`) as on Paper; with a plugin that holds hoppers back, Leaf's port left every chain stuck.

### Measured

- Survival bases 9.3 → 8.2 ms average tick with identical counters (two runs; superseded in 26.3-12) ([details](docs/measurements/ticking.md)).

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-7) · [Measurements](docs/measurements/ticking.md)

## 26.3-5 - 2026-10-27

A small tidy-up of the lagging check.

### Changed

- The 5-second lagging check moved into the 5-second TPS average patch (no change in behaviour).

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-5) · [Measurements](docs/measurements/ticking.md)

## 26.3-4 - 2026-10-26

Leaf and Paper merged, and a cheaper lagging check.

### Performance

- The tick loop's lagging check computes only the 5-second TPS average it uses: TPS averaging fell from 13.6 % to 3.4 % of the main thread on an idle server, one profile each; also proposed to Leaf as pull request #970 ([details](docs/measurements/ticking.md)).

### Upstream

- Merged Leaf and Paper upstream (Leaf `66f99f8e`).

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-4) · [Measurements](docs/measurements/ticking.md)

## 26.3-3 - 2026-10-26

### Added

- `/version` shows the Kindling build and commit and checks Kindling's GitHub releases for a newer build.

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-3) · [Measurements](docs/MEASUREMENTS.md)

## 26.3-2 - 2026-10-26

The pregenerator gets faster and chunk loading cheaper.

### Added

- Chunk workers sized for fast players: five eighths of the cores, at most five, never fewer than Paper (1 → 2 on 4 cores, 2 → 5 on 8, 3 → 5 on 12); eight fliers on 12 cores spent 0 % instead of 24 % over the void, and seven workers only added load, two runs each.

### Changed

- The pregenerator works in 16 × 16 chunk tiles and keeps half-generated neighbours in memory: +59 % chunks per minute and −41 % CPU per chunk against 26.3-1's ring-by-ring version, two runs each ([details](docs/measurements/pregeneration.md)).
- Block and biome palettes of saved chunks are read straight from NBT instead of through DataFixerUpper, with the same result; not measured as a speed-up.

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-2) · [Measurements](docs/measurements/pregeneration.md)

## 26.3-1 - 2026-10-26

The first Kindling build: a Leaf fork for shared hardware, based on Leaf `4c3d5689`.

### Added

- Built-in pregenerator: `/pregen start|pause|resume|cancel|status`, optional automatic start per dimension; it runs at the chunk system's lowest priority, throttles by tick time and resumes after a restart.
- Directional chunk loading queues chunks ahead of a moving player first: five elytra fliers spent 0 % instead of 9 % over ungenerated void at the same CPU, two runs each.
- Leaf's async entity tracker, async pathfinding, async player data saving and async mob spawning are on by default in a fresh `leaf-global.yml`.

### Changed

- Measured JVM advice: G1 with `-XX:+UseCompactObjectHeaders` and a right-sized heap, −29 % resident memory at the same tick time.

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-1) · [Measurements](docs/MEASUREMENTS.md)
