# Implementation notes (running log)

Short entries, newest last. "verified" means the command was run and its output seen.

## Step 1: tree builder, COMPILED_DENSE, COMPILED_PRUNE

- Start state (verified 2026-10-30): `sh run.sh build` exit 0; `sh run.sh dump --seed 0` prints Paper's tree (164 nodes,
  5 `InterpolatedFunction$Sampler`, caches 0, 1, 7-15 in the main corner tree).
- Architecture chosen for step 1:
  - Block level stays Paper's own code: the sampler tree above the interpolated functions is rebuilt with Paper's
    records (`SamplerRecords.rebuild`), only the `input` of every `InterpolatedFunction$Sampler` is replaced by a
    `CornerSampler`. So the block level is bit-identical by construction; step 2 (column tiling) replaces exactly this
    rebuilt tree (seam: `CompiledSampler.blockLevel`).
  - `CornerSampler.sampleVolume` runs our node graph on the corner volume Paper hands it. `sampleValue` calls Paper.
  - Node graph per sampler instance (one per world and thread, as `Variant.sampler` is called), so nodes may hold
    per-thread state (cache cells, counters) without a separate frame lookup. Deviation from DESIGN (shared immutable
    graph + Frame): simpler, same semantics; to revisit when moving into the server.
  - Scratch buffers come from the context's arena (Paper pays the same per node), so `PoisonedArena` poisons them in
    verify. Sub-volume leaf calls use one `ResizableBuffer` per frame (does not override any `DensityBuffer` method).
- Implemented (eval/): Node, Demand, Frame, Tuning, PruneCounter, ResizableBuffer, LeafCalls, Purity, TreeBuilder,
  CornerSampler, CompiledSampler; nodes Const, Unary (incl. clamp, squeeze), Binary, ConstBinary, RangeChoice,
  ConstRangeChoice, Lerp (3 kinds, const side = ConstNode), IntervalSelect (both kinds), Slice (X/Y/Z/Xz), Cache,
  Blend, Opaque. Harness: CompiledVariant (COMPILED_DENSE, COMPILED_PRUNE), Variant.diagnostics(), counters printed
  after verify and bench. run.sh build now uses a javac @argfile (the command line got too long on Windows).
- IntervalSelect is pruned too (not in DESIGN v1): it is the same select-per-element pattern as RangeChoice and the
  spaghetti/entrance rarity selects sit inside the caves graph; leaving it opaque would evaluate all its noises.
- Verified (small): `verify --seed 0 --chunks 8 --square 0 --far 1 --fuzz 4 --columns 4 --boxes 4` -> COMPILED_DENSE
  and COMPILED_PRUNE 0 mismatches in [CHUNK], [probe], [COLUMN], [BOX], VERIFY PASSED.
- Mutation checks (verified, reverted): lerp first-side demand on alpha != 0 instead of != 1 -> COMPILED_PRUNE
  255788 mismatches [CHUNK], VERIFY FAILED, DENSE still 0; CacheNode filling only on first use -> 364961
  mismatches, VERIFY FAILED. So verify does catch pruning and cache-mask bugs.
- Acceptance (verified 2026-10-30): build exit 0 (only the known `Contract` classfile warning); selftest SELF-TEST
  PASSED (DENSE and PRUNE pass on the self-test chunks); `verify` (3 seeds, 700 calls) and `verify --seed 7` (308
  calls): DENSE and PRUNE 0 mismatches in [CHUNK], [probe], [COLUMN], [BOX], VERIFY PASSED.
- Counters, default verify: caves range choice in 52.1 % / out 27.6 % of corners (20.3 % skipped by the slides);
  BlendedNoise lerp first 45.2 % / second 45.4 %; leaf points 17.69 M evaluated vs 33.36 M in DENSE (-47 %).
- Bench smoke (SMOKE ONLY, 1 fork, 1 thread, 3 rounds, 84 chunks, seed 0, 13 s): PAPER 1.686 ms/chunk, DENSE 1.724
  (0.978x), PRUNE 1.350 (1.249x); WARM VERIFY PASSED.
- Open for step 2: column tiling at `CompiledSampler.blockLevel`; `PURITY` check; `--call-cost`/`--run-gap` options
  (Tuning.DEFAULT = 8, 1 for now); per-node arena traffic (scratch still comes from the context arena).

## Step 2: column tiling (COMPILED_TILE, COMPILED)

- Start state (verified 2026-10-30): build exit 0; default verify VERIFY PASSED, DENSE and PRUNE 0 mismatches in every
  tally. Block level of the overworld (dump): add(min(squeeze(I0), range_choice(I1, [-1e6, 0), 64, add(I2,
  mul(max(abs(I3), abs(I4)), 1.5)))), context_bound(beardifier)); I0..I4 interpolated 4/8.
