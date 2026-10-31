# 📜 Changelog

One entry per Kindling release, newest first. Every build is a pre-release; the jars are on the [Releases](https://github.com/jonax1337/Kindling/releases) page. Build 26.3-6 was skipped and does not exist.

Legend: ✨ new · 🏆 faster (measured) · 🛡️ fixed · 🔧 changed · ⬆️ upstream · 📊 measured, no code change

Numbers come from [docs/MEASUREMENTS.md](docs/MEASUREMENTS.md) and carry their baseline. Results from two runs are marked as such and are not claimed as speed-ups.

## 26.3-16 · 2026-10-31
Chunk workers that follow the demand for new terrain, exact savings from the profiles of 2026-10-29 and a full comparison with Paper and plain Leaf; bench host, 3 interleaved runs each, the baseline on every line.

- 🏆 The terrain density is sampled for each cell corner without the branches that corner cannot take, `performance.compiled-density.enabled` (on by default; new patch `0344`): new chunks per second in a pregeneration 195.0 → 196.5 (+0.8 %) at the same CPU time, against the setting off; 40 000 verified calls and a whole world identical ([details](docs/measurements/chunks.md#compiled-density))
- 🏆 Every chunk worker runs only while new terrain waits for one, `performance.worker-demand` (on by default; new patch `0345` measures the wait): flight over a pregenerated world 35.6 → 33.3 ms, main-thread wait 709 → 311 ms, CPU time 513 → 477 s against the setting off; elytra, mixed flight and bases unchanged; against plain Leaf the pregenerated flight is now +5 % at the same CPU time ([details](docs/measurements/host.md#worker-demand))
- 🔧 `performance.worker-priority` is on by default: elytra flight tick time p95 43.9 → 37.9 ms and main-thread wait −59 % at the same chunk rate, against the setting off ([details](docs/measurements/host.md#worker-priority-and-the-main-threads-wait-for-a-cpu))
- 📊 Paper, plain Leaf and Kindling with their own defaults in five scenarios: 2.5 to 3.6 times the new chunks per second, 0 % instead of 82 % of the mixed flight over void, bases 11.8 → 10.8 ms (Paper 13.2), standing mobs 39.1 → 34.6 ms; a pregenerated world ticks 7 % slower than Leaf and the CPU time is 8 to 83 % higher ([details](docs/measurements/vs-leaf.md#campaign-of-2026-10-30-paper-plain-leaf-and-kindling))
- 🏆 Moonrise's per-section list of randomly ticking blocks allocates its index map on the first add instead of in the constructor (new Paper patch `0075`): live heap after collection 1 198 → 1 141 MB (−57 MB, p < 0.001); tick times, full collections (8 per run with 2 GB) and CPU time unchanged ([details](docs/measurements/memory-jvm.md#the-ring-of-partly-generated-chunks))
- 🤝 Natural spawn tries allocate no bound method references, `Vec3` or `ChunkPos` per chunk and category, look the nearby players up once per chunk and keep the spawn sampler (new patch `0343`); the flight ticks the same (37.4 → 37.1 ms, p = 0.47); the main thread waited 16 % less for a CPU and sent 2 % more chunk packets, both marked but small ([details](docs/measurements/ticking.md#cheaper-natural-spawn-tries))
- 📊 Profiles of the main thread, the chunk workers and the heap under load are in [MEASUREMENTS](docs/MEASUREMENTS.md): natural spawning is a third of the main thread while players explore, the density compiler spends 7 to 10 % of the workers' CPU on branches it throws away, and 82 % of the chunk sections in memory hold only air

## 26.3-15 · 2026-10-29
Faster world generation with the same blocks, and heap advice that counts the freezes. Measured on the bench host, 3 runs each, 2026-10-29; see [MEASUREMENTS](docs/MEASUREMENTS.md).

- 🏆 Material rules (surface, ore vein and underground blocks) are evaluated through a class written once per rule tree, `performance.compiled-material-rules.enabled` (on by default; new patch `0341`); vanilla's own conditions decide every block: a pregeneration made 8.4 % more chunks in the same time (63 376 → 68 693 in 240 s) at the same CPU time, and 1.3 billion blocks evaluated both ways gave 0 differences ([details](docs/measurements/chunks.md#compiled-material-rules))
- 🛡️ Entities created off the tick threads get a random of their own (new patch `0342`): with Purpur's shared random, chunk workers could give two generated mobs the same UUID and the chunk system dropped the second; 4 refused mobs in 2 of 4 pregeneration runs before, 0 in 4 after ([details](docs/measurements/vanilla-parity.md#entity-uuids-from-world-generation))
- 🔧 The recommended heap is 3 GB (README, website, `/kindling doctor`, which now advises 3 GB for the default distances and names the measured freezes): with 2 GB, 8 players exploring new terrain caused 5 to 6 full collections of 0.7 to 0.8 s per run, with 3 GB 2; GC pauses 8.1 → 5.0 s, CPU time −3.8 %, 1 GB more resident memory ([details](docs/measurements/memory-jvm.md#jvm-and-heap))
- 📊 For 2 GB heaps the README now names `-XX:G1ReservePercent=25 -XX:-G1UseAdaptiveIHOP -XX:InitiatingHeapOccupancyPercent=35`: full collections per run 6–7 → 0, longest pause 762–849 → 120–202 ms, at the same memory and about 5 % more CPU (3 runs each); Aikar's flags halved the full collections but kept the pauses ([details](docs/measurements/memory-jvm.md#jvm-and-heap))
- 📊 Under load 63 % of the chunk objects in memory are partly generated neighbours (11 181 of 17 672), vanilla's chunk levels; recorded as a known limit in the [roadmap](docs/ROADMAP.md)

## 26.3-14 · 2026-10-29
An in-game stress test, to watch your own server under load.

- ✨ `/kindling stress players <n> [stand|fly] | mobs <n> | bases <n> | stop` puts the server under load in a throwaway world with live MSPT and TPS on a boss bar; the world is deleted afterwards, the fake players are not saved, players who were taken into the test world are sent back even after a logout or a restart, and the test stops itself after 15 minutes or 20 seconds above 250 mspt

## 26.3-13 · 2026-10-29
The vanilla release: where Leaf or Gale changed vanilla behaviour without a switch, Kindling restores it, and Leaf's async pathfinding and mob spawning are opt-in again. Measured against release 26.3-12 (3 runs each, 2026-10-29): the bases and the flight tick the same, standing among 1 200 mobs is 8 % slower, most likely because async pathfinding and mob spawning are off now. See [the parity list](docs/measurements/vanilla-parity.md) and [the measurements](docs/measurements/vs-leaf.md#final-round-the-vanilla-build-of-2026-10-29-against-263-12-and-plain-leaf).

- 🔧 Fresh configurations write vanilla values: `async.async-pathfinding.enabled` and `async.async-mob-spawning.enabled` are `false`, `performance.optimize-player-movement` and `performance.skip-map-item-data-updates-if-map-does-not-have-craftmaprenderer` are `false`, and Gale's projectile chunk-load limits (`-1`), in-wall check interval (`1`), line-of-sight interval (`1`), villager item re-pickup (`-1`) and entity wake-up deviation (`0`) are vanilla; existing files keep their values
- ✨ `/kindling doctor` gets a "vanilla parity" finding that lists settings of an older file that differ from vanilla and the value to set, and no longer recommends async pathfinding
- 🛡️ `Vec3i` and `BlockPos` hash like vanilla again (Leaf patch 0260), so `HashSet<BlockPos>` order and redstone dust update order are vanilla's; no cost shows in the bases or the flight
- 🛡️ Mob goals keep their insertion order, first-added wins at equal priority, duplicates are dropped as in Paper (Leaf patch 0236); no cost shows in the bases or the flight
- 🛡️ Entities in a chunk section keep their insertion order on removal (Leaf patch 0195), and inside-block checks visit blocks in the direction of travel (Leaf patch 0194); a moving entity allocates one set per call again; no cost shows in the bases or the flight
- 🛡️ The biome zoom chooses exactly vanilla's cell (Leaf patch 0087 added blocks in a different order in 2 of 200 million tested positions); checked against a copy of vanilla's algorithm
- 🛡️ Lightning strikes at the configured chance per chunk tick again (Leaf patch 0012, Gale), a zombie villager's cure reputation goes through the online player only (Leaf patch 0215), `Paper.FailedSpawnAttempts` is only counted and saved when a throttle is on (Leaf patch 0182), the brownian walk no longer draws from the mob's random (Leaf patch 0180), `World#getTileEntityCount` counts like Paper (Leaf paper patch 0063) and shapeless recipes match like vanilla when an item fits two ingredients (Leaf patch 0008, Gale)
- 🛡️ The zero-movement skip no longer clears the vertical collision flags and ground state of players and player-steered vehicles (Leaf patch 0051, Gale); velocity scaling by the block speed factor, `Entity.distanceTo` and the attribute modifier summation order match vanilla; `getTicksLived()` counts ticked ticks like Paper unless a despawn time is set (Leaf patch 0259)
- 🛡️ A game event listener added or removed while an event is delivered no longer throws `ConcurrentModificationException`, and listeners far above or below the build height are no longer dropped (Leaf patch 0093)
- 🛡️ Async pathfinding, when switched on, sets the navigation target and trims paths where vanilla does, and every path completes (an error in the computation gives an empty path; a path queued after shutdown is computed on the calling thread)
- 🛡️ Sleeping hoppers stay asleep through Paper's temporary stack count while a plugin listens to `InventoryMoveItemEvent`; furnaces and brewing stands wake when a stack in their inventory is changed in place (count or components)
- 🛡️ The pregenerator is hardened further: an automatic run cut short by the border counts as done, a run that fails stays resumable, a full disk is re-checked while an empty server sleeps (new patch `0329`), `/pregen start` before the first tick keeps other worlds' progress, the state file is never written by two threads at once, and a saved radius above 3 750 000 chunks is dropped as damaged
- 🛡️ Copies of unreadable chunks also cover chunks the region file gives up on (truncated header or stream, bad length, unknown compression, unreadable external file), take only the sectors the header allotted, skip an oversized chunk without stopping others and retry a failed copy (patch `0340`)
- ✨ Opt-in `performance.biome-zoom-shortcut.enabled` (off by default): skips the biome zoom's distance math where the eight candidate cells hold one biome; the result is the same as without it, and it showed no tick time difference on or off (flight 27.2 against 27.7 ms, p = 0.49)
- ✨ Opt-in `performance.worker-governor.enabled` and `quiet-seconds` (off by default, Linux, needs the host monitor): lowers the chunk worker count while the host crowds the main thread and raises it again when it is quiet; not measured
- ✨ Opt-in `performance.pregeneration.frontier.enabled` and `margin-chunks` (off by default): generates a margin around the tiles players visited while the server is empty; the world folder grows by up to 25 tiles per isolated visited tile at the default margin; not measured
- 📊 Against release 26.3-12 (3 runs each, bench host): bases 11.5 against 11.5 ms (p = 0.80), flight tick time avg 27.2 against 28.1 ms (p = 0.29), join and time over void equal, CPU time equal; standing among 1 200 mobs 39.7 against 36.6 ms (+8 %, p = 0.005), 37.5 ms with the async options switched on again (p = 0.051); with a move event listener the bases tick 11.8 against 11.7 ms (p = 0.63)
- 📊 Against plain Leaf `66f99f8e` (3 runs each): time over void 85.5 % → 1.5 %, flight tick time p95 172.0 → 63.4 ms, bases 12.3 → 11.5 ms (p = 0.008), CPU time +48 % in the mixed load and +25 % in the bases; standing 47.9 → 39.7 ms is not significant
- 📊 The vanilla diff ran for the first time: in 72 contraption cells over 400 ticks this build differs from Mojang's jar in 0 blocks, release 26.3-12 in 8 (a redstone wire network unpowered, a repeater), vanilla against vanilla in 0; the worldgen mode is not conclusive (vanilla against vanilla already differs in 410 of 624 chunks)
- 📊 Parity scenarios on the new build pass: bases 15/15 counters, both sleeping pairs 10/10, bases with a move event listener 15/15, villagers 4/4
- 🛡️ The vanilla diff's comparer reads Minecraft 26 block palette entries and unwraps the wrapper compounds of mixed NBT lists, both with unit tests
- 📊 `kindling-bench` gets `vanilla-diff`, a differential test against Mojang's server jar, the scenario key `move-event-listener` and the overlays `parity-sleeping-listener-on` and `parity-sleeping-listener-off`

## 26.3-12 · 2026-10-28
The hardening release: vanilla-parity fixes for the async and sleeping features, ports from Lithium, a safer pregenerator and a doctor that sees the host. It measured the same speed as 26.3-11 in both bench loads.

- ✨ `/kindling doctor` gets live host diagnostics (Linux): how long the main thread and the chunk workers waited for a CPU, CPU time taken by the host (steal), container CPU throttling, and whether `worker-priority` took effect; it tells wake-up latency from real contention, and each piece of advice names its source
- ✨ Opt-in `performance.worker-priority` (off by default): chunk and I/O worker threads lower their own priority, so they compete less with the main thread on Linux (main-thread wait roughly halves on the bench host); a wait throttle for the pregenerator is also opt-in, because it stops pregeneration during a flight
- ✨ Opt-in `performance.idle-memory` (off by default): gives heap and native memory back while nobody is online; with `-Xms512M -Xmx2G` about 1.45 GiB less resident memory 70 s after the last player left (3 runs each, on vs off)
- ✨ Copies of unreadable chunks: when a stored chunk cannot be decompressed, read or parsed, its bytes and the exception are copied to `<world>/kindling-quarantine/` and one `WARN` names the path; the chunk is handled as before
- 🛡️ Sleeping block entities act as on Paper: no more duplicated items when a plugin rewrites a hopper transfer (652 in the parity test), tuned `spigot.yml` hopper rates are honoured (906 instead of 2 751 items), blocks set without block updates are noticed, held-back brewing stands and furnaces are served again, and a failed hopper transfer waits out Paper's cooldown; the new parity scenarios reproduced four of the five documented bugs on 26.3-11 (the held-back furnace and shared job sites did not reproduce, so those two fixes have no counter)
- 🛡️ Async pathfinding and the async tracker act as vanilla: villagers no longer share beds or job sites (5 and 3 shared beds before, 0 after), partial paths and the tracker's sync flags are kept
- 🛡️ Async player data saving is safe, and a failed async mob count no longer stops spawning until restart (Leaf pull request #937, mirrored): a pending save is never discarded, writes stay in order, `save-all flush` and the stop wait for them, a failed encode writes nothing (covered by unit tests only, not yet by a server test)
- 🛡️ The pregenerator is hardened: bounded tick work, radius cut to the world border, failures contained, worlds that unload and return, state file written off the main thread, exact index at shutdown, a pause when the disk would run out (`min-free-disk-gb`), and a validated `/pregen start`
- 🔧 Lithium ports: redstone wire power, sensors a mob does not use, an early exit in the rain tick and lazier map names, all with vanilla's results; bases scenario 12.0 → 11.2 ms average tick, 19.8 instead of 24.4 ms at p99 (3 runs each); the gain does not show against 26.3-11 (the build runs the bases at the release's 11.7 ms); natural spawning also skips useless biome lookups, vanilla-identical, no measurable tick time change
- 📊 Sleeping block entities re-measured on this build: −8 % average tick time in survival bases with identical results (12.3 → 11.3 ms, option on against off, 5 runs each); the earlier two-run −12 to −15 % no longer stands
- 🔧 Client-paced chunk sending was built, measured (fast clients got no earlier view, slow ones took twice as long to join) and taken out again before the release
- 📊 Stricter measurement: three or more runs per variant with Welch's test (`compare.py` marks real differences), a warm-up that waits for generation to calm down, five lines for the chunk lead and the player's own chunk, GC pauses instead of the sum of all collector beans, the main thread's run-queue wait, survival fake players, emulated client answers, the same JVM flags in the elytra script, and parity scenarios that run each option on and off
- 📊 Against plain Leaf `66f99f8e` (3 runs each, 4-core/8-thread host): eight fliers spent 2.0 % instead of 85.8 % of the flight over the void, at 27.8 instead of 37.7 ms per tick; the bases were a draw (11.7 against 12.5 ms, not significant) at 26 % more CPU time

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-12) · [Measurements](docs/MEASUREMENTS.md)

## 26.3-11 · 2026-10-27
A pregenerator status fix.

- 🛡️ `/pregen status` shows the current rate and the chunks really in flight

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-11) · [Measurements](docs/MEASUREMENTS.md)

## 26.3-10 · 2026-10-27
Fast fliers stay over generated land on modest hardware.

- 🔧 Paper's automatic limit of concurrent chunk generations per player is capped at 32 (an explicit value in `paper-global.yml` still wins); eight fliers on the 8-thread bench host spent 0.9 % instead of 38.5 % of a mixed load over the void, at the same CPU (two runs each, not a speed claim)
- 📊 Flight ticks get more expensive because the players now fly over chunks that exist; they stay below 50 ms at p95 (48.2 ms), standing ticks, memory and CPU time did not get worse

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-10) · [Measurements](docs/measurements/chunks.md#concurrent-generations-per-player)

## 26.3-9 · 2026-10-27
The doctor arrives, the pregenerator survives damaged state, and Leaf's options are measured.

- ✨ `/kindling doctor` and a start-up check (`performance.doctor.startup-check`): list what costs performance or memory (CPUs, heap against memory, collector, compact object headers, chunk workers, view distance, compression behind a proxy, free disk) and name the fix
- ✨ Parity CI: `parity.yml` and the weekly Leaf merge run the survival bases scenario and compare 15 counters; identical on Windows and Linux
- 🛡️ Pregeneration state is written atomically and durably; a damaged file is kept as `.corrupt` and only the affected worlds start over
- 🛡️ The pregenerator's state file is read strictly: unknown keys and an unknown `order` value are reported instead of being ignored; the doctor's compact-headers numbers are corrected and it raises fewer false warnings
- 📊 Leaf's synchronous options measured one by one (two runs each): none is both faster and the same behaviour, so none became a default

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-9) · [Measurements](docs/measurements/vs-leaf.md#leafs-options-one-by-one)

## 26.3-8 · 2026-10-27
A rename fix.

- 🛡️ `/version` and the docs point at the renamed repository

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-8) · [Measurements](docs/MEASUREMENTS.md)

## 26.3-7 · 2026-10-27
Sleeping block entities on by default.

- 🔧 Leaf's port of Lithium's sleeping block entities is on by default: idle hoppers, furnaces, brewing stands, crafters and chests stop ticking until something can happen
- 🔧 A hopper keeps asking plugins (`InventoryMoveItemEvent`, `HopperInventorySearchEvent`, `InventoryPickupItemEvent`) as on Paper; with a stand-in plugin that holds hoppers back, Leaf's port left every chain stuck and Kindling matched a server without sleeping hoppers
- 📊 First measurement: survival bases 9.3 → 8.2 ms average tick with identical counters (two runs; superseded in 26.3-12 by the 5-run −8 %)

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-7) · [Measurements](docs/measurements/ticking.md#sleeping-block-entities-after-the-fixes)

## 26.3-5 · 2026-10-27
A small tidy-up of the lagging check.

- 🔧 The 5-second lagging check moved into the 5-second TPS average patch (no change in behaviour)

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-5) · [Measurements](docs/measurements/ticking.md#the-lagging-check-of-the-tick-loop)

## 26.3-4 · 2026-10-26
Leaf and Paper merged, and a cheaper lagging check.

- 🔧 The tick loop's lagging check computes only the 5-second TPS average it uses; same value; the TPS averaging fell from 13.6 % to 3.4 % of the main thread on an idle server (one JFR profile each, not a repeated run); also proposed to Leaf as pull request #970
- ⬆️ Merged Leaf and Paper upstream (Leaf `66f99f8e`)

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-4) · [Measurements](docs/measurements/ticking.md#the-lagging-check-of-the-tick-loop)

## 26.3-3 · 2026-10-26
- ✨ `/version` shows the Kindling build, commit and date and checks Kindling's GitHub releases for a newer build

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-3) · [Measurements](docs/MEASUREMENTS.md)

## 26.3-2 · 2026-10-26
The pregenerator gets faster and chunk loading cheaper.

- ✨ Chunk workers sized for fast players: five eighths of the cores, at most five, never fewer than Paper (1 → 2 on 4 cores, 2 → 5 on 8, 3 → 5 on 12); eight fliers on 12 cores spent 0 % instead of 24 % over the void, and seven workers only added load (two runs each)
- 🔧 The pregenerator works in 16 × 16 chunk tiles and keeps half-generated neighbours in memory: +59 % chunks per minute and −41 % CPU per chunk against 26.3-1's ring-by-ring version (two runs each)
- 🔧 Block and biome palettes of saved chunks are read straight from NBT instead of through DataFixerUpper; same result (anything unusual still goes through the codec), a smaller share of the chunk workers' time, not measured as a speed-up on its own

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-2) · [Measurements](docs/measurements/pregeneration.md#pregeneration)

## 26.3-1 · 2026-10-26
The first Kindling build: a Leaf fork for shared hardware, based on Leaf `4c3d5689`.

- ✨ Built-in pregenerator: `/pregen start|pause|resume|cancel|status`, optional automatic start per dimension; runs at the chunk system's lowest priority, throttles by tick time and resumes after a restart
- ✨ Directional chunk loading: chunks ahead of a moving player are queued before chunks behind; five elytra fliers spent 0 % instead of 9 % over ungenerated void with the same CPU (two runs each, measured straight ahead of each player)
- ✨ Async defaults: Leaf's async entity tracker, async pathfinding, async player data saving and async mob spawning are on by default in a fresh `leaf-global.yml`
- 🔧 Measured JVM advice: G1 with `-XX:+UseCompactObjectHeaders` and a right-sized heap, −29 % resident memory at the same tick time

[Release](https://github.com/jonax1337/Kindling/releases/tag/26.3-1) · [Measurements](docs/MEASUREMENTS.md)
