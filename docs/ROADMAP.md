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
| `worker-priority` as a default | The bench host halves the main thread's wait in four scenarios and does not hurt pregeneration speed or lead, but the mixed load showed a (non-significant) rise in void time, and the VPS check never ran short of CPUs and saw no effect. A run under real load on the VPS decides ([host measurements](measurements/host.md)) |
| A usable criterion for the pregenerator's wait throttle | A share of the last minute fires at baseline on the VPS (a nearly idle thread waits 10 to 14 %) and at 0.10 stops pregeneration during a flight. Candidates: the absolute wait per tick, a higher limit such as 0.20 (untested), or throttling only while the machine is busy. The doctor's contention warning already uses the absolute wait (at least 20 ms per second) |
| A documented "fast" profile | Async pathfinding and async mob spawning on recover most of the 3.9 ms standing cost of the vanilla defaults (37.5 against 40.5 ms; the gap misses the rule at p = 0.051, so repeat it with more runs first). Whether a profile with them on belongs in the README and the doctor, given that a mob follows a path a tick or more late and spawn counts can be stale (not vanilla, [ground rule 1](../AGENTS.md)) |

**Measurements still missing**

| Open item | Why it is open |
| --- | --- |
| Comparisons with plain Leaf that are missing | The pregenerator was only measured against its own first version; Chunky on Leaf `66f99f8e` would be the fair baseline. The elytra test at 1.7 blocks/tick last ran against Leaf `4c3d5689` on the Windows machine with two runs |
| Standing against plain Leaf on a pregenerated world | In the final round plain Leaf was still generating while the players stood (its three runs spread ±5.4 ms, 47.9 against 39.7 ms is not significant), so the standing figure against Leaf is not a measure of the defaults |
| Kindling's CPU time | +25 % against plain Leaf in the bases (294 → 368 s) and +48 % in the mixed load (968 → 1 433 s), equal to 26.3-12. Five chunk workers instead of two on a 4-core, 8-thread host, the async threads and the extra chunks sent were not separated ([Kindling against plain Leaf](measurements/vs-leaf.md)) |
| What offsets the Lithium gain in the bases | The Lithium ports alone measured −7 %, yet the final build runs the bases at the release's 11.5 ms. The hopper cooldown after a failed transfer is the only later change to what the bases tick; an A/B of it alone would settle it ([world ticking](measurements/ticking.md)) |
| What offsets the restore patches | The restored vanilla code (hash, goal order, entity order, per-tick in-wall and line-of-sight checks) costs at most about 1 ms standing among 1 200 mobs and nothing visible in the bases or the flight, measured only together. Which restoration costs what, and whether a vanilla-identical cheaper version exists, would need one A/B per restoration |
| Memory return and a rejoin | The tick spike when players come back after the heap shrank was not measured; the bench needs a rejoin mode |
| Repeat the "0 % void" test with five lines | The figures of 2026-10-26 followed one line straight ahead, and the bench host spent about half of an elytra flight in the void |

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
| Partly generated chunks in memory | Under load 63 % of the chunk objects (11 181 of 17 672) are neighbours generated only part of the way, the ring vanilla's chunk levels keep around each player; they are the largest share of the heap that is not gameplay. Unloading them early would change the chunk lifecycle that plugins and the chunk system rely on; not attempted ([memory measurements](measurements/memory-jvm.md#jvm-and-heap)) |
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

### Reviewed and not taken

Scores from the 2026-10-27 review; DivineMC checked at `219de55` (`ver/26.3`).

**Already in Leaf:** Lithium equipment tracking (Leaf 0229), sheep (0054), in-wall/suffocation checks (0018), BFS slope search for fluids (0181), parallel world ticking, the multithreaded tracker, Secure Seed, DAB, Anti-Xray, `B_LINEAR` region files.

**Already in vanilla 26.3:** a density function compiler of its own (`levelgen/densityfunction/DensityFunctionCompiler`: rewrite rules, cache reuse, buffered samplers). This is why C2ME's compiler and noise optimisations no longer apply, and why DivineMC dropped them for 26.3.

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
| Slime world format | — | very large port, own world format; networks that need it use AdvancedSlimePaper |
