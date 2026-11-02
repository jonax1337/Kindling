# Roadmap

What is open and what was dropped. Kindling has one maintainer, so an item stays only if its value clearly outweighs the upkeep it adds on every Leaf merge and Minecraft update. Done items move to the [changelog](../CHANGELOG.md).

Every item follows the same rules: vanilla behaviour by default, three runs per side in `kindling-bench` with the difference marked by `compare.py` ([how to read the numbers](MEASUREMENTS.md#how-to-read-the-numbers)), no new patch where a setting, a plugin or upstream does the job, and changes that must not change behaviour also pass the parity scenarios. Fixes for bugs plain Leaf has too are candidates for Leaf pull requests; none has been sent yet ([Fixes Leaf could take](UPSTREAM.md#fixes-leaf-could-take)). A new item needs a clear case that it is worth its upkeep.

## Open

### Performance

| Item | Why it is open |
| --- | --- |
| The pregenerated world against Leaf | Kindling ticks 5 % slower than plain Leaf there (31.7 → 33.3 ms) at the same CPU time; its main thread waits twice as long for a CPU (149 → 311 ms) and it sends 5 % more chunk packets. The mixed flight also ticked slower with worker demand in all three rounds (33.4 against 30.9 ms, not marked). With Paper's two workers fixed it ticked like Leaf, so the likely rest is the time five workers run after each burst. Not established. ([worker demand](measurements/host.md#worker-demand)) |
| CPU time in the bases | +13 % against plain Leaf (276 → 313 s). With Paper's two workers fixed it was Leaf's. Likely the spawn area generated with every worker at the start. Not established. |
| Skipping discarded density branches | 7 to 10 % of the chunk workers' CPU evaluates range-choice branches that are thrown away ([profile](measurements/pregeneration.md#where-the-time-goes-in-the-chunk-workers)). Compiled density takes the cell-corner part; the noodle cave and ore vein choices run per block on interpolated inputs and need their own fix. Prerequisite: the worldgen vanilla diff with one worker thread (below). |
| Empty sections of the generation ring | 82 % of the overworld sections in memory hold only air, and their fixed overhead is 20 to 22 % of the live heap ([census](measurements/memory-jvm.md#the-ring-of-partly-generated-chunks)). A shared empty section for chunks below `BIOMES`, materialised on the first write, would take most of it back. `ChunkLoadTask` also parses the whole chunk NBT for a `STRUCTURE_STARTS` target; to be looked at afterwards. |
| A criterion for the pregenerator's wait throttle | A share of the last minute fires at baseline on a VPS and at 0.10 halves pregeneration. Candidates: the absolute wait per tick, a higher limit such as 0.20, or throttling only while the machine is busy. |
| A documented "fast" profile | Async pathfinding and mob spawning make 1 200 standing mobs 9 % faster, but are not vanilla. Whether a profile with them belongs in the docs and the doctor. |

### Measurements still missing

| Item | Why it is open |
| --- | --- |
| Pregenerator against Chunky | The pregenerator was only measured against its own first version. Chunky on plain Leaf would be the fair baseline. |
| Elytra test against current Leaf | Last run against an older Leaf on the Windows machine, two runs. |
| "0 % void" with five lines | The directional loading figures follow one line straight ahead of each player. |
| Standing among mobs on a pregenerated world | In the Leaf comparison, plain Leaf was still generating while the players stood. |
| What offsets the Lithium gain in the bases | The Lithium ports alone measured −7 %, but the current build runs the bases at the earlier release's tick time. The hopper cooldown after a failed transfer is the likely candidate; one A/B would settle it. |
| The cost of each vanilla restoration | Measured only together: at most about 1 ms standing among 1 200 mobs. One A/B per restoration would show which one costs what. |
| Memory return and a rejoin | The tick spike when players come back after the heap shrank. The bench needs a rejoin mode. |
| Worker governor | Whether it lowers the main thread's wait on a crowded host, what it costs in generation speed, and good thresholds. Also a `/paper reload` while it holds a lower count. |
| Frontier pregeneration | Throughput against `/pregen`, the real disk growth per visited tile, and a server with players coming and going. |

### Tests and parity

| Item | Why it is open |
| --- | --- |
| Worldgen vanilla diff with one worker thread | Vanilla against vanilla already differs in 410 of 624 chunks, because feature placement across chunk borders depends on worker scheduling. With one worker on both sides, the 6 biome cells that differ from vanilla can be explained or cleared. |
| Parity checks in the game | Hopper pull order with several items, pressure plate and tripwire order for falling entities, a plugin placing a sculk sensor from `BlockReceiveGameEvent`, a client-steered vehicle standing still, lightning frequency, the cure discount, a border-clipped automatic pregeneration across a restart, a full disk freeing up while the server sleeps. |
| Async player data saving on a real server | Write order, the flush barrier and the shutdown drain have unit tests only. Needs `save-all flush` with a look at the file times, and a stop on a slow disk. |
| Scenarios for four fixes | The removed nitwit shortcut, the happy ghast's adult sensor, the idle-memory pause during a pregeneration and the async writer's publication rest on unit tests and review. |
| The furnace fix and shared job sites | Neither bug reproduced on the release, so their fixes have no counter. |
| All parity scenarios in CI | `parity.yml` runs the bases check only; the sleeping and villager pairs run by hand. |
| A POI warning at shutdown | "Accessing poi chunk off-main" during `stop` while a pregeneration ran, once in 8 stops; the stack holds only vanilla, Paper and Moonrise code. Whether the skipped POI update is repaired on the next load is unchecked. A candidate for an upstream report. |

### Known limits

| Item | Why it stays |
| --- | --- |
| Partly generated chunks in memory | Under load 63 % of the chunk objects are neighbours generated only part of the way, kept by vanilla's chunk levels. Unloading them early would change the chunk lifecycle that plugins and the chunk system rely on. |
| `Player#saveData` and `Server#savePlayers` | They return before the asynchronous write lands, as in Leaf. Server-side reads wait for it; outside readers of the file may see stale data until the next flush. Waiting would need a Paper change. |

## Dropped

Recorded so they are not proposed again without new facts.

### Built or tried, and taken out

| Item | Reason |
| --- | --- |
| Spawn pre-checks in parallel | Slower (flight tick 36.7 → 42.9 ms, CPU +6 %, all marked) and not exact: 0.13 % of the mob picks differed because helper threads read other biomes. |
| Client-paced chunk sending | Fast clients got their view no sooner, slow clients took twice as long to join. ([measurements](measurements/chunks.md#client-paced-chunk-sending)) |
| `Vec3`, `AABB`, `ChunkPos` as value classes | −27 % main-thread allocation but no tick time gain; needs JDK 28 with `--enable-preview`, which alone costs 7.6 % in the bases; breaks plugins that use their identity. Worth measuring again once value classes leave preview. |
| Lithium's nitwit job-search shortcut | Skipped calls to the level's random, so not vanilla-identical. |

### Decided against

| Item | Reason |
| --- | --- |
| Trimming the world by `InhabitedTime` | Deletes exactly the chunks the pregenerator creates. |
| A pre-upgrade command | `--forceUpgrade` still exists. |
| Trimming the native heap while players are online | The diagnostic command can delay safepoints. Kindling trims only while nobody is online; the doctor points at `-XX:TrimNativeHeapInterval`. |
| Sleeping hoppers with `ticks-per.hopper-check` above 1 | A woken sleeper cannot keep Paper's check interval, so these hoppers stay awake. |
| Quarantine for entity and POI data | Only chunk data is copied. A chunk saved by a newer Minecraft version needs none: Paper stops the server first. |
| Reusing a built chunk packet for several players | The packet cannot be invalidated safely (block entity tags, light from neighbours, Anti-Xray and plugin listeners change it). |
| `SCHED_IDLE` for pregeneration | A priority inversion with synchronous chunk loads is a risk; `nice` was measured instead. |
| Direction smoothing in directional loading | Ignoring samples from the first ticks and jumps keeps the logic small. |
| `gettid` through `syscall` | The C library's `gettid` covers glibc 2.30 and musl 1.2.2; an older library logs once and keeps the priority. |
| A Voronoi geometry cache | The biome zoom shortcut skips the geometry where all eight cells agree and showed no difference, so the geometry is not the cost. |
| Fusing the density compiler's array passes | The buffers stay in L1; estimated 1 to 2 % of the workers. Only as a by-product of skipping discarded branches. |
| A per-section counter of dangerous blocks for pathfinding | Exact and feasible, but the neighbour loop is 1.1 % of the main thread at most. |
| Leaf pull request #928 (container contents decoded when opened) | Left to Leaf. |
| Leaf pull request #943 beyond its correctness part | Not ported: the MC-157589 behaviour change, a radix sort, the pathfinding pool size and its keepalive default. |
| Leaf pull request #952 (hopper rates) | Not ported; tuned rates are honoured by Paper's transfer code when `hopper-amount` is not 1, with cooldowns from `ticks-per.hopper-transfer`. |

### Reviewed and not taken

Already in Leaf: Lithium equipment tracking, sheep, in-wall checks, fluid slope search, parallel world ticking, the multithreaded tracker, Secure Seed, DAB, Anti-Xray, `B_LINEAR` region files.

Already in vanilla 26.3: a density function compiler of its own, which is why C2ME's compiler and noise optimisations no longer apply. Mojang's compiler still evaluates both branches of a range choice (open item above).

Dead code on a Paper server: the C2ME NBT cache limit and ModernFix compact bit storage.

| Item | Reason |
| --- | --- |
| C2ME aquifer | Aquifers are 7 % of chunk worker time, so a realistic gain stays below the bench's noise; rewrites the core `Aquifer` method; not bit-exact. |
| C2ME world gen math, End biome cache | One hoisted `Math.min`; the End cache ignores Y. |
| StructureLayoutOptimizer | Large; jigsaw assembly does not show in the profile; mainly helps modpacks. |
| Native math | Not seed-identical; native binaries in CI. |
| DivineMC entity, fluid, raid and Moonrise optimisations | Change ordering or timing, or collide with Leaf's patches. |
| Command block parse cache, async `/locate`, chunk lookups | Stale command source; `/locate` returns 0; negligible gain. |
| Player profile result cache | Caches Mojang's `hasJoinedServer` by name only, so a cached name logs in without authentication. |
| Portal search prefetch, chunk worker presets | No measured hitch; the presets give too few workers. |
| Async chunk sending | Skipped whenever Anti-Xray modifies the chunk; packet building is not in the profile. |
| Parallel sensors, parallel entity tracker | Sensors see the world before other mobs move; the tracker duplicates Leaf's. |
| Regionized chunk ticking | 20 core classes and a large scheduler; not maintainable by one person. |
| Behaviour options (entity culling, lag compensation, XP clumping, leaf decay and others) | Plugin-type features or covered by Spigot settings. |
| Lag spike recorder, `/kindling hotspots`, metrics endpoint | spark and exporter plugins cover them. |
| AOT from the jar, config profiles, join prefetch | A documented start command covers AOT; profiles write keys across six config systems; join prefetch is unmeasured and collides with Leaf. |
| Lithium framed maps, inactive navigations | Framed maps: 26.3 already skips the scan it targets. Inactive navigations: identical behaviour cannot be shown. |
| Sakura explosions | No TNT-heavy server to measure it. |
| Slime world format | A very large port with its own world format; networks that need it use AdvancedSlimePaper. |
