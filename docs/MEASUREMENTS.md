# 📊 Measurements

Why each Kindling change exists, and what was tried and left out. The scoreboard gives one row per measured change with its verdict; each row links to the topic page with the method and all numbers.

## Scoreboard

| Verdict | Meaning |
| :-: | --- |
| 🏆 | better, significant by the bench's rule |
| 🤝 | no measurable difference |
| 🔻 | costs something (slower, more CPU or memory) |
| 🛡️ | correctness or vanilla parity, no speed claim |
| 🧪 | fewer than three runs, or one profile (older rule, not a finding) |
| ⏸️ | opt-in, off by default |
| ❌ | dropped or not adopted |

| Change | Compared with | Result | Verdict | Details |
| --- | --- | --- | :-: | --- |
| Chunk defaults and directional loading, mixed flight | Plain Leaf `66f99f8e`, 3 runs | Time over void 85.8 % → 2.0 %, tick time p95 174.9 → 65.2 ms, new chunks per second 51.3 → 98.0 | 🏆 | [vs-leaf.md](measurements/vs-leaf.md#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) |
| Sleeping block entities | Same build with the option off, 5 runs | Tick time avg 12.3 → 11.3 ms (−8 %, p = 0.001), identical results | 🏆 | [ticking.md](measurements/ticking.md#sleeping-block-entities-after-the-fixes) |
| Lithium ports | Previous build, 3 runs | Tick time avg 12.0 → 11.2 ms (p = 0.016), p99 24.4 → 19.8 ms; the gain does not show against release 26.3-11 | 🏆 | [ticking.md](measurements/ticking.md#lithium-ports) |
| Survival bases | Plain Leaf `66f99f8e`, 3 runs | Tick time avg 11.7 against 12.5 ms (p = 0.28) | 🤝 | [vs-leaf.md](measurements/vs-leaf.md#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) |
| Build of 2026-10-28 | Release 26.3-11, 3 runs | No row differs in either load (flight tick time avg 27.8 against 28.8 ms, p = 0.25; bases 11.7 against 11.7 ms, p = 0.99) | 🤝 | [vs-leaf.md](measurements/vs-leaf.md#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) |
| Natural spawning | Previous build, 3 runs | Flight tick time avg 27.6 → 26.6 ms (p = 0.084), equal spawn counts per kind | 🤝 | [ticking.md](measurements/ticking.md#natural-spawning-skipped-spawn-cost-lookups) |
| Stop during a pregeneration | Release 26.3-11, 4 stops each | No difference between the builds; one 5.05 s overworld save and one off-main POI trace on the new build | 🤝 | [pregeneration.md](measurements/pregeneration.md#stop-during-a-pregeneration) |
| CPU time of Kindling | Plain Leaf `66f99f8e`, 3 runs | +53 % in the mixed load (927 → 1 416 s), +26 % in the bases (298 → 376 s) | 🔻 | [vs-leaf.md](measurements/vs-leaf.md#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) |
| Async fixes | Release 26.3-11 and previous build | Shared beds 5 and 3 → 0; standing tick time 37.0 → 36.8 ms (p = 0.77) | 🛡️ | [async.md](measurements/async.md#async-pathfinding-tracker-and-saving-after-the-fixes) |
| Loading chunks from disk | The codec path | Same result; parsing 13.9 % → 10.0 % of the worker time in the profile | 🛡️ | [chunks.md](measurements/chunks.md#loading-chunks-from-disk) |
| Copies of unreadable chunks | Build before the patch | Damaged chunk data is copied to `kindling-quarantine/`, nothing else changes | 🛡️ | [chunks.md](measurements/chunks.md#copies-of-unreadable-chunks) |
| Pregenerator hardening | Expected behaviour | Nine functional checks hold; none is a speed measurement | 🛡️ | [pregeneration.md](measurements/pregeneration.md#pregenerator-hardening-checks) |
| Lagging check of the tick loop | Build without the change | Same value; TPS averaging 13.6 % → 3.4 % of the main thread when idle | 🛡️ | [ticking.md](measurements/ticking.md#the-lagging-check-of-the-tick-loop) |
| The doctor's live readings | `/proc` read from outside | Agree within about half a percentage point | 🛡️ | [host.md](measurements/host.md#the-doctors-live-readings) |
| Vanilla defaults of a fresh configuration (ten settings) | The vanilla code that reads each value | Async pathfinding and mob spawning off, Gale limits and intervals vanilla; no speed measured | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#defaults-a-fresh-configuration-writes) |
| `Vec3i` hash, goal order, entity-section order | The vanilla code | Restored; the cost against Leaf's faster versions is not measured | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#hash-goals-and-entity-order) |
| Lightning, cure reputation, failed spawn attempts, brownian walk, tile entity count, shapeless recipes | The vanilla and Paper code | Restored; unit tests for the recipe helper only, no speed measured | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#smaller-restorations) |
| Inside-block order, movement flags, entity age, floating point, game events | The vanilla code | Restored; 6 unit tests, no speed measured | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#entity-movement-age-and-game-events) |
| Biome zoom cell | A copy of vanilla's algorithm, 4 × 10 000 000 random positions | Same cell as vanilla, including a pinned case Leaf got wrong (2 in 200 million) | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#biome-zoom) |
| Async pathfinding targets, trimming, completion (option on) | Vanilla's `PathNavigation` | Four unit tests; no server test, no speed measured | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#async-pathfinding-when-switched-on) |
| Sleeping hoppers with a move event listener | The build before the change | Unit tests only; the parity pairs and a bench run were not done | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#sleeping-hoppers-with-a-move-event-listener) |
| Biome zoom shortcut | Setting off | Same result by construction; no speed measured; off by default | ⏸️ | [vanilla-parity.md](measurements/vanilla-parity.md#biome-zoom) |
| Worker governor | Setting off | No speed measured; off by default | ⏸️ | [host.md](measurements/host.md#worker-governor) |
| Frontier pregeneration | Setting off | No speed or disk figure measured; off by default; the world folder grows | ⏸️ | [pregeneration.md](measurements/pregeneration.md#frontier-pregeneration) |
| Idle memory | Setting off, 3 runs | Resident memory with nobody online 2 492 → 1 032 MiB (−59 %); off by default | ⏸️ | [memory-jvm.md](measurements/memory-jvm.md#idle-memory) |
| Worker priority | Setting off, 3 runs | Main-thread wait roughly halves (5 430 → 2 673 ms in the elytra flight); off by default | ⏸️ | [host.md](measurements/host.md#worker-priority-and-the-main-threads-wait-for-a-cpu) |
| Pregenerator wait throttle (0.10) | Setting off, 3 runs | Pregeneration alone 140.3 → 65 chunks/s (−55 %); off by default | ⏸️ | [host.md](measurements/host.md#worker-priority-and-the-main-threads-wait-for-a-cpu) |
| Pregenerator in tiles | Kindling 26.3-1 (ring by ring), 2 runs | Chunks generated 19 040 → 30 315 (+59 %), CPU per chunk 47 → 28 ms (−41 %) | 🧪 | [pregeneration.md](measurements/pregeneration.md#pregeneration) |
| Directional chunk loading | Same build without it, 2 runs | Time over void 9 % → 0 % at the same CPU | 🧪 | [chunks.md](measurements/chunks.md#elytra-test-new-chunks-ahead-of-fast-players) |
| Chunk workers | Paper's default (3 workers on 12 cores), 2 runs | Time over void 24 % → 0 % with 5 workers | 🧪 | [chunks.md](measurements/chunks.md#default-number-of-chunk-workers) |
| Cap on concurrent generations | Paper's automatic limit (88), 2 runs | Time over void 31.5 % → 1.2 % with a cap of 32 | 🧪 | [chunks.md](measurements/chunks.md#concurrent-generations-per-player) |
| Mixed load, Kindling defaults | Leaf `4c3d5689` with a 3 GB heap, 2 runs | Peak RSS 3 590 → 2 555 MB; the tick times are not a finding | 🧪 | [vs-leaf.md](measurements/vs-leaf.md#mixed-load-leaf-against-the-kindling-defaults) |
| Leaf's options one by one | Kindling defaults, 2 runs | No option is both faster and the same behaviour | 🧪 | [vs-leaf.md](measurements/vs-leaf.md#leafs-options-one-by-one) |
| Sleeping block entities, first measurement | Kindling 26.3-4 with the option off, 2 runs | Bases tick time avg 9.3 → 8.0 ms; re-measured with five runs | 🧪 | [ticking.md](measurements/ticking.md#survival-bases-sleeping-block-entities) |
| JVM and heap | Leaf with a 3 GB heap, 2 runs | Peak RSS 3 590 → 2 549 MB (−29 %) at the same tick time | 🧪 | [memory-jvm.md](measurements/memory-jvm.md#jvm-and-heap) |
| AOT cache | Start without the cache, one start each | billiards 9.5 to 10.7 s instead of 16.9 to 26.9 s | 🧪 | [memory-jvm.md](measurements/memory-jvm.md#start-time-and-native-memory-cubyx-vps) |
| Waiting for a CPU on the VPS | The thread's own running time, one sample | Main thread ran 326 s, waited 92 s (+28 %) | 🧪 | [host.md](measurements/host.md#waiting-for-a-cpu-on-the-vps) |
| Client-paced chunk sending | Setting off, 3 runs | A fast client gets its view no sooner; a slow client joins 6 325 → 12 881 ms; dropped | ❌ | [chunks.md](measurements/chunks.md#client-paced-chunk-sending) |
| Ten other Leaf options | Mixed load table, 2 runs | None adopted: slower, no effect, or not vanilla behaviour | ❌ | [vs-leaf.md](measurements/vs-leaf.md#not-adopted-although-leaf-offers-them) |
| Three ideas looked at | Profile shares | Left alone: the gain was too small for the risk | ❌ | [ticking.md](measurements/ticking.md#looked-at-and-left-alone) |

## Topics

- [Kindling against Leaf](measurements/vs-leaf.md): the final round against plain Leaf and release 26.3-11, the older mixed load, and Leaf's options one by one.
- [Chunk generation and loading](measurements/chunks.md): directional loading, chunk workers, the generation cap, the palette decoder, copies of unreadable chunks and the dropped client-paced sending.
- [Pregeneration](measurements/pregeneration.md): the tiled pregenerator, its hardening checks, the stop during a run, frontier pregeneration and the chunk workers' profile.
- [Vanilla parity](measurements/vanilla-parity.md): what Kindling restores where Leaf or Gale changed vanilla, the defaults of a fresh configuration, and what still differs. No speed figures.
- [World ticking](measurements/ticking.md): sleeping block entities, Lithium ports, natural spawning, the lagging check, the main thread's profile and what was left alone.
- [Async defaults](measurements/async.md): villager parity and the cost of the fixes for async pathfinding, tracker and saving.
- [JVM, start time and memory](measurements/memory-jvm.md): JVM flags and heap, AOT cache and native heap trim, idle memory.
- [The host: waiting for a CPU](measurements/host.md): run-queue waits on the VPS, worker priority, the pregenerator's wait throttle, the worker governor and the doctor's live readings.

## How to read the numbers

**The rule (since 2026-10-28).** A variant runs at least three times; `compare.py` prints `mean ±half range nN` (half range: half the gap between the highest and the lowest run) and the p-value of Welch's t-test against the first label. A difference counts as real only if it is marked `*`: p < 0.05 **and** the means are more than twice the larger half range apart. Before, a difference counted when it was larger than the gap between two runs; with two runs that is about p = 0.29, so a claim such as "−3 %" could not be told from noise. A table on these pages marked "two runs" meets the old rule only. Tables with three or more runs name the p-value; a row without `*` is not a finding, even when it points in a direction. With many rows per comparison, one row at p < 0.05 can be chance; these pages only quote such a row next to the others it came from.

<details>
<summary><b>What is compared with what</b></summary>


- *Against plain Leaf:* the [final round](measurements/vs-leaf.md#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) compares Kindling with plain Leaf at the same base commit, each with its own defaults. That is the comparison the README and the website lead with. Older tables name the Leaf commit they used.
- *Against the same build without the change:* a section about one change compares the build with it against the build without it. That is the only way to see what each change does on its own. The baseline is named in each section, and it can be an earlier Kindling build, for example the pregenerator's first version (26.3-1), because plain Leaf has no pregenerator.

</details>

<details>
<summary><b>Corrections to the older tables</b></summary>


- *Tick time during a flight depends on the time in the void.* A player over ungenerated terrain has nothing to load, tick or send, so less void means more chunks for the main thread and a higher flight tick time (see [the worker counts](measurements/chunks.md#default-number-of-chunk-workers)). Flight tick times compare only at the same void share.
- *The "0 % void" figures measure one line straight ahead* of each player. The bench now also follows four more lines (at ±45° and ±90° to the flight direction) and the player's own chunk. The figures of 2026-10-26/27 on these pages were measured with the single line; they were not repeated with the five lines.
- *"GC time" in older tables adds up every collector bean* of the JVM. For G1 against G1 that is comparable; for ZGC and Shenandoah it adds the concurrent cycles to the pauses. Newer runs report `gc_pause_millis`, which leaves out the "... Cycles" beans.
- *Elytra comparisons with equal JVM flags.* The elytra script used to start Kindling with a 2 GB heap and compact object headers and the Leaf jar with a 3 GB heap without them. It now gives both the same flags (3 runs each by default). The Leaf rows of [the elytra table](measurements/chunks.md#elytra-test-new-chunks-ahead-of-fast-players) were measured with the old script and were not repeated, so their CPU and tick time may differ from a like-for-like run; the void and lead figures there come from the number of workers and the loading order, which the heap flags do not change.
- *Fake players are creative by default*, so mobs never target them. `gamemode=survival` exists since 2026-10-28; the mixed A/Bs of that day used it.
- *Raw data* lands in `kindling-bench/runs/` (not checked in). The Linux figures (`main_wait_ms`, steal, idle) exist only where `/proc` does.

</details>

## Bench host and runs

> [!NOTE]
> The numbers up to 2026-10-27 come from `kindling-bench` ([how it works](../kindling-bench/README.md)), measured on a Windows machine with 12 logical cores and Java 25.0.4 (the mixed load, the elytra test, the worker counts) or on the bench host (the sections that say so), against Leaf `4c3d5689` (Paper `1b9bf5c8`). **Those variants ran twice; tables show the mean.** Two runs do not meet the rule that applies since 2026-10-28 ([how to read the numbers](#how-to-read-the-numbers)); where a claim rests on two runs, its section says so. The sections from 2026-10-28 ran three or more times per variant; the [final round](measurements/vs-leaf.md#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) compared against Leaf `66f99f8e`, the commit Kindling is now based on. A shared-core VPS will show other absolute numbers; the direction should hold.
>
> **Bench host:** a PC with an Intel i7-7700 (4 cores, 8 threads), 16 GB, Linux (Ubuntu 24.04 under WSL2), Temurin 25.0.5, G1. Unless a section says otherwise, its server ran with `-XX:+UseCompactObjectHeaders -Xms2G -Xmx2G` and 8 CPUs.
