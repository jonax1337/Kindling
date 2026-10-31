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
| Campaign, new terrain (chunk defaults, directional loading, compiled generation) | Paper 26.3-223 and plain Leaf `66f99f8e`, 3 runs | Mixed flight: void 82.2 % → 0 %, new chunks per second 64.8 → 159.8, join 50.1 → 27.2 s, tick time p95 130 → 60 ms (average 32.4 → 36.3 ms); elytra 39.7 → 143.6 chunks per second, void 92.7 → 7.5 %; Paper equals Leaf | 🏆 | [vs-leaf.md](measurements/vs-leaf.md#campaign-of-2026-10-30-paper-plain-leaf-and-kindling) |
| Campaign, ticking without new terrain | Paper 26.3-223 and plain Leaf `66f99f8e`, 3 runs | Bases 11.8 → 10.8 ms (Paper 13.2), 1 200 standing mobs 39.1 → 34.6 ms (Paper 51.6), working villagers 8.5 → 8.4 ms (not significant, Paper 14.4); every counter identical on the three servers | 🏆 | [vs-leaf.md](measurements/vs-leaf.md#campaign-of-2026-10-30-paper-plain-leaf-and-kindling) |
| Flight over a pregenerated world | Plain Leaf `66f99f8e`, 3 runs | With worker demand 31.7 → 33.3 ms (+5 %), p95 43.5 → 50.5 ms, at the same CPU time (462 → 477 s, p = 0.085); five fixed workers were +12 % (35.6 ms) and +11 % CPU; the cause was the worker count, not the async options | 🔻 | [host.md](measurements/host.md#worker-demand) |
| CPU time of Kindling | Plain Leaf `66f99f8e`, 3 runs | Bases +13 % (276 → 313 s, with worker demand), pregenerated world equal (above); campaign before worker demand: +43 % mixed load (810 → 1 160 s, but 127 → 79 ms per new chunk), +83 % elytra; the extra is in the chunk workers (JFR) | 🔻 | [host.md](measurements/host.md#worker-demand) |
| Worker demand | Setting off (five fixed workers), 3 runs | Pregenerated flight tick time 35.6 → 33.3 ms, main-thread wait 709 → 311 ms, CPU time 513 → 477 s; elytra, mixed flight and bases without a marked difference; on by default | 🏆 | [host.md](measurements/host.md#worker-demand) |
| Compiled density (patch `0344`) | Same build with the setting off, 3 runs | Pregeneration new chunks per second 195.0 → 196.5 (+0.8 %), same CPU time; 40 000 verified calls and a whole world identical | 🏆 | [chunks.md](measurements/chunks.md#compiled-density) |
| Async pathfinding and mob spawning (opt-in) | The final build with them off, 3 runs | Standing among 1 200 mobs 34.9 → 31.8 ms (−9 %); pregenerated flight 27.3 → 26.9 ms (p = 0.40); not vanilla, so off | ⏸️ | [vs-leaf.md](measurements/vs-leaf.md#campaign-of-2026-10-30-paper-plain-leaf-and-kindling) |
| Compiled material rules | Same build with the setting off, 3 runs | Pregeneration +8.4 % chunks in 240 s (63 376 → 68 693), new chunks per second 175.2 → 192.0, same CPU time; 0 block differences in 1.3 billion | 🏆 | [chunks.md](measurements/chunks.md#compiled-material-rules) |
| Heap 3 GB instead of 2 GB | `-Xmx2G`, 3 runs | Full collections during play 5–6 → 2 per run, GC pauses 8.1 → 5.0 s, CPU −3.8 %, flight tick time 32.4 → 30.5 ms; +1 GB resident memory | 🏆 | [memory-jvm.md](measurements/memory-jvm.md#jvm-and-heap) |
| G1 reserve 25 % and marking at 35 %, 2 GB heap | Plain 2 GB, 3 runs | Full collections per run 6–7 → 0, longest pause 762–849 → 120–202 ms, GC pauses 8.4 → 5.4 s; CPU +4.9 %, standing tick time 35.8 → 37.3 ms; same memory. Aikar's flags: 3–4 full collections, pauses unchanged | 🏆 | [memory-jvm.md](measurements/memory-jvm.md#jvm-and-heap) |
| Sleeping block entities | Same build with the option off, 5 runs | Tick time avg 12.3 → 11.3 ms (−8 %, p = 0.001), identical results | 🏆 | [ticking.md](measurements/ticking.md#sleeping-block-entities-after-the-fixes) |
| Lithium ports | Previous build, 3 runs | Tick time avg 12.0 → 11.2 ms (p = 0.016), p99 24.4 → 19.8 ms; the gain does not show against release 26.3-11 | 🏆 | [ticking.md](measurements/ticking.md#lithium-ports) |
| Final round: the vanilla build | Release 26.3-12, 3 runs | Bases 11.5 against 11.5 ms (p = 0.80), flight tick time avg 27.2 against 28.1 ms (p = 0.29, p95 63.4 against 65.6 ms marked), join and void equal | 🤝 | [vs-leaf.md](measurements/vs-leaf.md#final-round-the-vanilla-build-of-2026-10-29-against-263-12-and-plain-leaf) |
| Vanilla defaults of a fresh configuration (async pathfinding and mob spawning off) | Release 26.3-12, 3 runs, second series with the options on | Standing tick time among 1 200 mobs 36.6 → 39.7 ms (+8 %, p = 0.005); 37.5 ms with the async options on (p = 0.051, misses the rule), so they are the likely cause | 🔻 | [vs-leaf.md](measurements/vs-leaf.md#final-round-the-vanilla-build-of-2026-10-29-against-263-12-and-plain-leaf) |
| Build of 2026-10-28 | Release 26.3-11, 3 runs | No row differs in either load (flight tick time avg 27.8 against 28.8 ms, p = 0.25; bases 11.7 against 11.7 ms, p = 0.99) | 🤝 | [vs-leaf.md](measurements/vs-leaf.md#previous-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) |
| Natural spawning | Previous build, 3 runs | Flight tick time avg 27.6 → 26.6 ms (p = 0.084), equal spawn counts per kind | 🤝 | [ticking.md](measurements/ticking.md#natural-spawning-skipped-spawn-cost-lookups) |
| Stop during a pregeneration | Release 26.3-11, 4 stops each | No difference between the builds; one 5.05 s overworld save and one off-main POI trace on the new build | 🤝 | [pregeneration.md](measurements/pregeneration.md#stop-during-a-pregeneration) |
| Async fixes | Release 26.3-11 and previous build | Shared beds 5 and 3 → 0; standing tick time 37.0 → 36.8 ms (p = 0.77) | 🛡️ | [async.md](measurements/async.md#async-pathfinding-tracker-and-saving-after-the-fixes) |
| Loading chunks from disk | The codec path | Same result; parsing 13.9 % → 10.0 % of the worker time in the profile | 🛡️ | [chunks.md](measurements/chunks.md#loading-chunks-from-disk) |
| Copies of unreadable chunks | Build before the patch | Damaged chunk data is copied to `kindling-quarantine/`, nothing else changes | 🛡️ | [chunks.md](measurements/chunks.md#copies-of-unreadable-chunks) |
| Pregenerator hardening | Expected behaviour | Nine functional checks hold; none is a speed measurement | 🛡️ | [pregeneration.md](measurements/pregeneration.md#pregenerator-hardening-checks) |
| Lagging check of the tick loop | Build without the change | Same value; TPS averaging 13.6 % → 3.4 % of the main thread when idle | 🛡️ | [ticking.md](measurements/ticking.md#the-lagging-check-of-the-tick-loop) |
| The doctor's live readings | `/proc` read from outside | Agree within about half a percentage point | 🛡️ | [host.md](measurements/host.md#the-doctors-live-readings) |
| Ten vanilla defaults (Gale limits and intervals, map updates, player movement, async off) | The vanilla code that reads each value | Each equals vanilla's behaviour; speed cost: see the row above | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#defaults-a-fresh-configuration-writes) |
| `Vec3i` hash, goal order, entity-section order (and the other restorations, together) | Release 26.3-12, 3 runs | Bases 11.5 against 11.5 ms (p = 0.80), flight 27.2 against 28.1 ms (p = 0.29); not measured one by one | 🤝 | [vanilla-parity.md](measurements/vanilla-parity.md#hash-goals-and-entity-order) |
| Lightning, cure reputation, failed spawn attempts, brownian walk, tile entity count, shapeless recipes | The vanilla and Paper code | Restored; unit tests for the recipe helper only; no cost in the bench loads (measured together) | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#smaller-restorations) |
| Inside-block order, movement flags, entity age, floating point, game events | The vanilla code | Restored; 6 unit tests; no cost in the bench loads (measured together) | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#entity-movement-age-and-game-events) |
| Biome zoom cell | A copy of vanilla's algorithm, 4 × 10 000 000 random positions | Same cell as vanilla, including a pinned case Leaf got wrong (2 in 200 million) | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#biome-zoom) |
| Async pathfinding targets, trimming, completion (option on) | Vanilla's `PathNavigation` | Four unit tests; villager parity on the new build: no bed or job site claimed twice; standing tick time 37.5 against 40.5 ms with the options on | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#async-pathfinding-when-switched-on) |
| Sleeping hoppers with a move event listener | Release 26.3-12, 3 runs | Bases tick time 11.8 against 11.7 ms (p = 0.63), no measurable effect; parity 15 of 15 counters identical, sleeping on and off | 🤝 | [vanilla-parity.md](measurements/vanilla-parity.md#sleeping-hoppers-with-a-move-event-listener) |
| Parity scenarios of the new build | `bases-ci.json` and the on/off pairs | Bases 15/15, both sleeping pairs 10/10, bases with a listener 15/15, villagers 4/4 | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#parity-scenarios) |
| Entity UUIDs from world generation | Build before the fix, 4 runs each | Mobs refused for a duplicate UUID during pregeneration: 4 in 2 of 4 runs → 0 in 4 runs | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#entity-uuids-from-world-generation) |
| Vanilla diff, contraptions (72 cells, 400 ticks) | Mojang's server jar on one seed, 1 run each | Block differences against vanilla: new build 0, 26.3-12 8 (a redstone wire network unpowered, a repeater), vanilla against vanilla 0; worldgen mode not conclusive | 🛡️ | [vanilla-parity.md](measurements/vanilla-parity.md#vanilla-diff-against-mojangs-jar) |
| Biome zoom shortcut | Setting off, 3 runs | No tick time row differs (flight 27.2 → 27.7 ms, p = 0.49); marked RSS rows read as chance, no memory gain claimed; stays off | 🤝 | [vanilla-parity.md](measurements/vanilla-parity.md#biome-zoom) |
| Worker governor | Setting off | No speed measured; off by default | ⏸️ | [host.md](measurements/host.md#worker-governor) |
| Frontier pregeneration | Setting off | No speed or disk figure measured; off by default; the world folder grows | ⏸️ | [pregeneration.md](measurements/pregeneration.md#frontier-pregeneration) |
| Idle memory | Setting off, 3 runs | Resident memory with nobody online 2 492 → 1 032 MiB (−59 %); off by default | ⏸️ | [memory-jvm.md](measurements/memory-jvm.md#idle-memory) |
| Worker priority | Setting off, 3 runs | Elytra flight tick time 22.5 → 21.2 ms, p95 43.9 → 37.9 ms, main-thread wait −59 %, chunk rate and void unchanged; on by default | 🏆 | [host.md](measurements/host.md#worker-priority-and-the-main-threads-wait-for-a-cpu) |
| Pregenerator wait throttle (0.10) | Setting off, 3 runs | Pregeneration alone 140.3 → 65 chunks/s (−55 %); off by default | ⏸️ | [host.md](measurements/host.md#worker-priority-and-the-main-threads-wait-for-a-cpu) |
| Lazy index map of the per-section ticking list (Paper patch `0075`) | Release 26.3-15, 3 runs | Heap after collection 1 198 → 1 141 MB (−57 MB, p < 0.001); full collections at 2 GB 8 → 8 per run, tick times and CPU unchanged | 🏆 | [memory-jvm.md](measurements/memory-jvm.md#the-ring-of-partly-generated-chunks) |
| Cheaper natural spawn tries (patch `0343`) | Release 26.3-15, 3 runs | Flight tick time avg 37.4 → 37.1 ms (p = 0.47), no tick-time row moves; main-thread wait 4 434 → 3 741 ms and chunk packets +2 % are marked but small | 🤝 | [ticking.md](measurements/ticking.md#cheaper-natural-spawn-tries) |
| JDK 28 early access | Temurin 25.0.5, release 26.3-16, 3 runs | Standing tick time 40.6 → 40.2 ms, bases 11.5 → 11.8 ms, neither marked; standing GC pauses 365 → 283 ms and GC count 99 → 88 marked; counters identical; Paper starts only with `-DPaper.IgnoreJavaVersion=true` | 🤝 | [valhalla.md](measurements/valhalla.md#jdk-28-early-access) |
| `--enable-preview` alone | JDK 28 EA without the flag, same jar, 3 runs | Bases 11.8 → 12.7 ms (+7.6 %, p = 0.010); mixed tick times unchanged; cause not established | 🔻 | [valhalla.md](measurements/valhalla.md#the-preview-flag-alone) |
| Main thread while exploring and while standing (profile) | JFR, the mixed load, 2 runs | Natural spawning 33 % of the main thread in the flight (biome lookup 10 %, mob pick 9 %, placement checks 6 %), 6 % while standing; standing is entity ticks 75 %, no single frame above 3.1 %; the spawner is 7 % of the main thread's allocation, the inside-block check and entity movement 30 % | 🧪 | [ticking.md](measurements/ticking.md#where-the-time-goes-main-thread-profile) |
| Discarded density branches in the chunk workers | Timers at every range choice plus JFR, pregeneration alone, 2 runs | Both branches of every range choice are evaluated: the cave subtree for the 71 % of cell corners above the terrain, noodle caves for the 50 % of blocks without one, the ore vein chain outside each type's y range; 103 s certainly wasted of 1 409 worker-seconds (7.3 %), 135 s at most (9.6 %) | 🧪 | [pregeneration.md](measurements/pregeneration.md#discarded-density-branches) |
| The ring of partly generated chunks (heap) | Live histograms and a chunk census, the mixed load, 1 and 3 runs | 82 % of 424 128 overworld sections hold only air, 9 204 of 17 672 chunks have no block data at all; the sections' fixed overhead is 229 to 251 MB (20 to 22 %) of a 1.06 to 1.23 GB live heap, 82 to 94 MB of it one eager hash map per section | 🧪 | [memory-jvm.md](measurements/memory-jvm.md#the-ring-of-partly-generated-chunks) |
| Pregenerator in tiles | Kindling 26.3-1 (ring by ring), 2 runs | Chunks generated 19 040 → 30 315 (+59 %), CPU per chunk 47 → 28 ms (−41 %) | 🧪 | [pregeneration.md](measurements/pregeneration.md#pregeneration) |
| Directional chunk loading | Same build without it, 2 runs | Time over void 9 % → 0 % at the same CPU | 🧪 | [chunks.md](measurements/chunks.md#elytra-test-new-chunks-ahead-of-fast-players) |
| Chunk workers | Paper's default (3 workers on 12 cores), 2 runs | Time over void 24 % → 0 % with 5 workers | 🧪 | [chunks.md](measurements/chunks.md#default-number-of-chunk-workers) |
| Cap on concurrent generations | Paper's automatic limit (88), 2 runs | Time over void 31.5 % → 1.2 % with a cap of 32 | 🧪 | [chunks.md](measurements/chunks.md#concurrent-generations-per-player) |
| Mixed load, Kindling defaults | Leaf `4c3d5689` with a 3 GB heap, 2 runs | Peak RSS 3 590 → 2 555 MB; the tick times are not a finding | 🧪 | [vs-leaf.md](measurements/vs-leaf.md#mixed-load-leaf-against-the-kindling-defaults) |
| Leaf's options one by one | Kindling defaults, 2 runs | No option is both faster and the same behaviour | 🧪 | [vs-leaf.md](measurements/vs-leaf.md#leafs-options-one-by-one) |
| Sleeping block entities, first measurement | Kindling 26.3-4 with the option off, 2 runs | Bases tick time avg 9.3 → 8.0 ms; re-measured with five runs | 🧪 | [ticking.md](measurements/ticking.md#survival-bases-sleeping-block-entities) |
| JVM and heap, first measurement | Leaf with a 3 GB heap, 2 runs | Peak RSS 3 590 → 2 549 MB (−29 %) at the same tick time; did not count full collections, superseded by the 3 GB row above | 🧪 | [memory-jvm.md](measurements/memory-jvm.md#jvm-and-heap) |
| AOT cache | Start without the cache, one start each | billiards 9.5 to 10.7 s instead of 16.9 to 26.9 s | 🧪 | [memory-jvm.md](measurements/memory-jvm.md#start-time-and-native-memory-cubyx-vps) |
| Waiting for a CPU on the VPS | The thread's own running time, one sample | Main thread ran 326 s, waited 92 s (+28 %) | 🧪 | [host.md](measurements/host.md#waiting-for-a-cpu-on-the-vps) |
| `Vec3`, `AABB` and `ChunkPos` as value classes (Valhalla, JEP 401) | Same sources without the change, JDK 28 EA with `--enable-preview`, 3 runs | Main-thread allocation while standing 12.6 → 9.2 GB (−27 %, p < 0.001); tick times, GC pauses and full collections unchanged; counters identical; not adopted, preview only | ❌ | [valhalla.md](measurements/valhalla.md#vec3-aabb-and-chunkpos-as-value-classes) |
| Client-paced chunk sending | Setting off, 3 runs | A fast client gets its view no sooner; a slow client joins 6 325 → 12 881 ms; dropped | ❌ | [chunks.md](measurements/chunks.md#client-paced-chunk-sending) |
| Ten other Leaf options | Mixed load table, 2 runs | None adopted: slower, no effect, or not vanilla behaviour | ❌ | [vs-leaf.md](measurements/vs-leaf.md#not-adopted-although-leaf-offers-them) |
| Three ideas looked at | Profile shares | Left alone: the gain was too small for the risk | ❌ | [ticking.md](measurements/ticking.md#looked-at-and-left-alone) |

## Topics

- [Kindling against Leaf](measurements/vs-leaf.md): the campaign against Paper and plain Leaf, the round against release 26.3-12, the previous round against 26.3-11, the older mixed load, and Leaf's options one by one.
- [Chunk generation and loading](measurements/chunks.md): directional loading, chunk workers, the generation cap, the palette decoder, copies of unreadable chunks and the dropped client-paced sending.
- [Pregeneration](measurements/pregeneration.md): the tiled pregenerator, its hardening checks, the stop during a run, frontier pregeneration and the chunk workers' profile.
- [Vanilla parity](measurements/vanilla-parity.md): what Kindling restores where Leaf or Gale changed vanilla, the defaults of a fresh configuration, the parity scenarios, the vanilla diff against Mojang's jar, and what still differs.
- [World ticking](measurements/ticking.md): sleeping block entities, Lithium ports, natural spawning, the lagging check, the main thread's profile and what was left alone.
- [Async defaults](measurements/async.md): villager parity and the cost of the fixes for async pathfinding, tracker and saving.
- [JVM, start time and memory](measurements/memory-jvm.md): JVM flags and heap, AOT cache and native heap trim, idle memory.
- [JDK 28 and value classes](measurements/valhalla.md): JDK 28 early access against JDK 25, the cost of `--enable-preview`, and `Vec3`, `AABB` and `ChunkPos` as value classes (not adopted).
- [The host: waiting for a CPU](measurements/host.md): run-queue waits on the VPS, worker priority, the pregenerator's wait throttle, worker demand, the worker governor and the doctor's live readings.

## How to read the numbers

**The rule (since 2026-10-28).** A variant runs at least three times; `compare.py` prints `mean ±half range nN` (half range: half the gap between the highest and the lowest run) and the p-value of Welch's t-test against the first label. A difference counts as real only if it is marked `*`: p < 0.05 **and** the means are more than twice the larger half range apart. Before, a difference counted when it was larger than the gap between two runs; with two runs that is about p = 0.29, so a claim such as "−3 %" could not be told from noise. A table on these pages marked "two runs" meets the old rule only. Tables with three or more runs name the p-value; a row without `*` is not a finding, even when it points in a direction. With many rows per comparison, one row at p < 0.05 can be chance; these pages only quote such a row next to the others it came from.

<details>
<summary><b>What is compared with what</b></summary>


- *Against plain Leaf:* the [final round](measurements/vs-leaf.md#final-round-the-vanilla-build-of-2026-10-29-against-263-12-and-plain-leaf) compares Kindling with plain Leaf at the same base commit, each with its own defaults. That is the comparison the README and the website lead with. Older tables name the Leaf commit they used.
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
> The numbers up to 2026-10-27 come from `kindling-bench` ([how it works](../kindling-bench/README.md)), measured on a Windows machine with 12 logical cores and Java 25.0.4 (the mixed load, the elytra test, the worker counts) or on the bench host (the sections that say so), against Leaf `4c3d5689` (Paper `1b9bf5c8`). **Those variants ran twice; tables show the mean.** Two runs do not meet the rule that applies since 2026-10-28 ([how to read the numbers](#how-to-read-the-numbers)); where a claim rests on two runs, its section says so. The sections from 2026-10-28 ran three or more times per variant; the [final round of 2026-10-29](measurements/vs-leaf.md#final-round-the-vanilla-build-of-2026-10-29-against-263-12-and-plain-leaf) and the [previous round](measurements/vs-leaf.md#previous-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) compared against Leaf `66f99f8e`, the commit Kindling is now based on. A shared-core VPS will show other absolute numbers; the direction should hold.
>
> **Bench host:** a PC with an Intel i7-7700 (4 cores, 8 threads), 16 GB, Linux (Ubuntu 24.04 under WSL2), Temurin 25.0.5, G1. Unless a section says otherwise, its server ran with `-XX:+UseCompactObjectHeaders -Xms2G -Xmx2G` and 8 CPUs.
>
> **Known disturbance:** from 2026-10-27 afternoon until 2026-10-29 04:23 two orphaned SSH session processes on the bench host's Windows side kept about two of its eight threads busy. Every run in that time had the same extra load, and the variants ran interleaved, so the comparisons between them hold; the absolute idle shares and main-thread waits of those rounds are pessimistic.
