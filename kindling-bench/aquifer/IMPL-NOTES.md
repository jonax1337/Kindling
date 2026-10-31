# Implementation notes (running log)

Short entries, newest last. "verified" means the command was run and its output seen.

## Step 0: design (2026-10-30)

- DESIGN.md written from Aquifer.java (Mojang 26.3, unpatched), NoiseChunk, NoiseBasedChunkGenerator,
  NoiseGeneratorSettings and C2ME's MixinAquiferSamplerImpl + RandomUtils. No Java yet, nothing built or run.
- Findings to keep: C2ME's top-4 selection is not exact against 26.3 (tie-break uses y/z/x visit order instead of
  vanilla's x/y/z; 4th slot skipped when key > C); its getFluidBlockY algebra rounds differently; RandomUtils skips
  the Xoroshiro zero-seed fix-up. Only overworld, large_biomes and amplified have aquifers.
- Next: step 1 = World/Workload/PortAquifer/Verifier with VANILLA vs PORT, then techniques one by one.

## Step 1: harness and VANILLA reference (2026-10-30)

- Files: run.sh (LF), java/aquifer/*.java (World, Variant, AquiferInputs, VanillaVariant, Variants, AquiferRun,
  DensityLoader, CallList, ChunkCase, Workload, Replay, EventLog, TraceLogs, TracingSamplerSet, TracingFluidPicker,
  Tally, Verifier, RealChunkCheck, BrokenAquifer, BrokenVariant, SelfTest, Bench, Measurements, Report, Stats, Options, Main).
- Verified (commands run, output seen): `sh run.sh build` exit 0; `sh run.sh selftest` SELF-TEST PASSED (8 broken
  variants rejected, vanilla copy passes); `sh run.sh verify` VERIFY PASSED in 6.3 s (before default bump), 0
  differing calls against a real NoiseChunk; `sh run.sh bench --forks 1 --threads 1` 13.6 s, JSON written, warm verify passed.
- JDK: PATH has JDK 17 first in Git Bash and JAVA_HOME is stale, so run.sh looks for JAVA_HOME, then
  C:/Program Files/Eclipse Adoptium/jdk-25*, then PATH.
- Next: step 2 = candidate/BaseAquifer (exact constructor, statuses) + PortAquifer, verify it, then techniques one by one.

## Step 2: BaseAquifer + PortAquifer (2026-10-30)

- candidate/BaseAquifer (exact constructor, statuses, pressure gradient as static helpers), candidate/PortAquifer (A:205-352
  line by line), CandidateVariant (factory with Config.create's shape). PORT registered.
- Verified: `sh run.sh build` exit 0; `sh run.sh verify --no-random-seed --seeds 0 --presets overworld` VERIFY PASSED (4.8 s),
  PORT 0 differences in FILL/CARVE/COLUMN/SHUFFLE/EDGE.
- Next: C2meAquifer (T1 eager + T1-lazy, T2 ints, T3 exact network, T4 barrier field, T5 split).

## Step 3: C2ME candidates (2026-10-30)

- candidate/C2meAquifer (MIT header): T1 (filledEagerly / filledLazily), T2 (int x,y,z after the BlockPos round trip, one
  interleaved int[]), T3 exact (key = dist << 4 | tieRank, vanilla ranks 11 - visitOrder, unguarded min/max cascade), T4
  (barrier field reset at A:308), T5 (computeSubstance -> selectNearest + substanceOfNearest + substanceBetweenAquifers).
  candidate/C2meBranchyAquifer: same minus T3 (vanilla's if/else insertion on the keys), to isolate T3.
- Variants: PORT, C2ME (eager), C2ME_LAZY, C2ME_BRANCHY. Per-technique variants are ablations from C2ME, not PORT + one
  technique (see DESIGN.md "Findings during implementation").
- Verified: build exit 0; verify --seeds 0 --presets overworld: all four 0 differences in every tally (5.3 s).
- Next: broken/BrokenC2meAquifer mutants in the self-test, then full verify, second seed set, bench smoke.

## Step 4: mutants, verify, smoke (2026-10-30)

- broken/BrokenC2meAquifer (MIT header): TIE_OLDER_WINS, C2ME_TIEBREAK, C2ME_FOURTH_SLOT, STALE_BARRIER, FLAG_KEPT,
  STATUS3_FIRST, SPREAD_DOUBLE, C2ME_FLOOD_ALGEBRA, and C2ME_LITERAL (= C2ME's ranks + fourth slot + T6). SelfTest
  requires all registered candidates to pass; Options --chunks/--columns/--shuffles now also size the self-test.
- Verified: selftest PASSED (small 5 cases and 36 cases); verify default (seeds 0,1,-7,42 + random -3280633976904976672)
  75/75 PASS in 10.5 s; verify --seeds 3,99,-123456789,2026,8675309 --workload-seed 777 --chunks 8 --columns 32 75/75 PASS
  in 11.2 s; bench smoke --rounds 5 --warmup-sec 3: 15.7 s, WARM VERIFY PASSED, results/smoke-c2me/fork-1.json.
- Findings: DESIGN.md section 6.

## Step 5: review findings (2026-10-30)

- All five findings checked against the code; DESIGN.md section 7 has the verdicts. Confirmed: 1 (deep volume never reached the wrap),
  2 (column bench without getBaseHeight's early exit), 3 (two real mutants SIMILARITY_MUL and LAVA_LEVEL_LT unseen; LAVA_NOISE_GE and
  GRADIENT_BOUND are equivalent), 4 (eager fill needs the in-volume contract; documented), 5 (the 15 % gap was noise: not reproduced).
- New: `World.withDeepSea` / `withSeaLevelMinus10` (sea level changed, floodedness x3), groups `DEEP` and `FLOODED`, `CallList.floor`,
  `PartChecks` (similarity, pressure, fluid type and cell locations against Mojang's private methods by reflection), mutants
  `NO_BLOCKPOS_WRAP`, `SIMILARITY_MUL`, `LAVA_LEVEL_LT`, early-exit column metric in `Bench`/`Report`.
- Finding: `NO_BLOCKPOS_WRAP` stays invisible to the replay even with the deep world (a wrapped cell is >= 17 blocks from the lowest block);
  only the location comparison in `PartChecks` rejects it.
- Verified: build exit 0 (no new warnings); selftest PASSED (13.7 s, 20 mutants + 2 proven-equivalent); verify default 168/168 PASS in 14.4 s
  (random seed 5862704204152716968); verify --seeds 3,99,-123456789,2026,8675309 --workload-seed 777 --chunks 8 --columns 32: 168/168 PASS in
  15.4 s; bench smoke --rounds 5 --warmup-sec 3: 15.9 s, WARM VERIFY PASSED, results/smoke-review/fork-1.json.