- Built (eval/): Techniques (pruning, tiling), BlockLevelRebuild (moved out of CompiledSampler, exposes cornerOf),
  TileFrame, Tile, ConstTile, UnaryTile, ConstBinaryTile, BinaryTile, ConstRangeChoiceTile, RangeChoiceTile,
  InterpolatedTile, FullVolumeTile, TileBuilder, TileStage. Element loops of the step-1 nodes made package static and
  reused (UnaryNode.apply, ConstBinaryNode.apply, BinaryNode.combine, ConstRangeChoiceNode.choose).
- Small verify (seed 0, 8 chunks, far 1, fuzz 4, columns 8, boxes 8): all four COMPILED variants 0 mismatches in
  [CHUNK], [probe], [COLUMN], [BOX], VERIFY PASSED; counters show 29 tiled calls (13 chunks + 8 columns + 8 boxes).
- Mutation checks (verified, reverted): InterpolatedTile writing lower + valueStep * y instead of accumulating ->
  TILE and COMPILED 285610 [CHUNK], 700 [COLUMN], 57028 [BOX] mismatches, VERIFY FAILED, DENSE/PRUNE 0;
  RangeChoiceTile skipping whenInRange unless more than half the column is in range -> COMPILED 37385 [CHUNK],
  30338 [BOX] mismatches, TILE 0. So verify catches both interpolation order and block-level pruning mistakes.
- Frame call stamp made long (an int could wrap into a tile's initial -1 after 2^31 calls).
- Acceptance (verified 2026-10-30, after the last code change): build exit 0 (only the `Contract` classfile
  warning); selftest SELF-TEST PASSED (all six candidates pass); `verify` (3 seeds, 700 calls) and `verify --seed 7`
  (308 calls): DENSE, PRUNE, TILE and COMPILED 0 mismatches in [CHUNK], [probe], [COLUMN], [BOX], VERIFY PASSED.
  All 700 calls were tiled (block interpolated counters: 700 calls each). COMPILED counters: noodle corner volumes
  skipped in 88 of 700 calls; noodle whenOutOfRange skipped in 15.2 % of columns, whenInRange in 0.1 %.
- Bench smoke (SMOKE ONLY: 1 fork, 1 thread, warm-up 3 s, 3 rounds, seed 0, 13 s): PAPER 1.715 ms/chunk, DENSE 1.783
  (0.962x), PRUNE 1.382 (1.241x), TILE 1.952 (0.878x), COMPILED 1.554 (1.104x); WARM VERIFY PASSED. In this smoke the
  block stage costs time instead of saving it (TILE slower than DENSE, COMPILED slower than PRUNE). Not a
  measurement; a host run decides. Suspects, not checked: per-column dispatch through ~13 tiles x 256 columns, the
  switch-based element loops shared with the corner level (one JIT profile for 1,225- and 384-float sizes), the z/x
  lerps recomputed per column and level, and the column copy into the output.

## Review round 2 (verifier coverage; verified 2026-10-30)

- Findings 1, 2, 5 (verifier coverage) confirmed and fixed; 3, 4, 6 are risks: partly fixed or documented as open
  review points 9, 10, 12 in DESIGN.md.
- New: `SyntheticSuite` (659 synthetic trees x 7 volumes, part of every verify, `--synthetic-only` runs it alone);
  `Verifier` repeat pass (`[repeat]` tally); `--tuning callCost,runGap`; stepped and cell-aligned point boxes and
  structures under COLUMN and BOX volumes in `Workload.verifyOnly` (default `--boxes` 16 -> 48); `run.sh mutants` +
  `mutants.sh` (11 mutants, all killed); self-test variant `SKIPS_REPEATED_VOLUME`; `CompiledSampler` thread
  confinement (throws on a second thread).
- The first mutant list had one survivor, `lerp_nan_alpha_skips_sides`: equivalent, since Mth.lerp(NaN, a, b) is NaN
  whatever the sides hold. Replaced by `lerp_alpha_one_inclusive` and `lerp_first_side_skipped_at_alpha_zero`.
- The cache mutant (CacheNode trusts a finished context) is caught by `[repeat]` and by the synthetic `[BOX]` tally
  (the single cell-aligned point boxes come back with the same volume), not by `[CHUNK]`.
- No evaluator bug found: every synthetic tree, every tuning (8/1, 0/0, 100000/8), all four COMPILED variants are
  bit-identical, including NaN payloads (0 payload differences).
