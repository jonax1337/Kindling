# 🧭 Kindling roadmap

What is still open and what was dropped. Kindling has one maintainer, so an item stays on this list only if its value clearly outweighs the upkeep it adds on every Leaf merge and Minecraft update (scored 1–10 in an independent review on 2026-10-27; 7.5 or more stays).

What is done, release by release: [CHANGELOG.md](../CHANGELOG.md). Why each change exists and what it measured: [MEASUREMENTS.md](MEASUREMENTS.md).

**Contents:** [Rules](#rules-for-every-item) · [Open](#-open) · [Dropped](#-dropped)

## Rules for every item

1. **Vanilla-identical by default** ([AGENTS.md](../AGENTS.md), ground rule 1).
2. **Measured before it becomes a default:** `kindling-bench` before and after, three runs each, a difference counts only if `compare.py` marks it `*` (p < 0.05 and a gap of more than twice the larger half range; [how to read the numbers](MEASUREMENTS.md#how-to-read-the-numbers)). Changes that must not change behaviour also pass the parity scenarios.
3. **No new patch where a setting, a plugin or upstream does the job.** Every Kindling patch is rebased on every Leaf merge.
4. **Bugs go upstream, features stay.** A fix for something that is broken in plain Leaf too is a candidate for a Leaf pull request (like Leaf#970); the owner decided on 2026-10-28 to send none for now, so the fixes are listed in [UPSTREAM.md](UPSTREAM.md#fixes-leaf-could-take). Kindling's own features stay in Kindling.

## 🔜 Open

New items need the 7.5 bar of the review.

**Defaults to decide**

| Open item | Why it is open |
| --- | --- |
| The pregenerated world against Leaf | With worker demand Kindling still ticks 5 % slower than plain Leaf there (31.7 → 33.3 ms) at the same CPU time; its main thread waits twice as long for a CPU (149 → 311 ms) and it sends 5 % more chunk packets. With Paper's two workers fixed it ticked like Leaf, so the likely rest is the time five workers run at the start and after each burst; not established. The mixed flight ticked slower with worker demand in all three rounds (33.4 against 30.9 ms, not significant) ([worker demand](measurements/host.md#worker-demand)) |
| A usable criterion for the pregenerator's wait throttle | A share of the last minute fires at baseline on the VPS (a nearly idle thread waits 10 to 14 %) and at 0.10 stops pregeneration during a flight. Candidates: the absolute wait per tick, a higher limit such as 0.20 (untested), or throttling only while the machine is busy. The doctor's contention warning already uses the absolute wait (at least 20 ms per second) |
| A documented "fast" profile | Async pathfinding and async mob spawning on make standing among 1 200 mobs 9 % faster (34.9 → 31.8 ms, 3 runs, marked; Kindling is already faster than Leaf without them). Whether a profile with them on belongs in the README and the doctor, given that a mob follows a path a tick or more late and spawn counts can be stale (not vanilla, [ground rule 1](../AGENTS.md)) |

**Measurements still missing**

| Open item | Why it is open |
| --- | --- |
| Comparisons with plain Leaf that are missing | The pregenerator was only measured against its own first version; Chunky on Leaf `66f99f8e` would be the fair baseline. The elytra test at 1.7 blocks/tick last ran against Leaf `4c3d5689` on the Windows machine with two runs |
| Standing against plain Leaf on a pregenerated world | In the final round plain Leaf was still generating while the players stood (its three runs spread ±5.4 ms, 47.9 against 39.7 ms is not significant), so the standing figure against Leaf is not a measure of the defaults |
| Kindling's CPU time in the bases | +13 % against plain Leaf with worker demand (276 → 313 s); with Paper's two workers fixed it was Leaf's (281.5 against 280.0 s). The bases generate only around the spawn at the start, so the likely cost is that area generated with every worker; not established ([worker demand](measurements/host.md#worker-demand)) |
| What offsets the Lithium gain in the bases | The Lithium ports alone measured −7 %, yet the final build runs the bases at the release's 11.5 ms. The hopper cooldown after a failed transfer is the only later change to what the bases tick; an A/B of it alone would settle it ([world ticking](measurements/ticking.md)) |
| What offsets the restore patches | The restored vanilla code (hash, goal order, entity order, per-tick in-wall and line-of-sight checks) costs at most about 1 ms standing among 1 200 mobs and nothing visible in the bases or the flight, measured only together. Which restoration costs what, and whether a vanilla-identical cheaper version exists, would need one A/B per restoration |
| Memory return and a rejoin | The tick spike when players come back after the heap shrank was not measured; the bench needs a rejoin mode |
| Repeat the "0 % void" test with five lines | The figures of 2026-10-26 followed one line straight ahead, and the bench host spent about half of an elytra flight in the void |

**Candidates from the code review of 2026-10-29** (read in the applied 26.3 sources and measured the same evening with a profiling build, nothing built as a patch yet). The review looked for a single hidden flaw that would make the server many times faster and found none: a tenfold gain would mean dropping 90 % of all work, and the largest piece of discarded work it could prove is the density branches below. Kindling's structural win, 85.5 % → 1.5 % of a flight over the void, is behind it; what remains are the percentages in this table. The measured shares are in [ticking.md](measurements/ticking.md#where-the-time-goes-main-thread-profile), [pregeneration.md](measurements/pregeneration.md#discarded-density-branches) and [memory-jvm.md](measurements/memory-jvm.md#the-ring-of-partly-generated-chunks); what each patch gains is still an expectation until its own A/B.

| Open item | Why it is open |
| --- | --- |
| 1. Small exact spawner costs | Done as patch `0343` and measured (🤝, [ticking.md](measurements/ticking.md#cheaper-natural-spawn-tries)): no tick-time row moves, so the 2 to 4 % expected from the allocation profile did not show; stays because it is exact |
| 2. Spawn pre-checks in parallel, commit in order | Built and taken out again: slower and not exact ([Dropped](#decided-against-while-building)) |
| 3. Discarded branches of the density compiler | Measured: `RangeChoiceFunction.Sampler.sampleVolume` evaluates both branches for the whole volume, and 103 s of 1 409 worker-seconds of a pregeneration are certainly spent on the branch that is thrown away (7.3 %; 9.6 % at most): the cave subtree for the 71 % of cell corners above the terrain (33 s), the noodle cave interpolation for the half of the blocks whose toggle is negative (30 s), and the ore vein chain outside each type's y range and the toggle's dead band (about 40 s). Three different fixes: the caves need a branch evaluated only where chosen, which is exact (no interpolation inside the subtree) but needs the compiler's volume-keyed caches to serve sub-volumes; the noodle and ore vein choices run at block resolution on interpolated inputs and can skip the interpolation per point. Prerequisite for all three: the worldgen vanilla diff with one worker thread (below), because the change is only acceptable bit for bit |
| 4. Empty sections of the generation ring | Measured: the 26.3 `ChunkPyramid` keeps 9 204 of 17 672 chunks at `STRUCTURE_STARTS` or `BIOMES` without any block data, 82 % of all 424 128 overworld sections hold only air, and the fixed overhead of the sections (two `PalettedContainer`s with data, palette and bit storage, a `ShortList` with its hash map and arrays, the nibble arrays) is 229 to 251 MB of a 1.06 to 1.23 GB live heap, 427 B per section. A shared empty section state for chunks below `BIOMES`, materialised on the first write, would take most of it back; `ChunkLoadTask` also parses the whole chunk NBT for a `STRUCTURE_STARTS` target, to be looked at afterwards |
| 5. The eager hash map in every section's ticking list | Done as Paper patch `0075` and measured (🏆, [memory-jvm.md](measurements/memory-jvm.md#the-ring-of-partly-generated-chunks)): heap after collection −57 MB (p < 0.001), but the full collections at 2 GB stayed at 8 per run, so the 2 GB freezes are not a question of old-generation size; item 4 is the remaining memory lever |

**Built, not measured yet** (each needs three runs per side in `kindling-bench`, `compare.py` marking the gap `*`; the vanilla restorations and the biome zoom shortcut were measured on 2026-10-29 and are out of this list)

| Open item | Why it is open |
| --- | --- |
| `performance.worker-governor` | Off by default, Linux only. Whether it lowers the main thread's wait on a crowded host, what it costs in generation speed, and whether 2 samples down and 60 s up are good values are all unknown. Also untried on a live pool: a `/paper reload` while it holds a lower count |
| `performance.pregeneration.frontier` | Off by default. Throughput against `/pregen` (a tile's in-flight chunks drain at each tile boundary), the real disk growth per visited tile, and a run on a server with players coming and going. The disk forecast counts the visited tiles' chunks as still to be written, so it is conservative |
| Worldgen vanilla diff with one worker thread | The worldgen mode is not conclusive: vanilla against vanilla already differs in 410 of 624 chunks because feature placement across chunk borders depends on worker scheduling. Run both sides with a single chunk worker thread; then the 6 biome cells that differ between the new build and vanilla can be explained or cleared |
| Entity-parity and parity fixes in the game | Hopper pull order with several items, pressure plate and tripwire order for falling entities, a plugin's `BlockReceiveGameEvent` handler placing a sculk sensor, a client-steered vehicle standing still, lightning frequency, the cure discount, a border-clipped automatic pregeneration run across a restart, a full disk that frees up while the server sleeps |

**Tests and parity**

| Open item | Why it is open |
| --- | --- |
| A server test of the async player data saving | The write ordering, the flush barrier and the shutdown drain have unit tests only. `save-all flush` followed by a look at the file times, and a stop on a slow disk, need a real server |
| Scenarios for four late fixes | The removed nitwit shortcut, the happy ghast's adult sensor, the idle-memory pause during a pregeneration and the async writer's publication rest on unit tests and review; the final round's scenarios exercise none of them |
| A scenario that shows the furnace fix and shared job sites | Both bugs did not reproduce on the release, so their fixes have no counter |
| Parity in CI | `parity.yml` runs the bases check only; the sleeping and villager pairs run by hand (`parity/run.sh <jar> all`) |
| The shutdown stack trace | "Accessing poi chunk off-main" during `stop` while a pregeneration ran: once in 8 stops (new build 1 of 4, release 0 of 4), and the stack holds only vanilla, Paper and Moonrise code. Whether the skipped POI update is repaired on the next load is not checked; a candidate for an upstream report ([pregeneration measurements](measurements/pregeneration.md)) |

**Known limits**

| Open item | Why it is open |
| --- | --- |
| Partly generated chunks in memory | Under load 63 % of the chunk objects (11 181 of 17 672) are neighbours generated only part of the way, the ring vanilla's chunk levels keep around each player; how much of the heap they hold was not measured. Unloading them early would change the chunk lifecycle that plugins and the chunk system rely on; not attempted ([memory measurements](measurements/memory-jvm.md#jvm-and-heap)) |
| Player#saveData and Server#savePlayers | They return before the asynchronous write lands (Leaf's behaviour); server-side reads wait for it, outside readers of the file may see stale data until the next flush. Waiting would need a Paper-server change |

## ❌ Dropped

Recorded so they are not proposed again without new facts.

### Decided against while building

| Item | Reason |
| --- | --- |
| Trimming the world by `InhabitedTime` | Conflicts with pregeneration: it would delete exactly the chunks the pregenerator is made to create |
| A pre-upgrade command | Not needed: `--forceUpgrade` still exists in 26.3 |
| Trimming the native heap while players are online | The diagnostic command runs in the calling thread's VM state and can delay safepoints. Kindling trims only while nobody is online (`idle-memory`); the doctor points at `-XX:TrimNativeHeapInterval` for those who want the JDK's own periodic trim |
| Leaf pull request #928 (container contents decoded when opened) | Left to Leaf; Kindling carries no copy |
| Leaf pull request #943 beyond the correctness part | Not ported: the MC-157589 behaviour change, a radix sort, the pathfinding pool size and its keepalive default |
| Leaf pull request #952 (hopper rates) | Not ported; the tuned rates are honoured by taking Paper's transfer code when `hopper-amount` is not 1, with the cooldowns derived from `ticks-per.hopper-transfer` |
| Making hoppers sleep with `ticks-per.hopper-check` above 1 | A woken sleeper cannot keep Paper's check interval, so these hoppers stay awake |
| Lithium's nitwit job-search shortcut | Taken out again: it skipped calls to the level's random, so it was not vanilla-identical |
| Quarantine for entities and POI data | Only chunk data is copied. (Chunks the region file gives up on are covered since patch `0340`) |
| Reusing a built chunk packet for several players | Evaluated, not built: the packet cannot be invalidated safely. A spawner's `Delay` changes the block entity update tag without a notification, light changes arrive from neighbouring chunks on light threads, and Paper's Anti-Xray and plugin packet listeners change a packet's buffer and lists in place. A narrower cache of the section bytes only, keyed by modification counters on `PalettedContainer`, could be exact but would need a profile showing section serialisation is a large share of `PlayerChunkSender.sendChunk` first |
| A quarantine copy of a chunk saved by a newer Minecraft version | Paper stops the server first (verified), so nothing is lost |
| `SCHED_IDLE` for pregeneration | Not attempted: a priority inversion with synchronous chunk loads is a risk, and `nice` was measured first |
| Direction smoothing in directional loading | Not added: samples from the first ticks and jumps are ignored instead, which keeps the order logic small |
| `gettid` through `syscall` | `worker-priority` uses the C library's `gettid` (glibc 2.30, musl 1.2.2); an older library logs it once and the workers keep their priority |
| Showing the furnace and job-site bugs in a scenario | Neither reproduced on the release, so no counter proves their fixes |
| Client-paced chunk sending (built as `0328`, taken out again) | Measured on the bench host: fast clients got their full view no sooner, slow clients took twice as long to join, clients that never answer 4.9 s longer, and no tick time gain ([chunk measurements](measurements/chunks.md)) |
| Spawn pre-checks in parallel, commit in order (review candidate 2, built as `0344`, taken out again) | Helper threads recorded each spawn try's mob pick and placement checks, and the main thread replayed vanilla's loop with them. Measured on the bench host against the same code without it (mixed load, 2 GB heap, two helper threads, three interleaved runs each): flight tick 36.7 → 42.9 ms, its p95 61.4 → 72.9 ms, standing 35.9 → 39.6 ms, CPU time 1 234 → 1 314 s, all `*`. Not exact either: in a verification run 10 025 of 7 922 536 recorded picks (0.13 %) differed from the main thread's, because the biome read on a helper thread saw another biome (mostly lukewarm ocean where the main thread reads river, sulfur caves or mushroom fields), and 25 of 37 823 303 placement checks differed |
| `Vec3`, `AABB` and `ChunkPos` as value classes (Valhalla, JEP 401; built outside the patches, measured, not taken) | Main-thread allocation while standing −27 %, but no tick time, GC pause or full-collection row moved, and it needs JDK 28 with `--enable-preview`, which alone costs 7.6 % in the bases. Plugins that use these objects' identity would break. Worth measuring again once value classes leave preview ([JDK 28 and value classes](measurements/valhalla.md)) |

### Reviewed and not taken

Scores from the 2026-10-27 review; DivineMC checked at `219de55` (`ver/26.3`).

**Already in Leaf:** Lithium equipment tracking (Leaf 0229), sheep (0054), in-wall/suffocation checks (0018), BFS slope search for fluids (0181), parallel world ticking, the multithreaded tracker, Secure Seed, DAB, Anti-Xray, `B_LINEAR` region files.

**Already in vanilla 26.3:** a density function compiler of its own (`levelgen/densityfunction/DensityFunctionCompiler`: rewrite rules, cache reuse, buffered samplers). This is why C2ME's compiler and noise optimisations no longer apply, and why DivineMC dropped them for 26.3. It is not a reason to treat the worldgen compiler as finished: Mojang's compiler has gaps of its own, such as evaluating both branches of a range choice for the whole volume (open item 3 above).

**Dead code on a Paper server:** C2ME NBT cache limit (Paper's chunk system bypasses `IOWorker`'s write queue; the patch also has a `Math.min(1, …)` bug), ModernFix compact bit storage (only on the client's packet read path).

**Not vanilla-identical, or not worth the upkeep:**

| Item | Score | Reason |
| --- | --- | --- |
| C2ME aquifer (DivineMC 0047) | 4.5 | aquifers are 7 % of chunk worker time, so a realistic gain is below the bench's noise; rewrites the core `Aquifer` method; not bit-exact (literal `1.2` vs vanilla's `Mth.map` result) |
| C2ME world gen math (0009), End biome cache (0054) | 1.0 / 2.5 | one hoisted `Math.min`; the End cache ignores Y |
| StructureLayoutOptimizer (0028) | 4.0 | large, jigsaw assembly does not show in the pregen profile; mainly helps modpacks |
| Native math (0066) | 1.5 | not seed-identical (DivineMC's own warning), native binaries in CI |
| Optimise entities, fluids, raids, Moonrise (0013, 0027, 0025, 0058) | 1.5–3.5 | change ordering or timing, or collide with Leaf's own patches |
| Command block parse cache, async `/locate`, chunk lookups (0033, 0010, 0022) | 2.0 | stale command source; `/locate` returns 0; negligible gain |
| Player profile result cache (0034) | 0.5 | caches Mojang's `hasJoinedServer` by name only, so a cached name logs in without authentication |
| Portal search prefetch, chunk worker presets (Paper 0019) | 4.5 / 2.0 | no measured hitch; the presets give about 2 workers on a 2 GB heap, already measured as too few |
| Async chunk sending (0032) | 3.0 | skipped whenever Anti-Xray modifies the chunk; packet building is not in the measured profile |
| Parallel sensors (0067), parallel entity tracker (0038) | 2.5 / 2.0 | sensors see the world before other mobs move; the tracker duplicates Leaf's |
| Regionized chunk ticking (0045 + Paper 0020) | 1.0 | 20 core classes plus a 33 KB scheduler, redstone and block events in parallel; not maintainable by one person |
| Behaviour options: entity culling, lag compensation, update-suppressor catch, XP clumping, 1-gt delays, MC-73719, spawn tracking range, spectator packet, leaf decay | 1.0–5.5 | plugin-type features or already covered by Spigot settings |
| Lag spike recorder, `/kindling hotspots`, metrics endpoint | 6.5 / 6.0 / 5.0 | spark and exporter plugins already cover them; hotspots needs hooks in hot, heavily patched tick loops |
| AOT from the jar, profiles, join prefetch | 6.0 / 5.0 / 4.0 | a documented start command covers AOT; profiles write keys across six config systems; join prefetch is unmeasured and collides with Leaf |
| Lithium framed maps, inactive navigations | — | Framed maps: Lithium's mixin no longer prunes removed players and no longer removes the cursors of players with a map invisibility item when a framed map ticks, and 26.3 already skips the inventory scan it targets, so only the player name is built lazily. Inactive navigations: needs hooks wherever a mob's navigation or vehicle changes, next to Kindling's async path code, in the hot `ServerLevel.sendBlockUpdated`; identical behaviour cannot be shown |
| Sakura explosions | 2.0 | no TNT-heavy server to measure or justify it |
| Voronoi geometry cache (the winning corner per block, not the biome; proposed 2026-10-29) | — | the measured biome zoom shortcut skips the geometry entirely where all eight cells agree (91 % of neighbourhoods in the bench world) and showed no difference, so the geometry is not the cost; a cache would add memory traffic to a lookup whose cost is the chunk lookup and the palette read |
| Fusing the density compiler's array passes (constant add, multiply, clamp in one loop) | — | the cell-corner buffers hold 1 225 floats and stay in L1, so an extra pass costs a virtual call, not memory bandwidth; estimated 1 to 2 % of the workers, worth doing only as a by-product of open item 3 |
| A per-section counter of dangerous blocks for `WalkNodeEvaluator.checkNeighbourBlocks` | — | exact and feasible after the pattern of `specialCollidingBlocks`, but measured on 2026-10-29 the neighbour loop is 1.1 % of the main thread while 1 200 mobs stand and 0.4 % in the flight, so that is the ceiling; a candidate once the open items above are done |
| Slime world format | — | very large port, own world format; networks that need it use AdvancedSlimePaper |
