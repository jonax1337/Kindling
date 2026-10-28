# 📜 Changelog

One entry per Kindling release, newest first. Every build is a pre-release; the jars are on the [Releases](https://github.com/jonax1337/Kindling/releases) page. Build 26.3-6 was skipped and does not exist.

Legend: ✨ new · 🏆 faster (measured) · 🛡️ fixed · 🔧 changed · ⬆️ upstream · 📊 measured, no code change

Numbers come from [docs/MEASUREMENTS.md](docs/MEASUREMENTS.md) and carry their baseline. Results from two runs are marked as such and are not claimed as speed-ups.

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
