# Measurements

Why each Kindling change exists and what it measured. One line per change, grouped by topic; each topic page has the method and all numbers.

## How to read the numbers

- Every figure comes from [`kindling-bench`](../kindling-bench/README.md) unless its page says otherwise.
- A variant runs at least three times. `compare.py` marks a difference `*` when p < 0.05 (Welch's t-test) **and** the means are more than twice the larger half range apart. Only a marked difference counts.
- Results with fewer runs, or from a single profile, are labelled **Indicative**. They point somewhere; they are not findings.
- The baseline is named on every line: plain Leaf at a commit, the same build with the setting off, or the release before the change. Plain Leaf has no pregenerator, so pregenerator results compare Kindling builds.
- Flight tick times compare only at the same share of time over ungenerated void: a player over void has nothing to load, tick or send.

Result words: **Faster**, **Same**, **Slower**, **Correct** (a correctness check, no speed claim), **Indicative**, **Not measured**, **Dropped**. Definitions: [STYLE.md](STYLE.md#result-words).

## Bench host

A PC with an Intel i7-7700 (4 cores, 8 threads) and 16 GB, Ubuntu 24.04 under WSL2, Temurin 25.0.5, G1. Unless a page says otherwise, the server runs with `-XX:+UseCompactObjectHeaders`, a 2 GB heap and 8 CPUs. A shared-core VPS shows other absolute numbers; the direction should hold. The pages marked **Indicative** with "12 cores" ran on an older Windows machine.

Some rounds before release 26.3-14 ran while two stray processes kept about two of the host's eight threads busy. The variants of a round ran interleaved, so comparisons within a round hold; absolute idle shares and main-thread waits from those rounds are pessimistic.

The time over void was first measured on one line straight ahead of each player; the bench now also follows four more lines and the player's own chunk. Figures from the single line say so on their page.

## Kindling against Paper and Leaf

[vs-leaf.md](measurements/vs-leaf.md): Paper build 223, plain Leaf `66f99f8e` and Kindling with their own defaults, 3 runs each.

| Change | Result |
| --- | --- |
| [New terrain](measurements/vs-leaf.md#paper-plain-leaf-and-kindling) | **Faster.** 2.5 to 3.6 times the new chunks per second of Leaf; mixed flight over void 82 % → 0 %; join until the full view 50.1 → 27.2 s; flight tick time p95 130 → 60 ms |
| [Ticking without new terrain](measurements/vs-leaf.md#paper-plain-leaf-and-kindling) | **Faster.** Bases 11.8 → 10.8 ms, 1 200 standing mobs 39.1 → 34.6 ms; villagers **Same** (8.5 → 8.4 ms) |
| [Pregenerated world](measurements/host.md#worker-demand) | **Slower.** Flight tick time 31.7 → 33.3 ms (+5 %) at Leaf's CPU time |
| [CPU time](measurements/vs-leaf.md#paper-plain-leaf-and-kindling) | **Slower.** 8 to 83 % more, most of it in the chunk workers; bases +13 % |
| [Async pathfinding and mob spawning](measurements/vs-leaf.md#async-pathfinding-and-mob-spawning-opt-in) | **Faster**, opt-in. 1 200 standing mobs 34.9 → 31.8 ms with them on; not vanilla, so off by default |
| [Leaf's options](measurements/vs-leaf.md#leafs-options-one-by-one) | **Indicative.** No option is both faster and vanilla; [ten not adopted](measurements/vs-leaf.md#not-adopted-although-leaf-offers-them) |

## Chunk generation and loading

[chunks.md](measurements/chunks.md)

| Change | Result |
| --- | --- |
| [Directional chunk loading](measurements/chunks.md#directional-chunk-loading) | **Indicative.** Time over void 9 % → 0 % at the same CPU, against the same build without it |
| [Default number of chunk workers](measurements/chunks.md#default-number-of-chunk-workers) | **Indicative.** Time over void 24 % → 0 %, 5 workers against Paper's 3 |
| [Concurrent generations per player](measurements/chunks.md#concurrent-generations-per-player) | **Indicative.** Time over void 31.5 % → 1.2 %, cap 32 against Paper's 88 |
| [Compiled material rules](measurements/chunks.md#compiled-material-rules) | **Faster.** +8.4 % chunks in a pregeneration at the same CPU time; 0 differences in 1.3 billion blocks |
| [Compiled density](measurements/chunks.md#compiled-density) | **Faster.** +0.8 % new chunks per second; 0 differences in 40 000 calls |
| [Loading chunks from disk](measurements/chunks.md#loading-chunks-from-disk) | **Correct.** Same result as the codec path; less worker time |
| [Copies of unreadable chunks](measurements/chunks.md#copies-of-unreadable-chunks) | **Correct.** Damaged chunk data is copied to `kindling-quarantine/` |
| [Client-paced chunk sending](measurements/chunks.md#client-paced-chunk-sending) | **Dropped.** No faster view for fast clients; slow clients join twice as slowly |

## The host

[host.md](measurements/host.md)

| Change | Result |
| --- | --- |
| [Worker demand](measurements/host.md#worker-demand) | **Faster.** Pregenerated flight 35.6 → 33.3 ms, CPU time 513 → 477 s, against five fixed workers |
| [Worker priority](measurements/host.md#worker-priority) | **Faster.** Elytra flight p95 43.9 → 37.9 ms, main-thread wait −59 %, same chunk rate |
| [Pregenerator wait throttle](measurements/host.md#pregenerator-wait-throttle) | **Slower**, opt-in. At 0.10 pregeneration falls from 140 to 65 chunks/s |
| [Worker governor](measurements/host.md#worker-governor) | **Indicative**, opt-in. Elytra tick time 16.9 ms but chunk rate −11 %, one series |
| [The doctor's live readings](measurements/host.md#the-doctors-live-readings) | **Correct.** Within half a percentage point of `/proc` |
| [Waiting for a CPU on a VPS](measurements/host.md#waiting-for-a-cpu-on-the-vps) | **Indicative.** The main thread waited 28 % of its running time |

## Pregeneration

[pregeneration.md](measurements/pregeneration.md)

| Change | Result |
| --- | --- |
| [The tiled pregenerator](measurements/pregeneration.md#the-tiled-pregenerator) | **Indicative.** +59 % chunks, −41 % CPU per chunk, against its first version |
| [Hardening checks](measurements/pregeneration.md#hardening-checks) | **Correct.** Nine functional checks hold |
| [Stop during a pregeneration](measurements/pregeneration.md#stop-during-a-pregeneration) | **Same** as release 26.3-11 |
| [Frontier pregeneration](measurements/pregeneration.md#frontier-pregeneration) | **Not measured**, opt-in |
| [Chunk worker profile](measurements/pregeneration.md#where-the-time-goes-in-the-chunk-workers) | **Indicative.** 7 to 10 % of worker CPU went to discarded density branches |

## World ticking

[ticking.md](measurements/ticking.md)

| Change | Result |
| --- | --- |
| [Sleeping block entities](measurements/ticking.md#sleeping-block-entities) | **Faster.** Bases 12.3 → 11.3 ms (−8 %) with identical results, on against off |
| [Lithium ports](measurements/ticking.md#lithium-ports) | **Faster.** Bases 12.0 → 11.2 ms against the previous build |
| [Skipped spawn cost lookups](measurements/ticking.md#natural-spawning-skipped-spawn-cost-lookups) | **Same.** Equal spawn counts |
| [Cheaper natural spawn tries](measurements/ticking.md#cheaper-natural-spawn-tries) | **Same.** Flight 37.4 → 37.1 ms |
| [Lagging check of the tick loop](measurements/ticking.md#the-lagging-check-of-the-tick-loop) | **Correct.** Same value at a quarter of the cost |
| [Main thread profile](measurements/ticking.md#where-the-time-goes-main-thread) | **Indicative.** Natural spawning is a third of the main thread while players explore |
| [Ideas left alone](measurements/ticking.md#looked-at-and-left-alone) | **Dropped** |

## Vanilla parity

[vanilla-parity.md](measurements/vanilla-parity.md). What each restoration changes: [Vanilla differences](reference/vanilla-differences.md).

| Change | Result |
| --- | --- |
| [Vanilla defaults of a fresh configuration](measurements/vanilla-parity.md#vanilla-defaults-of-a-fresh-configuration) | **Slower.** 1 200 standing mobs 36.6 → 39.7 ms (+8 %), likely the async options being off (not proven: p = 0.051); bases and flight unchanged |
| [Restorations, measured together](measurements/vanilla-parity.md#restorations-measured-together) | **Same.** Bases and flight unchanged against release 26.3-12 |
| [Biome zoom](measurements/vanilla-parity.md#biome-zoom) | **Correct.** Exactly vanilla's cell; the opt-in shortcut is **Same** |
| [Async pathfinding, when on](measurements/vanilla-parity.md#async-pathfinding-when-switched-on) | **Correct.** Follows vanilla's `PathNavigation` |
| [Async fixes](measurements/vanilla-parity.md#async-fixes-for-pathfinding-tracker-and-saving) | **Correct.** Shared villager beds 5 and 3 → 0 |
| [Sleeping hoppers with a move event listener](measurements/vanilla-parity.md#sleeping-hoppers-with-a-move-event-listener) | **Same.** No measurable cost |
| [Parity scenarios](measurements/vanilla-parity.md#parity-scenarios) | **Correct.** All four pass |
| [Vanilla diff against Mojang's jar](measurements/vanilla-parity.md#vanilla-diff-against-mojangs-jar) | **Correct.** 0 block differences in 72 contraption cells |
| [Entity UUIDs from world generation](measurements/vanilla-parity.md#entity-uuids-from-world-generation) | **Correct.** No more refused mobs |

## JVM and memory

[memory-jvm.md](measurements/memory-jvm.md)

| Change | Result |
| --- | --- |
| [3 GB heap instead of 2 GB](measurements/memory-jvm.md#jvm-and-heap) | **Faster.** Full collections 5–6 → 2 per run, CPU −3.8 %, 1 GB more memory |
| [G1 flags for a 2 GB heap](measurements/memory-jvm.md#jvm-and-heap) | **Trade-off.** Full collections 6–7 → 0 per run, GC pauses 8.4 → 5.4 s; CPU +5 % and standing tick time 35.8 → 37.3 ms |
| [Lazy ticking-list map](measurements/memory-jvm.md#the-lazy-ticking-list-map) | **Faster.** Live heap −57 MB |
| [Partly generated chunks in memory](measurements/memory-jvm.md#the-ring-of-partly-generated-chunks) | **Indicative.** A fifth of the live heap is chunk sections, 82 % of them only air |
| [Idle memory](measurements/memory-jvm.md#idle-memory) | **Faster**, opt-in. Resident memory with nobody online 2 492 → 1 032 MiB |
| [AOT cache](measurements/memory-jvm.md#aot-cache-and-native-memory) | **Indicative.** Start 9.5 to 10.7 s instead of 16.9 to 26.9 s |
| [JDK 28 early access](measurements/memory-jvm.md#jdk-28-early-access) | **Same** as JDK 25 |
| [The preview flag](measurements/memory-jvm.md#the-preview-flag) | **Slower.** Bases +7.6 % |
| [Value classes](measurements/memory-jvm.md#vec3-aabb-and-chunkpos-as-value-classes) | **Dropped.** −27 % allocation, no tick time gain, preview only |
