# Kindling roadmap

What Kindling works on next. Kindling has one maintainer, so an item stays on this list only if its value clearly outweighs the upkeep it adds on every Leaf merge and Minecraft update (scored 1–10 in an independent review on 2026-10-27; 7.5 or more stays). [MEASUREMENTS.md](MEASUREMENTS.md) records what was measured; each finished item moves there.

## Rules for every item

1. **Vanilla-identical by default** ([AGENTS.md](../AGENTS.md), ground rule 1).
2. **Measured before it becomes a default:** `kindling-bench` before and after, three runs each, a difference counts only if `compare.py` marks it `*` (p < 0.05 and a gap of more than twice the larger half range; [how to read the numbers](MEASUREMENTS.md#how-to-read-the-numbers)). Changes that must not change behaviour also pass the parity scenarios.
3. **No new patch where a setting, a plugin or upstream does the job.** Every Kindling patch is rebased on every Leaf merge.
4. **Bugs go upstream, features stay.** A fix for something that is broken in plain Leaf too is a candidate for a Leaf pull request (like Leaf#970); the owner decided on 2026-10-28 to send none for now, so the fixes are listed in [UPSTREAM.md](UPSTREAM.md#fixes-leaf-could-take). Kindling's own features stay in Kindling.

## Next

New items need the 7.5 bar of the review. What the work of 2026-10-28 left open:

| Open item | Why it is open |
| --- | --- |
| `worker-priority` as a default | The bench host halves the main thread's wait in four scenarios and does not hurt pregeneration speed or lead, but the mixed load showed a (non-significant) rise in void time, and the VPS check never ran short of CPUs and saw no effect. A run under real load on the VPS decides ([MEASUREMENTS](MEASUREMENTS.md#worker-priority-and-the-main-threads-wait-for-a-cpu)) |
| A usable criterion for the pregenerator's wait throttle | A share of the last minute fires at baseline on the VPS (a nearly idle thread waits 10 to 14 %) and at 0.10 stops pregeneration during a flight. Candidates: the absolute wait per tick, a higher limit such as 0.20 (untested), or throttling only while the machine is busy. The doctor's wording ("other threads or processes compete with it") is wrong for the baseline case |
| Memory return and a rejoin | The tick spike when players come back after the heap shrank was not measured; the bench needs a rejoin mode |
| A server test of the async player data saving | The write ordering, the flush barrier and the shutdown drain have unit tests only. `save-all flush` followed by a look at the file times, and a stop on a slow disk, need a real server |
| Player#saveData and Server#savePlayers | They return before the asynchronous write lands (Leaf's behaviour); server-side reads wait for it, outside readers of the file may see stale data until the next flush. Waiting would need a Paper-server change |
| Bench for the five fixes after the last bench round | The hopper cooldown after a failed transfer, the removed nitwit shortcut, the happy ghast's adult sensor, the idle-memory pause during a pregeneration and the async writer's publication are covered by unit tests only |
| The shutdown stack trace | "Accessing poi chunk off-main" during `stop` while a pregeneration ran; not compared with the previous build |
| Parity in CI | `parity.yml` runs the bases check only; the sleeping and villager pairs run by hand (`parity/run.sh <jar> all`) |
| A scenario that shows the furnace fix and shared job sites | Both bugs did not reproduce on the release, so their fixes have no counter |
| Repeat the "0 % void" test with five lines | The figures of 2026-10-26 followed one line straight ahead, and the bench host spent about half of an elytra flight in the void |

## Done (2026-10-28)

From the deep analysis of 2026-10-27. Numbers and method: [MEASUREMENTS.md](MEASUREMENTS.md).

| Item | Result |
| --- | --- |
| Stricter measurement | Three runs, Welch's test marked `*` by `compare.py`, a warm-up that waits for generation to calm down, five lines for the chunk lead and the player's own chunk, GC pauses instead of the sum of all collector beans, the main thread's run-queue wait, survival fake players, emulated client answers, and the same JVM flags in the elytra script |
| Parity scenarios | `type=sleeping` and `type=villagers` with an on/off pair check. They reproduced four of the five documented bugs on release 26.3-11: duplicated items (652), tuned hopper rates ignored (906 instead of 2 751), blocks placed without physics not noticed and a held-back brewing stand never served, and shared villager beds (5 and 3). The held-back furnace and shared job sites did not reproduce |
| Sleeping block entities act as on Paper (`0322`) | Parity pairs identical in all counters with default and tuned rates; not slower than the release (12.7 ±0.3 against 12.6 ±0.4 ms, n = 6 each). The earlier −12 to −15 % points the same way but is not significant with three runs |
| Async pathfinding and tracker act as vanilla (`0324`, Paper `0071`) | Shared beds 5 and 3 on the release, 0 after; claims 48 and 48 in both modes; mixed-load tick times unchanged. The correctness part of Leaf pull request #943 |
| Async player data saving is safe (`0323`) | A pending save is never discarded, writes stay in order on one thread, `save-all flush` and the stop wait for them, an encode error writes nothing. Unit tests only |
| Async mob spawning stall (`0321`) | A failed count no longer stops spawning until restart; mirrors Leaf pull request #937 |
| Skipped spawn cost lookups (`0325`) | Vanilla-identical; no measurable tick time change, spawn counts per kind equal |
| Lithium ports (`0326`) | Bases: average tick 12.0 → 11.2 ms, p95 17.0 → 15.3 ms, p99 24.4 → 19.8 ms (3 runs each, all 15 counters identical) |
| Pregenerator hardening and disk guard (`0327`, Paper `0072`) | Bounded tick work, border-aware radius, failures contained, worlds that unload and return, background state file, exact index at shutdown, pause when the disk would run out, validated `/pregen start`; functional checks passed |
| Doctor corrected, live host diagnostics | The numbers behind each piece of advice cite their source; the `LIVE` section agrees with `/proc` on the bench host and on the VPS |
| Worker priority (`performance.worker-priority`, Paper `0073`) and the pregenerator's wait throttle | Opt-in, off by default; main-thread wait halves on the bench host; the throttle is off by default because it stops pregeneration during a flight |
| Client-paced chunk sending (`0328`) | Opt-in, off by default; the bench shows no gain for fast clients |
| Idle memory return | Opt-in, off by default; about 1.45 GiB less resident memory with nobody online at `-Xms512M -Xmx2G` |
| Copies of unreadable chunks (`0329`) | Verified with damaged region files |

## Not done, on purpose

| Item | Reason |
| --- | --- |
| Trimming the world by `InhabitedTime` | Conflicts with pregeneration: it would delete exactly the chunks the pregenerator is made to create |
| A pre-upgrade command | Not needed: `--forceUpgrade` still exists in 26.3 |
| Trimming the native heap while players are online | The diagnostic command runs in the calling thread's VM state and can delay safepoints. Kindling trims only while nobody is online (`idle-memory`); the doctor points at `-XX:TrimNativeHeapInterval` for those who want the JDK's own periodic trim |
| Leaf pull request #928 (container contents decoded when opened) | Left to Leaf; Kindling carries no copy |
| Leaf pull request #943 beyond the correctness part | Not ported: the MC-157589 behaviour change, a radix sort, the pathfinding pool size and its keepalive default |
| Leaf pull request #952 (hopper rates) | Not ported; the tuned rates are honoured by taking Paper's transfer code when `hopper-amount` is not 1, with the cooldowns derived from `ticks-per.hopper-transfer` |
| Making hoppers sleep with `ticks-per.hopper-check` above 1 | A woken sleeper cannot keep Paper's check interval, so these hoppers stay awake |
| Lithium's framed map change, inactive navigations | See the table below |
| Lithium's nitwit job-search shortcut | Taken out again: it skipped calls to the level's random, so it was not vanilla-identical |
| Quarantine for entities and POI data, and for chunks the region file reports as missing | Only chunk data that throws is copied. A truncated header or an invalid compression id can be recovered by the region file itself, and its raw bytes are not reliably at hand |
| A quarantine copy of a chunk saved by a newer Minecraft version | Paper stops the server first (verified), so nothing is lost |
| `SCHED_IDLE` for pregeneration | Not attempted: a priority inversion with synchronous chunk loads is a risk, and `nice` was measured first |
| Direction smoothing in directional loading | Not added: samples from the first ticks and jumps are ignored instead, which keeps the order logic small |
| `gettid` through `syscall` | `worker-priority` uses the C library's `gettid` (glibc 2.30, musl 1.2.2); an older library logs it once and the workers keep their priority |
| Showing the furnace and job-site bugs in a scenario | Neither reproduced on the release, so no counter proves their fixes |

## Done (2026-10-27)

| Item | Score | Result |
| --- | --- | --- |
| Pregenerator resume fix | 9.5 | State written atomically and durably; a damaged file is kept as `.corrupt` and only the affected worlds start over |
| Leaf's synchronous options one by one | 8.5 | Measured; none is both faster and the same behaviour, so no new default ([MEASUREMENTS](MEASUREMENTS.md#leafs-options-one-by-one)) |
| `/kindling doctor` and start-up check | 8.0 | Command plus start-up warnings, setting `performance.doctor.startup-check` |
| Fast fliers on modest hardware | — | Paper's automatic limit of concurrent generations per player capped at 32: time in the void on the 8-thread bench host 38.5 % → 0.9 % ([MEASUREMENTS](MEASUREMENTS.md#concurrent-generations-per-player)) |
| Parity CI | 7.8 | `parity.yml` and the weekly Leaf merge run the bases scenario and compare 15 counters with `kindling-bench/parity/bases-ci.json`; identical on Windows and Linux |
| Lithium ports (redstone wire power, useless sensors, rain and ice checks, lazy map cursor names) | — | Same results as vanilla (a differential test checks the wire power against vanilla's algorithm in 4000 random surroundings). Bases scenario, 3 runs each: average tick 12.0 → 11.2 ms, p95 17.0 → 15.3 ms, p99 24.4 → 19.8 ms, all 15 counters identical. Measured with Lithium's nitwit job-search shortcut, which was taken out afterwards because it changes the level's random numbers |

## Reviewed and dropped

Recorded so they are not proposed again without new facts. Scores from the 2026-10-27 review; DivineMC checked at `219de55` (`ver/26.3`).

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
