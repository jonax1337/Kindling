# Worldgen parity: design

Status: Mode A implemented and run at dev size (2026-10-30). The first dev runs corrected four points of this design;
section 8 lists them and wins over the text above it. Every claim below is marked **[code]** (read in the
applied 26.3 sources or disassembled from Mojang's jar with `javap`, see IMPL-NOTES.md), **[measured]** (taken from
an earlier run in `docs/measurements/vanilla-parity.md`) or **[to verify]** (first thing the dev run must check).
Source paths are relative to `E:\DEV\Minecraft\cubyx-leaf\leaf-server\src\minecraft\java\` (applied Paper/Leaf/Kindling
sources, read only); "Mojang" means the classes inside `minecraft-server-26.3.jar` (26.3 ships unobfuscated, so the
names are the same as in the Paper-family jars at runtime).

## 0. Result in one paragraph

Feature placement writes into the 3 x 3 chunks around the decorated chunk, so a world is the result of an *ordered*
composition of per-chunk decorations, and Mojang defines no order: it is whatever the worker threads produce. A
comparison of two whole worlds can therefore only be exact if both servers decorate in the same order. The chosen
method makes the order canonical with nothing but vanilla commands: **a raster sweep of `forceload` steps over a
rectangle whose top row and left column were pre-generated once by Mojang's jar and copied into every server
folder.** With that "L" in place, each step raises exactly one chunk to each generation status (one new
structure-start chunk, one new biome chunk, one new terrain chunk, one new decorated chunk, one new full chunk), so
there is nothing left for a scheduler to reorder. Ticking is frozen (`tick freeze`), one worker thread runs all
generation (for the climate R-tree's thread-local cache), and Minecraft's own JFR event `minecraft.ChunkGeneration`
(emitted by vanilla and by Moonrise through the same `ChunkStep.apply`) records the order of every generation step, so
each run *proves* it followed the canonical order instead of assuming it. Under these conditions vanilla against vanilla
must be exactly 0 in everything compared (blocks, biomes, block entities, scheduled ticks, structure starts and
references, chunk status, post-processing lists, generated entities without UUIDs), and Paper, Leaf and Kindling are
compared with Mojang block for block on the same footing. A second, cheaper mode extends the *terrain* check (noise,
aquifers, ore veins, surface rules, carvers, structure terrain adaptation) to much larger areas at full thread count
with a features-off datapack, where order does not matter by construction.

---

## 1. Where the order dependence comes from

### 1.1 The 26.3 pipeline, step by step

`ChunkPyramid.GENERATION_PYRAMID` (`net/minecraft/world/level/chunk/status/ChunkPyramid.java:11-34`) **[code]**. 26.x
merged noise, surface and carvers into one `TERRAIN` status; the statuses are empty, structure_starts,
structure_references, biomes, terrain, features, initialize_light, light, spawn, full
(`ChunkStatus.java:21-30`).

| Status | Task (`ChunkStatusTasks.java`) | Reads | Writes | Order-dependent? |
| --- | --- | --- | --- | --- |
| `structure_starts` | `generateStructureStarts` :44-62 → `ChunkGenerator.createStructures` | seed, biome *source* (climate R-tree, single-point queries) | only this chunk's starts | no for blocks; the R-tree query history matters for exact ties (1.4) |
| `structure_references` | `generateStructureReferences` :71-78 | starts within 8 chunks | this chunk's references | no |
| `biomes` | `generateBiomes` :80-87 → `ChunkAccess.fillBiomesFromNoise` (`ChunkAccess.java:613-624`) | climate sampler, R-tree | this chunk's biomes | in practice no (1.4) |
| `terrain` | `buildTerrain` :102-142 → noise, aquifers, ore veins, surface rules, carvers (`NoiseBasedChunkGenerator.java:396-437`) | own noise, biomes of the 3 x 3 (`BIOMES` radius 1), structure pieces within 8 (beardifier) | **this chunk only** (`blockStateWriteRadius(0)`, ChunkPyramid.java:20) | no |
| `features` | `generateFeatures` :144-155 → `ChunkGenerator.applyBiomeDecoration` (`ChunkGenerator.java:386-436`) | blocks, heightmaps, biomes of the **3 x 3** | blocks, ticks, block entities, post-processing marks in the **3 x 3** (`blockStateWriteRadius(1)`, ChunkPyramid.java:27; enforced by `WorldGenRegion.ensureCanWrite`, `WorldGenRegion.java:379-418`) | **yes** |
| `initialize_light`, `light` | `initializeLight`/`light` :157-172 | blocks | light only | not compared (different engines: Starlight in the Paper family) |
| `spawn` | `generateSpawn` :174-182 → `spawnOriginalMobs` | biomes, blocks; `WorldGenRegion.random` seeded per centre chunk (`WorldGenRegion.java:131`) | entities | positions no; per-entity random fields possibly (to verify) |
| `full` | `full` :184-212 on the main thread; block ticking promotion then runs `LevelChunk.postProcessGeneration` | neighbour blocks | shape updates, fluid ticks of marked positions | yes, but only through the order of the steps before it |

The decoration of one chunk is seeded per chunk and per feature (`setDecorationSeed` at ChunkGenerator.java:397,
`setFeatureSeed` at :428), so each chunk's *random numbers* are order-independent. Its *result* is not: a feature tests
the blocks and heightmaps it sees (is this air, is this stone, how high is the surface here), and in the 3 x 3 around
it some neighbours may already be decorated and others not. Two neighbours decorated in the opposite order give a
different world wherever their 3 x 3 areas overlap and one feature looks at what the other wrote. Structure pieces are
placed in the same step (ChunkGenerator.java:424-436), clipped to the decorated chunk (`getWritableArea`, :536-543),
but they read heightmaps and blocks too.

### 1.2 Mojang's scheduler, and why vanilla against vanilla differed in 410 of 624 chunks

**[code, Mojang via javap]** `ChunkMap.<init>` builds `new ConsecutiveExecutor(executor, "worldgen")` on the server's
background executor and wraps it in a `ChunkTaskDispatcher`; `runGenerationTask` submits every `ChunkGenerationTask`
there. A task schedules its layers one status at a time (`ChunkGenerationTask.java:115-157`, the vanilla class kept in
the applied sources): for each status it walks the square `x` outer, `z` inner and calls `applyStep` for every chunk
that is not there yet; `features` returns a completed future, so the decoration runs right inside that loop. Many tasks
exist at once (one per chunk holder whose ticket level rose), they interleave whenever one of them waits for an
asynchronous future, and `terrain` and `biomes` are asynchronous (Mojang's `NoiseBasedChunkGenerator.buildTerrain` and
`ChunkGenerator.createBiomes` use `CompletableFuture.supplyAsync(..., Util.backgroundExecutor().forName(...))`). So
**which task reaches its `features` layer first, and therefore the decoration order at every chunk border, depends on
when noise futures finish on the worker threads**. Mojang's background executor has
`clamp(availableProcessors - 1, 1, max.bg.threads)` threads (`Util.maxAllowedExecutorThreads`, javap), 11 on a 12-core
machine.

The worldgen mode forceloads 14 areas (624 chunks plus their pyramids) in one go, so thousands of tasks race. The
result **[measured]**: terrain equal, features differ, 410 of 624 chunks. Two further sources of run-to-run noise in that
mode, both **[code]**:

- **Nothing is frozen.** Forceloaded chunks are entity-ticking for minutes; random ticks (grass, vines, leaves),
  scheduled fluid and block ticks placed by features (springs, gravel), fire and mob AI run with unseeded randoms.
- **The game rules are not set.** 26.3 renamed the game rules to snake case (`gamerules/GameRules.java:24-92`:
  `random_tick_speed`, `spawn_mobs`, `advance_time`, `advance_weather`, ...). `worldgen_script.py` and
  `contraptions.py` still send `gamerule doMobSpawning false`, `randomTickSpeed 0`, `doFireTick false`. **[to verify]**
  in a server log that 26.3 rejects these (no compatibility alias was found in `GameRules.java`); if so, mob spawning
  and random ticks ran in both modes, which also explains part of the contraption mode's 74 entity differences.

### 1.3 Moonrise (Paper, Leaf, Kindling)

Moonrise replaces the scheduler (`GenerationChunkHolder` methods throw `UnsupportedOperationException`,
`GenerationChunkHolder.java:38-90`) but runs the same steps: `ChunkUpgradeGenericStatusTask.java:97` calls
`ChunkPyramid.GENERATION_PYRAMID.getStepTo(status).apply(...)`. Statuses with a write radius take a radius lock
(`ChunkUpgradeGenericStatusTask.java:47-54`), so two decorations never overlap in time, but their *order* is still the
worker pool's. Terrain runs synchronously on the calling worker (`Runnable::run`, `NoiseBasedChunkGenerator.java:437`).
The worker count comes from `chunk-system.worker-threads` (`GlobalConfiguration.java:214-222`), defaulting to
`-D<brand>.WorkerThreadCount` or Kindling's `ChunkWorkers.defaultFor` (`MoonriseCommon.java:45-68`, which logs
"<brand> is using N worker threads"); Paper's `Util` background executor reads `-DPaper.WorkerThreadCount`
(`Util.java:238-253`). Even with one worker on each side, Moonrise's order is not Mojang's, so **a single worker thread
alone can make vanilla agree with vanilla, but not Paper with vanilla**: any step that raises two neighbouring chunks to
`features` at once leaves their order to the scheduler.

### 1.4 The climate R-tree remembers the last answer per thread

**[code]** `Climate.RTree` keeps `ThreadLocal lastResult` (`Climate.java:263`); `search` passes it as the starting
candidate (`:462-466`), and `SubTree.search` only replaces the candidate on a strictly smaller distance (`:534-553`).
Moonrise's replacement `moonrise$findNearest` (`:266-316`, used by `ParameterList.findValue`, `:173-185`) does the same
with its own thread-local `lastReturned` and `childDist >= suggested.distance → skip`. So on an exact tie the answer is
the previous answer of that thread. Ties are not rare boundary cases only: `OverworldBiomeBuilder.java:906-912` gives
`dripstone_caves` (continentalness 0.8..1.0) and `lush_caves` (humidity 0.7..1.0) boxes that both span every other
parameter at depth 0.2..0.9 with offset 0 (`:1031-1043`), so in the whole overlapping volume both are at distance 0.
Paper documents the hazard itself (`LevelChunkSection.java:322`, "Sampling order must match Vanilla, as the RTree
caches the last result (MC-241546)").

What this means:

- **Stored biomes** are in practice order-independent: `fillBiomesFromNoise` samples a chunk bottom-up in a fixed
  order on one thread (`ChunkAccess.java:613-624`, `LevelChunkSection.java:317-331`), so only the chunk's very first
  sample (at the bottom of the world, below the cave depth range) sees a candidate from outside the chunk.
- **Single-point queries** (structure biome checks in `createStructures`, `WorldGenRegion.getUncachedNoiseBiome`,
  stronghold ring placement at world load, `/locate biome`) do see whatever the thread answered last, so a structure
  start can in principle depend on query history.
- The method below runs all generation on one thread in an identical step sequence on both sides, which makes the query
  history identical after the first query that is not a tie.

### 1.5 The six biome cells (new build against vanilla)

Not explained yet; the run folders were deleted, so the coordinates are gone. In order of likelihood, each with a
deciding check (`climate_probe`, section 6):

1. **The climate sampler is not bit-exact in the Paper family** (an optimised noise or density path that rounds
   differently). A last-bit difference flips the quantised climate value (`Climate.quantizeCoord`) at a parameter
   boundary and picks the neighbouring biome, a handful of times per million samples. Check: compute the target point
   of each differing cell with Mojang's classes and with the jar's classes (same class names, same probe); different
   targets settle it, and the probe names the parameter.
2. **Comparer artifact** for an encoding only Paper writes (Paper keeps uniform biome sections in a single-value
   palette, `LevelChunkSection.java:319-324`; the comparer already needed one fix for 26.x palettes). Check: dump the
   raw biome `palette`/`data` of the six sections from both worlds and decode them by hand.
3. **An R-tree tie** (1.4). Unlikely for stored biomes (only a chunk's first sample is exposed). Check: the probe
   lists every leaf at minimal distance; two leaves holding A's and B's biome mean a tie.

---

## 2. Methods considered

| Method | What it gives | Verdict |
| --- | --- | --- |
| **(a) Compare by stage** in the existing chaotic mode | Biomes and structure starts/references of every saved chunk (they are written before any neighbour can touch the chunk); terrain cannot be isolated, because the only saved chunks with status `terrain` border decorated chunks and carry their writes | Cheap but small: no blocks. Part of the chosen method (structure data and status are added to the comparison) |
| **(b) Canonical order with vanilla means** | One worker thread plus forceloading chunk by chunk with waits. Plain, this still raises 7 x 7 chunks to `features` with the first `forceload` and a row of 7 at the start of every row (a forced chunk is level 31, `ChunkMap.java:126`; `features` is needed out to distance 3), and their order is the scheduler's. **With a shared L-shaped bootstrap each step raises exactly one chunk per status** (proof in 3.1). | **Chosen**, as the canonical sweep (Mode A) |
| **(c) Order-independent subsets or stability masks** | Masking chunks that differ between N vanilla runs hides exactly the borders where a scheduler change shows; N runs only bound the observed variation. "Each chunk's own features once all neighbours are done" is not order-independent either: a decoration reads what earlier neighbours wrote, and that can chain across chunks | Rejected as a verdict; kept as a diagnostic only |
| **(d) Headless in-process driver** | Calling the generator steps on our own `ProtoChunk`s in a fixed order. Feasible for vanilla through its own `GenerationChunkHolder`/`StaticCache2D`, but in the Paper family those methods throw and `WorldGenRegion` reads chunks through Moonrise's holders, so one driver cannot serve every jar without re-implementing each scheduler's holder layer. It would bypass ticket levels, chunk loading/saving and the Moonrise task pipeline that ships | Rejected for the parity verdict. A detached terrain-only harness may still be useful as a unit test for bit-exact terrain changes (density compiler), not as the vanilla proof |
| **Features-off datapack** (variant of a) | Biomes with empty feature lists (generated from Mojang's own biome JSONs): `features` decorates nothing, so every block comes from `terrain` (write radius 0) and from structure pieces clipped to their chunk. Order-independent by construction except structure pieces that read across borders (to be measured) | **Chosen as Mode B**, for wide terrain coverage at full thread count |

---

## 3. The chosen method

### 3.1 Mode A: the canonical sweep

Notation: chunk `(x, z)`; `S_r(c)` the square of chessboard radius `r` around `c`. A forced chunk `c` (ticket level 31,
`ChunkLevel.java:10-48`) needs status `s` on `S_{r_s}(c)` with, from the pyramid's accumulated dependencies:
`full` r = 2, `initialize_light` (hence `features`) r = 3, `terrain` r = 4, `biomes` and `structure_references` r = 5,
`structure_starts` r = 13 **[code; the dev run confirms it from the JFR log]**.

**Bootstrap.** For a sweep over `x ∈ [X0, X1]`, `z ∈ [Z0, Z1]`, Mojang's jar forceloads the L
`B = {(X0-1, z) : Z0-1 ≤ z ≤ Z1} ∪ {(x, Z0-1) : X0-1 ≤ x ≤ X1}` once (any thread count; the order inside the L does not
matter), removes the forceloads, saves and stops. This world is copied into every server folder.

**Sweep.** In raster order (`z` ascending, then `x` ascending) `forceload add` one chunk `c = (x, z)` and wait until it
is entity-ticking before the next.

**Claim.** For every radius `r`, step `(x, z)` raises exactly one chunk to the statuses of radius `r`: `(x+r, z+r)`.

*Proof.* Let `P` be the bootstrap plus all earlier steps; the chunks already at those statuses are `U = ∪_{p∈P} S_r(p)`
(statuses persist, so this holds whether or not the chunks are still loaded). A point `(a, b) ∈ S_r(c)` with
`b ≤ z+r-1` lies in `S_r((x, z-1))`, and `(x, z-1) ∈ P` (previous row, or the bootstrap row when `z = Z0`). A point with
`b = z+r` and `a ≤ x+r-1` lies in `S_r((x-1, z))`, and `(x-1, z) ∈ P` (previous step, or the bootstrap column when
`x = X0`). That leaves `(x+r, z+r)`. It is not in `U`: covering it needs `p_z ≥ z`; earlier steps have `p_z < z`, or
`p_z = z` and `p_x < x` (so `|x+r-p_x| > r`); bootstrap row chunks have `p_z = Z0-1 < z`; bootstrap column chunks have
`p_x = X0-1 < x`. ∎

So a step consists of exactly one chain of generation steps, each depending on the previous one:
`structure_starts (x+13, z+13)` → `structure_references`, `biomes (x+5, z+5)` → `terrain (x+4, z+4)` →
`features`, `initialize_light (x+3, z+3)` → `light`, `spawn`, `full (x+2, z+2)`, then the block-ticking promotion of
`(x+1, z+1)` (post-processing) and entity ticking of `(x, z)`. There is nothing a scheduler can reorder, whatever its
thread count, and both servers decorate the same chunk with the same neighbourhood at every step. Entity ticking of
`c` requires its radius-2 neighbours to be full, so "c is loaded" (`execute if loaded`, which tests
`ENTITY_TICKING`, `ExecuteCommand.java:458-462`) means the whole chain of the step has finished.

**Why one worker thread anyway.** The chain is canonical with any thread count, but the R-tree's thread-local candidate
(1.4) would then depend on which thread ran which step. Vanilla gets `-Dmax.bg.threads=1`; the Paper family gets
`chunk-system.worker-threads: 1` in `config/paper-global.yml` and `-DPaper.WorkerThreadCount=1` (plus the same property
with the jar's brand), and the driver aborts unless the log line of `MoonriseCommon.java:68` says 1 worker thread. The
JFR log must then show every generation event except `full` on one and the same thread.

**Why the bootstrap is copied, not regenerated.** The first `forceload` of an empty world always decorates 7 x 7
chunks at once; there is no vanilla command that requests a chunk at a status below `full` (the only intermediate
requests are `structure_references` lookups and `biomes` reads without loading), and every load ticket is at least a
full chunk with a decorated 3 x 3. So the L is ambiguous once, and it is made identical by copying instead of by order.
Loading it is also a test: the jar under test must read and re-save proto chunks that Mojang wrote, unchanged.

**Order evidence.** After `tick freeze` the driver sends `jfr start`; before saving, `jfr stop`. `ChunkStep.apply`
(`ChunkStep.java:64-78`) emits `minecraft.ChunkGeneration` (fields `chunkPosX`, `chunkPosZ`, `status`, `level`,
`ChunkGenerationEvent.java:14-38`) for every status that actually generates, in vanilla and in Moonrise alike.
`jfr print --json --events minecraft.ChunkGeneration` (the JDK's own tool) gives start time, thread and position. The
checker requires, per step, exactly the predicted events at the predicted positions, in chain order, all before the
next step's first event, and no generation event anywhere else. For two runs it also requires the same event sequence
(ignoring `full` and `light`, where Moonrise's tasks may not go through `ChunkStep`). A run that fails the check gets no
verdict.

### 3.2 One measured run (per jar, per location)

1. Copy the bootstrap world into a fresh folder; write `server.properties` (`server-ip=127.0.0.1`,
   `server-port=25999`, status/query/rcon off, `online-mode=false`, the seed, `generate-structures=true`); for the Paper
   family also `config/paper-global.yml` (worker threads 1) and `cache/mojang_26.3.jar` (a copy of the verified Mojang
   jar, so Paperclip does not download it **[to verify]** the file name Paperclip expects).
2. Start the jar; wait for `Done`; send `tick freeze` immediately (`ServerLevel.java:1019-1072`: block ticks, fluid
   ticks, time and chunk ticking only run while the tick rate manager runs normally; `ServerChunkCache.java:526,548`:
   no random ticks, no unloading while frozen unless Spigot's `unload-frozen-chunks`, default false). Nothing is loaded
   before this point: the bootstrap was saved with its forceloads removed, there are no spawn chunks in 26.x, and
   `setInitialSpawn` (`MinecraftServer.java:849-900`) only runs for new worlds.
3. Set the game rules with their 26.3 names (`random_tick_speed 0`, `spawn_mobs false`, `advance_time false`,
   `advance_weather false`, ...) and check each answer in the log.
4. `jfr start`; sweep the location (`execute in <dimension> run forceload add ...` and
   `execute in <dimension> if loaded ... run say STEP <i>`, polled once per tick); `jfr stop`.
5. `save-all flush`, `stop`; wait for the process to exit.
6. Check the JFR order; compare the world with the reference run of the same location.

One location per server start keeps memory bounded (while frozen nothing unloads) and keeps the runs independent.
Only `execute if loaded` is used to wait: commands that read blocks (`execute if block`, `setblock`, `tp`) load the
chunk synchronously and would generate out of order.

### 3.3 What is compared

Every chunk saved in the location's rectangle `[X0-14, X1+14] x [Z0-14, Z1+14]`, whatever its status, in both worlds:

| Compared | Change against the current comparer |
| --- | --- |
| Chunk `Status` | now compared for every chunk, not only "both full" |
| Block states, biomes, block entities (full NBT minus volatile keys), scheduled block and fluid ticks | as now, also for proto chunks |
| `structures` (starts and references) | new |
| `PostProcessing` (proto chunks) | new |
| Heightmaps | new, as their own category (derived, but deterministic now) |
| Entities from the `spawn` step, without `UUID` and the volatile keys | as in contraptions mode; any field the vanilla self-check shows to vary goes to `ignored.py` with its reason |
| Light, `isLightOn` | not compared (Starlight against Mojang's engine); available behind a flag |

Each report also prints what it compared (chunks per status, non-air blocks, block entities, structure starts,
entities, scheduled ticks, distinct biomes), and the run fails if a predicted chunk is missing or empty: a "0
differences" over nothing must not pass. Differences are reported per stage, separating chunks where the stage was
computed by the jar under test (the `(x+r, z+r)` set) from bootstrap chunks it only loaded, modified and saved.

### 3.4 Covered, and not covered

**Covered, with the claim "vanilla against vanilla 0, and the jar under test equal to Mojang's jar":** for each swept
location, the whole generation pipeline as each jar runs it end to end through its own chunk system: structure starts
and references, biomes, terrain (noise, aquifers, ore veins, surface rules, carvers, structure terrain adaptation),
feature decoration and structure pieces with the full neighbourhood interaction, post-processing at the block-ticking
promotion, scheduled ticks left by generation, entities spawned by generation, and the reading and rewriting of
Mojang-written proto chunks. In all three dimensions.

**Not covered:**

- The scheduler's own order. Vanilla does not define one, so "same order as vanilla" has no meaning; the claim is "the
  same world as vanilla for the same order".
- Multi-threaded generation effects: races and thread-local R-tree histories with several workers. A jar whose
  generation code is only wrong under concurrency (a shared random, a cache keyed per thread) passes Mode A. Example:
  the Purpur shared-random UUID race Kindling fixed (`EntityRandoms`) would not show here.
- Chunks generated at server start of a new world and the bootstrap L itself (made by Mojang's jar; the jar under test
  only reloads and extends them).
- Anything after generation: ticking, players, light values.
- Areas outside the swept rectangles (Mode B widens the terrain part).
- Single-point R-tree queries made outside generation (`/locate biome`, spawn search).

### 3.5 Mode B: wide terrain check

A datapack generated from Mojang's jar (`data/minecraft/worldgen/biome/*.json` with every `features` list emptied,
carvers and everything else untouched), placed in `world/datapacks` and enabled through `initial-enabled-packs`
before the world is created **[to verify]** that every jar enables it at creation. Structures stay on, so the
beardifier and structure pieces are exercised. Then the existing chaotic forceload of large areas at the default
thread count. Expected order-independent by construction (terrain writes radius 0); residual differences in vanilla
self-checks would come from structure pieces reading neighbour blocks and must be listed, not masked. Compared:
blocks, biomes, structure starts, block entities of structure pieces. This is the mode that can cover hundreds of
thousands of chunks for bit-exact terrain changes (density compiler, compiled material rules) in reasonable time.

---

## 4. Self-tests

A result counts only after all of these pass on the same build of the tool.

1. **Vanilla against vanilla, N times** (dev: N = 2 on a 4 x 4 sweep; bench host: N = 5 on the standard profile):
   0 differences in every category and identical JFR event sequences. Different machine load between the runs on the
   host is welcome.
2. **Order check is not vacuous:** one vanilla run forceloads the whole sweep rectangle at once. The JFR checker must
   reject it, and its world should differ from the canonical one (this also shows why the canonical order matters).
3. **Bootstrap must be shared:** one vanilla run starts from a bootstrap generated by a second, independent Mojang run.
   Differences near the L are expected; none would mean the L does not influence the result and the setup is suspect.
4. **A planted deviation in pure vanilla:** the test side with `generate-structures=false`. Expected: missing
   structure starts and references, terrain differences only around structures (beardifier), feature differences
   where pieces are missing; biomes 0.
5. **A planted deviation in a fork's generator:** Kindling with the overlay `leaf-global.yml`
   `performance.faster-random-generator.enabled: true` and `enable-for-worldgen: true` (Leaf's option that swaps the
   decoration random, `ChunkGenerator.java:526`, `FastRNG.java:35,72`, warns "This will affect world generation").
   Expected: feature and structure differences, biomes 0; reported under `features`. (`misc.secure-seed.enabled` is
   a second candidate but changes seeds stored in the world, so it is only usable on a fresh bootstrap.)
6. **Comparer sensitivity:** `mutate_region.py` changes exactly one block state, one biome entry, one structure
   reference, one scheduled tick and one entity field in a copy of a saved world (rewriting the chunk into the region
   file). The comparer must report exactly those five and nothing else.
7. **Unit tests** (standard library only): the single-increment property simulated for every radius 0..13 and random
   rectangle sizes; the JFR checker on synthetic event lists (accepts the canonical list, rejects a swap, an extra
   event, a missing event, a second thread); the NBT and region writer round trip.

---

## 5. Scope and runtime on the bench host

Locations are picked once with `/locate structure` and `/locate biome` in a throwaway Mojang run (never in the
bootstrap) and fixed in `locations.json`, at least 100 chunks apart and from spawn. Candidate set for seed
`-4172144997902289642`: overworld village, ancient city (deep dark), ocean monument, trial chambers, mangrove swamp or
jungle, badlands (mineshafts), a lush/dripstone cave region (the R-tree overlap); nether bastion and fortress; end city
on the outer islands (x ≥ 1 500, away from the dragon fight).

| Profile | Locations | Sweep | Steps per jar | Expected time per jar run | Chunks compared per jar |
| --- | --- | --- | ---: | --- | ---: |
| dev (this PC) | 1 overworld | 4 x 4 | 16 | 1-2 min incl. start | ~1 000 |
| standard | 4 overworld, 1 nether, 1 end | 24 x 24 | 3 456 | ~15-25 min | ~16 000 |
| full | 7 overworld, 2 nether, 1 end, 2 seeds | 32 x 32 | 20 480 | ~1.5-2 h | ~70 000 |

Per-step time is an **expectation**: one chain of nine generation steps on one thread (tens of milliseconds) plus
waiting for the next tick to process the command and the poll, about 0.15-0.3 s. The first dev run times it; the table
is redrawn from that. The comparison runs in Python at an expected 10-30 ms per chunk. Jars per profile: Mojang (reference
plus N self-checks), Paper at `paperCommit`, plain Leaf `66f99f8e`, Kindling; one server at a time on
127.0.0.1:25999. Mode B (when built): 4 overworld areas of 64 x 64 chunks at the default thread count, a few minutes per
jar.

---

## 6. File plan and commands

New tool `kindling-bench/worldgen-parity/`, standard-library Python (run with `python -I`), importing the comparer
modules of `../vanilla-diff` instead of copying them.

| File | Job |
| --- | --- |
| `DESIGN.md`, `IMPL-NOTES.md`, later `README.md` | this design, the work log, usage |
| `parity.py` | entry point: `bootstrap`, `run`, `compare`, `selfcheck`, `suite`; exit codes 0 identical, 1 differences, 2 setup or order failure |
| `server.py` | start a jar in a folder (always 127.0.0.1:25999, refuses anything else), console reader thread, wait for lines, send commands and match answers; per-family setup (Mojang: `-Dmax.bg.threads=1`; Paper family: `paper-global.yml`, `-D<brand>.WorkerThreadCount=1`, Paperclip cache) and the worker-count check |
| `sweep.py`, `locations.json` | bootstrap L and raster order per location; the predicted event list per step |
| `jfr_order.py` | runs `jfr print --json`, checks a run against the prediction, compares two runs |
| `worldcopy.py` | copies the bootstrap into a server folder in the layout the jar reads (Mojang's dimension folders against CraftBukkit's `world_nether`/`world_the_end`), verified by a round-trip run |
| `compare_all.py` | the comparison of 3.3 on top of `vanilla-diff/worlddiff.py` and `chunkdiff.py` (status, structures, post-processing, heightmaps, all statuses, per-stage tally, non-vacuity counts) |
| `nbtwrite.py`, `mutate_region.py` | NBT writer, region rewrite, planted mutations (self-test 6) |
| `fetch_paper.py` | PaperMC fill API v3 (`fill.papermc.io/v3/projects/paper/versions/26.3/builds`): the build whose commit list contains `paperCommit` `e415f5e0...`, sha256 checked, into `jars/`; stops if no build contains that commit. Nothing else is downloaded |
| `terrain_pack.py` | Mode B datapack from the biome JSONs inside Mojang's jar |
| `climate_probe/ClimateProbe.java` | the six-cells probe (1.5): compiled against and run on one server jar's own classes and libraries (`VanillaRegistries`, `RandomState`, `Climate.ParameterList`); prints the target point and all leaves at minimal distance for given quart cells |
| `test_sweep.py`, `test_jfr_order.py`, `test_mutate.py` | the unit tests of self-test 7 |

The Mojang jar is not inside this worktree: `kindling-bench/vanilla-diff/jars/` exists only in
`E:\DEV\Minecraft\cubyx-leaf` (gitignored). The tool takes `--vanilla-jar` and checks the sha1 against Mojang's
manifest through `vanilla-diff/fetch_vanilla.py`.

```bash
JAVA=".../jdk-25/bin/java"   # java on the PATH of this PC is 17
python -I parity.py bootstrap --vanilla-jar <mojang.jar> --locations locations.json --profile dev --out runs/boot
python -I parity.py run --jar <mojang.jar> --bootstrap runs/boot --profile dev --out runs/v1
python -I parity.py run --jar <mojang.jar> --bootstrap runs/boot --profile dev --out runs/v2
python -I parity.py compare runs/v1 runs/v2                      # must print identical, order identical
python -I parity.py run --jar <kindling.jar> --bootstrap runs/boot --profile dev --out runs/k1
python -I parity.py compare runs/v1 runs/k1
python -I parity.py suite --profile standard --jars mojang,paper,leaf,kindling --selfchecks 5   # bench host only
```

---

## 7. Risks, and what the first dev run must settle

In this order; each is a few minutes of generation on a 4 x 4 sweep:

1. **Status radii and wait semantics** in Mojang's jar and in Kindling: the JFR log of one sweep must show exactly the
   nine predicted events per step, each step finished before the next starts. If Moonrise promotes entity ticking
   before the radius-2 chunks are full, the wait needs a second condition; if Moonrise generates more than the pyramid
   requires, the prediction is adapted, as long as each step stays a single chain.
2. **JFR in the Paper family:** `jfr start` exists there (`Commands.java:272`), and the events are registered by
   `JfrProfiler` (`JfrProfiler.java:61-71`); to confirm that `minecraft.ChunkGeneration` actually records under
   Moonrise. Without it the order is not proven, only made likely; the tool would then say so in every report.
3. **Game rule names** in 26.3 (1.2), and the same fix in the existing tool's two modes.
4. **Bootstrap layout** for the nether and the end in the Paper family (CraftBukkit's separate world folders) and the
   round trip: the jar loads the bootstrap, saves without sweeping, and the comparison with the bootstrap is 0 apart
   from documented Paper-only keys.
5. **Entities from generation:** fields drawn from a mob's own unseeded random would make vanilla differ from itself;
   such fields are listed and ignored with their reason, or entities leave the hard-0 scope.
6. **Thread placement:** all generation events but `full` on one thread in every jar. Leaf's patch 0097 ("Faster
   sequencing of futures for chunk structure gen") might move structure work to another executor; the JFR thread field
   shows it. Attribution `Leaf` if so.
7. **Runtime:** 0.15-0.3 s per step is a guess; at 1 s per step the full profile becomes about 6 h per jar and is cut to
   the standard profile.

Residual risks after that: a jar that is only wrong under concurrency passes (3.4); bootstrap content is Mojang's
order, not a "neutral" one (irrelevant for equality, but stated); a difference found in Paper as well is attributed
`Upstream` against `paperCommit`, not against the latest Paper.

---

## 8. Implementation status and corrections from the dev runs (2026-10-30)

Evidence and output tails for each point: IMPL-NOTES.md, "implementation and dev runs".

| Design point | What the dev runs showed | What the tool does now |
| --- | --- | --- |
| Order evidence via `jfr start` (3.1) | `minecraft.ChunkGeneration` is `@Enabled(false)` and not in Mojang's jfc, so `/jfr start` would record nothing | `-XX:StartFlightRecording` with `chunkgen.jfc` from JVM start; records in Mojang's jar and in the Paper family. The Paper family emits 7 statuses (no `light`, no `full`), so their order is proven for Mojang's jar only |
| Wait with `execute if loaded` (3.1) | It follows the ticket level of an already full chunk; the next step started before spawn/full of the previous one | Barrier on (x+2, z+2) with `execute if block`, polled until the chunk is full (never loads), then `if loaded` |
| Post-processing at the block-ticking promotion is covered (3.4) | Mojang's promotion is timing dependent: an unprocessed bootstrap chunk was post-processed in some fast runs only; the last step's promotion raced the save | 0.25 s pause per step, 2 s before saving (5 of 5 and 3 of 3 vanilla pairs identical afterwards); a chunk that only Mojang's side left unprocessed is compared except the footprint of its marks (PARTLY COMPARED, MASKED, exit 3; section 9). Post-processing is therefore covered only where Mojang's own result is stable |
| Self-test 4 via `generate-structures=false` (4) | The property only applies to new worlds | `--without-structures` edits the copy's `world_gen_settings.dat`; still vacuous at dev size (no structure starts in the swept band) |
| Paper reads the bootstrap (risk 4) | Paper/Leaf prompt before importing a vanilla world ("Type 'confirm'") | `-Dpaper.disableMigrationDelay=true`; the import copies level data only |
| Game rules (risk 3) | Old names rejected | 26.3 names here and in `../vanilla-diff` |
| Comparison | Entity attribute order varies per JVM start; Paper family adds Starlight keys, `Purpur.*`, `WorldUUID*`, `"Items": []` | Attributes sorted by id; those keys ignored or normalised, each named in every report |

Settled: status radii and one chain per step (risk 1; 144 = 16 x 9 events in Mojang's jar, 112 = 16 x 7 in Kindling and
Leaf), JFR under Moonrise (risk 2), one generation thread in all three jars at this location (risk 6), Paper family
deterministic under the sweep (Kindling and Leaf each identical with themselves).

Open: nether and end locations and the standard/full location list (only `village_desert` and `lush_caves` are in
`locations.json`); risk 5 at scale (entities from generation agreed at dev size); self-test 3; self-test 4 at a
location with a structure start in the swept band; Paper itself (`fetch_paper.py` not written, so `Leaf` versus
`Upstream` attribution is open); Mode B, `suite`, `climate_probe` not built; the root cause of Mojang's promotion race.


---

## 9. Open review points (second review, 2026-10-30)

Fixed after the review (IMPL-NOTES.md, "review fixes"): the promotion-race exclusion is one-sided (only Mojang's side)
and position-wise instead of chunk-wise; `compare` takes its locations, rectangles and bootstrap from the runs and
refuses runs that cannot be compared; differences no category explains are reported; the report names the jars; the
java and jfr lookup; the bootstrap is never reused; the bind address is checked on disk before every start.

Still open:

1. **The 0 between two Mojang runs is thin evidence.** At the dev location whole-rectangle order differs from canonical
   in one block, so a 0 there says little about whether the order is controlled; the order itself is proven by JFR, not
   by that test. To close it: run the vanilla pair at a feature-dense location (forest, jungle or lush caves) where
   whole-rectangle differs strongly from canonical, at least 5 pairs on the bench host, with `--step-settle`
   0.1 / 0.25 / 1.0 to show the result does not depend on the pause, self-test 3 (a second independent bootstrap), and
   self-test 4 at a location with a structure start in the swept band.
2. **The promotion race is held down by sleeps, not by a barrier.** A late promotion on a loaded host can still flip.
   Between two Mojang runs that shows as a difference (safe). Against another jar it is excused only inside the
   footprint, and the excuse is only valid if Mojang consistently leaves the chunk unprocessed: the vanilla pairs must
   agree on every partly compared chunk. A barrier needs the root cause, which is not found.
3. **The footprint is derived, not measured.** It is the marked positions and their six neighbours (what the vanilla
   code changes: the block at the mark, fluid spreading by one block, ticks at those positions). On the dev data only
   2 fluid ticks were masked inside it. A bigger effect would show up as an unmasked difference in the chunk, which is
   the safe direction. An excused chunk gets no "unexplained" safety net (its post-processing list differs by design);
   the explicit section, biome-container and duplicate-entity checks do apply to it.
4. **Entities from generation** are checked on 19 mobs (risk 5); the bootstrap-loaded entities carry attributes the
   Paper family adds, which is why every Kindling comparison lists 18 entity differences.
5. **The count check is a net, not a diff.** Counts that differ without any named difference are reported, but the
   counts a masked chunk can change (ticks, marks, block entities, sections with blocks) are skipped when something is
   masked.
6. **Java and jfr on the bench host** are fixed by reading and unit tests only; the first run there must show a clean
   `parity.py run`. Only the standard library is used (the host has Python 3.12.3).
7. **Settings an overlay could still change** (Paper's own configs) are not checked; only the bind address and the
   rcon, query and status switches in `server.properties` are.
