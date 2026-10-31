# Worldgen parity: work log

Newest entries at the bottom. Each entry says what was done and what was seen; "read" means read in code, "ran" means
executed, with the output tail.

## 2026-10-30, design stage

No server was started, nothing was downloaded, nothing was committed.

- Read `kindling-bench/vanilla-diff` (README, run.sh, worldgen_script.py, compare_worlds.py, worlddiff.py, ignored.py)
  and `docs/measurements/vanilla-parity.md`. The 410/624 vanilla self-check and the six biome cells (new build against
  vanilla, not vanilla against itself; ROADMAP line on the worldgen diff) come from there. The old run folders were
  deleted, so the six cells' coordinates are not available.
- The Mojang jar named in the task, `kindling-wt/worldgen-parity/kindling-bench/vanilla-diff/jars/minecraft-server-26.3.jar`,
  does not exist in this worktree (ran `ls`: "No such file or directory"). It exists at
  `E:\DEV\Minecraft\cubyx-leaf\kindling-bench\vanilla-diff\jars\minecraft-server-26.3.jar`.
- `java` on the PATH is 17 (ran `java -version`: `openjdk version "17.0.20.1"`); Java 25 is at
  `C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot`. The tool must take `JAVA` explicitly.
- Extracted `META-INF/versions/26.3/server-26.3.jar` from the Mojang bundler into the session scratchpad and
  disassembled classes with the JDK's `javap -c -p` (reading, not running, the jar). Seen:
  - `ChunkMap.<init>`: `new ConsecutiveExecutor(executor, "worldgen")`, wrapped in `ChunkTaskDispatcher`;
    `runGenerationTask` → `worldgenTaskDispatcher.submit(...)`.
  - `Util.maxAllowedExecutorThreads`:
    ```
    0: invokestatic  Runtime.getRuntime  3: availableProcessors  6: iconst_1  7: isub  8: iconst_1
    9: invokestatic  getMaxThreads:()I   12: invokestatic Mth.clamp:(III)I   15: ireturn
    ```
    and `getMaxThreads` reads `max.bg.threads` (1..255, default 255).
  - `NoiseBasedChunkGenerator` and `ChunkGenerator` call `Util.backgroundExecutor().forName(...)` with
    `CompletableFuture.supplyAsync` (terrain and biomes are asynchronous in Mojang's jar).
  - `Climate$ParameterList.findValue` → `findValueIndex` (the R-tree search with the thread-local last result).
- Read in the applied sources (cubyx-leaf, read only): the pyramid and tasks, `ChunkGenerationTask`,
  `GenerationChunkHolder` (Paper stubs), `ChunkUpgradeGenericStatusTask` (Moonrise calls `ChunkStep.apply`),
  `ChunkStep.apply` (JFR event), `ChunkGenerationEvent` fields, `JfrProfiler` event list, `Commands.java:272`
  (`JfrCommand` registered), `ExecuteCommand.isChunkLoaded` (ENTITY_TICKING), `ChunkLevel`, `ChunkMap.FORCED_TICKET_LEVEL`,
  `ServerLevel` tick-freeze gate, `ServerChunkCache.tick`, `Climate` (vanilla and Moonrise search),
  `OverworldBiomeBuilder` underground biomes, `LevelChunkSection.fillBiomesFromNoise` (Paper's MC-241546 comment),
  `MoonriseCommon.adjustWorkerThreads`, `GlobalConfiguration.ChunkSystem`, `Util` (Paper), Leaf's `FastRNG` and
  `SecureSeed` options, `GameRules` (snake-case ids in 26.3).
- Finding for the existing tool, not yet confirmed by a run: `contraptions.py` and `worldgen_script.py` send the
  pre-26 game rule names (`randomTickSpeed`, `doMobSpawning`, ...), while 26.3 registers `random_tick_speed`,
  `spawn_mobs`, ... (`GameRules.java:24-92`). If the server rejects the old names, those rules were never set.
- Wrote `DESIGN.md`.

Next: the dev-run checklist in DESIGN.md section 7, on a 4 x 4 sweep, Mojang's jar first.

## 2026-10-30, implementation and dev runs

Everything below ran on this PC with Java 25 (`C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot`), one server at
a time, each on 127.0.0.1:25999 (checked by `server.py` before start, by binding the port, and after start: "Starting
Minecraft server on 127.0.0.1:25999"). Location `village_desert`, 4 x 4 sweep, chunks x 186..189, z 137..140, found
with `locate structure #minecraft:village` from 3200 64 3200 in a throwaway run. Nothing committed.

### Built

`parity.py` (bootstrap, run, compare, plant), `server.py`, `driver.py`, `runs.py`, `sweep.py` + `locations.json`,
`jfr_order.py` + `chunkgen.jfc`, `worldcopy.py`, `compare_all.py`, `report.py`, `nbtwrite.py`, `mutate_region.py`,
`paths.py`, tests `test_sweep.py`, `test_jfr_order.py`, `test_mutate.py`, `overlays/faster-random-worldgen/`.
Changed in `../vanilla-diff`: `chunkdiff.py` (three helpers made public: `value_changes`, `without_volatile`,
`shorten`; no behaviour change), `ignored.py` (also drops `WorldUUIDLeast/Most` and the `Purpur.` prefix),
`contraptions.py` and `worldgen_script.py` (26.3 game rule names). Not built: Mode B (`terrain_pack.py`),
`fetch_paper.py`, `suite`, `climate_probe/`.

### Findings, in the order they came up

1. **Old game rule names are rejected** (risk 3, settled). Probe run of Mojang's jar:
   ```
   [13:39:50] [Server thread/INFO]: System chat: Incorrect argument for command
   [13:39:50] [Server thread/INFO]: System chat: gamerule doMobSpawning false<--[HERE]
   [13:39:51] [Server thread/INFO]: System chat: Game rule spawn_mobs is now set to false
   ```
   So the existing contraptions and worldgen modes never set their rules. Both now send the 26.3 names (`doFireTick`
   has no direct counterpart and was dropped). The contraptions mode was **not** re-run after this change.
2. **`/jfr start` cannot record the order.** `ChunkGenerationEvent` is `@Enabled(false)` and Mojang's
   `flightrecorder-config.jfc` does not enable it. The tool starts a recording with the JVM instead
   (`-XX:StartFlightRecording` with `chunkgen.jfc`, which enables only that event); it records in Mojang's jar and in
   the Paper family (risk 2, settled). Moonrise emits 7 of the 9 statuses (no `light`, no `full`: its own tasks), so
   the order of light and full is proven for Mojang's jar only.
3. **`execute if loaded` is not a step barrier.** It tests the ticket level of a full chunk, not the promotion. First
   canonical run: "event sequence differs ... #16: predicted ('spawn', (189, 139)), recorded ('structure_starts',
   (201, 150))": the next step began before spawn and full of the previous one. The barrier is now
   `execute if block` on (x+2, z+2), polled until it stops answering "That position is not loaded" (it never loads; a
   first version assumed a synchronous load and hung for 300 s in one run).
4. **Status radii confirmed** (risk 1): with the barrier every Mojang run records exactly 144 events (16 steps x 9),
   each step one chain at (x+13), (x+5), (x+5), (x+4), (x+3), (x+3), (x+2), (x+2), (x+2), all on `Worker-Main-1`
   except `full` on `Server thread`. Kindling and Leaf: exactly 112 events (16 x 7), all on `Paper Common Worker #0`
   (risk 6 settled for this location: no second executor seen).
5. **Entity attribute order is random per JVM start.** First vanilla pair: same attributes, different list order
   (a hash map keyed by holders). The comparer sorts the list by id.
6. **Mojang's block-ticking promotion is timing dependent.** Without pauses, chunk (187, 140) (full but unprocessed in
   the bootstrap) was post-processed in some vanilla runs and not in others (4 of 14 runs differed from the rest), and
   with a save after every step it was never post-processed (4 of 4). Separately, the promotion of (x1+1, z1+1) after
   the last step raced the save ((190, 141): 315 marks left in 2 of 4 runs). With a 0.25 s pause after each step and
   2 s before saving, 5 of 5 vanilla runs agreed (the chunk is then never post-processed, consistently). The Paper
   family post-processes it; the comparer therefore excludes a chunk on exactly this signature (full on both sides,
   one side still holds the bootstrap's PostProcessing list unchanged, the other none) and lists it as NOT COMPARED.
   Root cause in Mojang's code not found; `ChunkHolder.updateFutures`, `DistanceManager.runAllUpdates` (two passes)
   and `ChunkMap.getChunkRangeFuture` were read with javap and did not show it.
7. **Paper asks before importing a vanilla world.** `WorldFolderMigration` ("Type 'confirm' to continue", Leaf's
   change of Paper's 30 s delay) blocked the first Kindling start; `-Dpaper.disableMigrationDelay=true` skips it. The
   import copies level-wide saved data; region files are not touched (read in `VanillaWorldMigration`). That failed
   start also showed that `Server.start` did not kill the process on a timeout; it does now (the stuck server, PID
   19928, was stopped by hand; the owner's other JVMs were left alone).
8. **Fields only the Paper family writes**: `starlight.light_version`, `starlight.blocklight_state`,
   `starlight.skylight_state` (light bookkeeping), entity keys `Purpur.*`, `WorldUUIDLeast/Most`, and `"Items": []` on
   empty containers (Mojang omits the key). Ignored or normalised, each listed in every report.
9. **Self-test 4 as designed is vacuous.** `generate-structures=false` in server.properties only applies to new
   worlds; the copied world keeps `data/minecraft/world_gen_settings.dat`. `--without-structures` now switches that
   file off (checked after the run: `generate_structures 0`), but the result was still identical: the dev sweep only
   computes structure starts for chunks (199..202, 150..153), where no structure starts anyway. It needs a location
   where a structure starts inside the swept band.

### Acceptance runs (final code; `runs/final/`)

Unit tests:
```
python -I test_sweep.py         Ran 6 tests   OK
python -I test_jfr_order.py     Ran 13 tests  OK
python -I test_mutate.py        Ran 17 tests  OK
python -I ../vanilla-diff/test_compare_worlds.py   Ran 39 tests  OK
```
Mojang's jar against itself (v1, v2, v3 with the final runner; v4 after the last `driver.py` refactor):
```
compare runs/final/v1 runs/final/v2   RESULT: identical in everything compared   VERDICT: identical   (exit 0)
compare runs/final/v1 runs/final/v3   RESULT: identical in everything compared   VERDICT: identical   (exit 0)
compare runs/final/v1 runs/final/v4   RESULT: identical in everything compared   VERDICT: identical
compared (A / B): block entities 22/22, distinct biomes 8/8, post-processing marks 9989/9989, scheduled ticks
1409/1409, sections with blocks 1443/1443, status full 81/81, initialize_light 40/40, terrain 48/48, biomes 56/56,
structure_starts 736/736, structure references 211/211, structure starts 9/9, stored entities 19/19
```
Self-test 2 (`--order whole-rectangle`): "ORDER CHECK FAILED ... #9: predicted ('structure_starts', (200, 150)),
recorded ('structure_starts', (199, 152))", exit 2; its world differs from v1 in 1 block.
Self-test 5 (Kindling with `overlays/faster-random-worldgen`, round 3): against Kindling without it, "14551
differences in 18 chunks; block 14508, block entity 1, heightmap 6, post-processing 35, scheduled tick 1".
Self-test 6 (`plant runs/final/v1`): planted 5 (block, biome, structure reference, scheduled tick, entity Health),
reported exactly those 5, "SELF-TEST 6: passed".
Self-test 3 (second independent bootstrap): not run.
Paper-family determinism (round 3): Kindling k1 against k2 identical, plain Leaf l1 against l2 identical.

Kindling (`leaf-paperclip-26.3.local-SNAPSHOT.jar`, "Kindling 26.3-DEV-work/kindling-hardening@23a2629") against
Mojang (`compare runs/final/v1 runs/final/k1`, exit 1):
```
order B: canonical order proven; 112 generation events near the location
NOT COMPARED: chunk (187, 140): block-ticking promotion never post-processed it in A
RESULT: 20 differences in 8 chunks      by category: block 2, entity 18
  chunk (193, 143) [computed: structure_references,biomes,terrain]: block: (3101, -36, 2292) minecraft:air -> minecraft:deepslate
  chunk (193, 144) [computed: structure_references,biomes,terrain]: block: (3102, -8, 2310) minecraft:air -> minecraft:deepslate
  18 entities loaded from the bootstrap: attributes Mojang does not write, all at their base value without modifiers,
  e.g. villager "attribute minecraft:max_health only in B (base 20.0, no modifiers); minecraft:scale (1.0); minecraft:tempt_range"
```
Plain Leaf 66f99f8e against Mojang (round 3): the same 2 terrain blocks and 18 entities, plus 8 leaf `distance`
differences in chunks (192, 143) and (192, 144) that Kindling does not have. Attribution so far: the 2 terrain blocks
and the attribute entries are in Leaf too, so `Leaf` or `Upstream` (Paper was not run); the leaf distances are `Leaf`
and absent in Kindling. Nothing was fixed in Kindling.

### Runtimes on this PC

Bootstrap 15 s. Mojang run 19 s wall (start 7.2-7.5 s, sweep 7.6 s for 16 steps, of which 4 s are the settle pauses
and 2 s the final pause). Kindling/Leaf run 23 s (start 12 s, sweep 7.2 s). Comparison 2-3 s. Without pauses a step
took 0.03-0.15 s after the first (1.8-2.7 s, loading the bootstrap), so the 0.25 s pause dominates: a 24 x 24 location
is about 576 x 0.35 s = 3.4 min of sweep per jar; the standard profile (6 locations) about 25 min per jar including
starts.


## 2026-10-30, review fixes

Ran on this PC (Java 25, Mojang's jar, 127.0.0.1:25999 only, one server at a time, port free before and after).
Nothing committed. New tests: `test_review_fixes.py` (27 tests); `test_mutate.py` now has 20. The steps below that
name planted tests were run in scratch copies of `runs/final` outside the repository.

- **Finding 1 (promotion-race exclusion), confirmed and fixed.** `promotion_races` never asked which side was Mojang.
  Now `compare_location(..., mojang_sides)` takes it from the runs' `family`; a chunk is only excused when Mojang's side
  is the unprocessed one and the other side is not Mojang. It is not dropped: it is compared except the positions of
  the marks and their six neighbours, the heightmap columns above them and the marks themselves; differences inside
  are listed as MASKED. The verdict "identical except ..." is exit 3, never exit 0. Planted: a block in (187, 140)
  outside the footprint is now found (21 differences, `block 3`; before 20); a jar under test that never
  post-processed 6 chunks (+1 block each) gives `2907 differences ... post-processing 2881`, VERDICT different
  (before: identical, 6 chunks not compared). Real data, `compare runs/final/v1 runs/final/k1`: still exactly the 2
  terrain blocks and 18 entities, with chunk (187, 140) now compared apart from 452 positions; only 2 fluid ticks were
  masked in it, so the footprint covered everything post-processing changed there.
  Found on the way: `_post_processing_position` decoded the packed marks as `x | z << 4 | y << 8`;
  `ProtoChunk.packOffsetCoordinates` is `x | y << 4 | z << 8` (read in ProtoChunk.java:302), so y and z in
  "post-processing" differences were swapped. Fixed, with a test.
- **Finding 2 (profile instead of the runs), confirmed and fixed.** `compare` and `plant` read locations and
  rectangles from each run's `run.json`; `--profile` and `--locations` are optional and refused when they disagree
  (`compare ... --profile standard` on dev runs: "error: ... the runs swept village_desert ... x 186..189 ..., the
  given profile or locations file says lush_caves ...", exit 2).
- **Finding 3 (unexplained differences), confirmed and fixed.** Planted in copies of v2, compared with v1: biomes
  container deleted gives `biome: section -4: biomes container only in A`; top section removed gives `section 19 only
  in A`; a second block entity gives `block entity: (2995, 64, 2226) listed 2 times in B`. All three were
  "identical" before. `diff_chunk` also yields "unexplained" when the normalised chunks differ and no category names
  it (palette order and list order are normalised away first), and count mismatches are reported when no difference
  was found. Real data: the Paper family writes empty light-only sections at Y -5 and 20; a first version flagged 64
  chunks for it, so a section that holds nothing but its Y and ignored light data counts as absent.
- **Finding 4 (what was compared), confirmed and fixed.** The report prints each side's jar, sha256, family, the
  server's version line, java, variant, pauses and bootstrap hash. `compare v1 v1` is refused ("A and B are the same
  folder"), byte-identical region files are refused, different bootstraps or variants are refused unless asked for.
- **Finding 5 (jfr and java), confirmed by reading, fixed, not run on the host.** `resolve_java` looks a bare name up
  on the PATH and follows symlinks, `jfr` is derived from the resolved path, `--jfr` overrides, a missing jfr stops
  the run before the server starts, and the default java is `$JAVA`, else the Adoptium path if it exists, else `java`.
  The failing symlink case itself (host: /usr/local/bin/java -> /opt/jdk-25/bin/java) needs the host and is untested
  here; the unit test only covers the derivation from a resolved path.
- **Finding 6 (thin evidence for the 0), documented, not closed.** Added `--step-settle` and `--end-settle`
  (recorded in run.json) so the sleeps can be varied; the rest needs the bench host (DESIGN.md, open review points).
- **Finding 7 (bootstrap reuse), confirmed and fixed.** `bootstrap` refuses an existing folder; the bootstrap run
  records `world_sha256` in bootstrap.json, `run` refuses a bootstrap that changed since, every run.json carries
  `bootstrap_sha256`, and `compare` checks it (runs made before this have no hash: a warning, not a refusal).
- **Finding 8 (overlay and bind address), confirmed and fixed.** An overlay that contains `server.properties` is
  refused before anything is created; the overlay is applied before `server.properties` is written; and
  `Server.start` reads the file from disk and refuses anything but 127.0.0.1:25999 with rcon, query and status off.

Acceptance runs after the fixes (`runs/review/`):
```
bootstrap runs/review/boot      second call: error: ... exists; a bootstrap always starts from an empty folder
run v1, v2 (Mojang, dev)        144 events each, canonical order proven; console: Starting Minecraft server on 127.0.0.1:25999
compare runs/review/v1 runs/review/v2   RESULT: identical in everything compared   VERDICT: identical   (exit 0)
plant runs/review/v1                    SELF-TEST 6: passed, exactly the planted changes were reported
compare runs/final/v1 runs/final/v2     VERDICT: identical (old runs: bootstrap hash warning)
compare runs/final/v1 runs/final/k1     20 differences in 8 chunks (block 2, entity 18), PARTLY COMPARED (187, 140)
```
