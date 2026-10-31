# C2ME aquifer port: design

Goal: measure whether C2ME's `NoiseBasedAquifer` optimisation, ported to 26.3, makes `computeSubstance` faster
while giving exactly vanilla's results. Parity is the hard requirement: a technique that cannot be shown exact is
dropped, not tuned.

Path abbreviations: `A` = `leaf-server/src/minecraft/java/net/minecraft/world/level/levelgen/Aquifer.java`,
`NBCG` = `.../levelgen/NoiseBasedChunkGenerator.java`, `NC` = `.../levelgen/NoiseChunk.java`,
`NGS` = `.../levelgen/NoiseGeneratorSettings.java`, `M` = C2ME's `MixinAquiferSamplerImpl.java` (dev/26.3.0
@2c19de87, yarn names), `RU` = C2ME's `c2me-opts-worldgen-general/.../random_instances/RandomUtils.java`.
All `A` lines are Mojang's 26.3 code; no Paper, Leaf or Kindling patch touches `Aquifer.java`.

## 1. How 26.3 uses the aquifer

### Who creates it, with which inputs

- `NC:19-51`: every `NoiseChunk` builds one `SamplerContext` with caches enabled, the chunk's buffer arena and the
  beardifier/blender user fields (`NC:41-43`), and from it the `DensitySamplerSet cachingSamplers`. If the settings
  have aquifers it calls `Aquifer.Config.create(cachingSamplers, randomState.getOrCreateRandomFactory("minecraft:aquifer"),
  volume, globalFluidPicker)` (`NC:44-47`), otherwise `Aquifer.createDisabled(globalFluidPicker)` (`NC:48-50`, `A:27-39`).
- `A:65-72`: `Config.create` is the single construction point of `NoiseBasedAquifer` (constructor private, `A:130`).
  It is the hook for a future patch, and it covers all three callers below.
- Inputs:
  - router functions: the six `Config` functions (`A:45-52`), built in `NoiseRouterData.overworldAquifers`
    (`NoiseRouterData.java:390-401`): barrier = noise(aquifer_barrier, 0.5), floodedness = noise(..., 0.67), spread =
    noise(..., 0.714...), lava = noise(aquifer_lava), exclusion = `deepDarkRegion(erosion, depth)`, surfaceLevel =
    `preliminarySurfaceLevel`. Bound through the chunk's context (`A:137-142`); `DensitySampler.Bound.sampleValue`
    returns **float** (`densityfunction/DensitySampler.java:29-32`).
  - positional random: `RandomState.getOrCreateRandomFactory` = `random.fromHashOf(name).forkPositional()`
    (`RandomState.java:124-125`); Xoroshiro for all vanilla aquifer settings. `XoroshiroPositionalRandomFactory.at`
    builds a fresh `XoroshiroRandomSource(getSeed(x,y,z) ^ seedLo, seedHi)` (`XoroshiroRandomSource.java:122-126`),
    so every `at()` is a pure function of (x, y, z). Legacy (`LegacyRandomSource.java:64-68`) is pure too.
  - fluid picker: `NBCG:80-92`, a lambda: `y < min(-54, seaLevel) ? lava(-54) : sea(seaLevel, defaultFluid)`, or the
    empty status under `DEBUG_DISABLE_FLUID_GENERATION` (`SharedConstants.java:102`, false unless debug is enabled).
  - volume: chunk `16 x height x 16` (`NBCG:111-114`) or column `1 x height x 1` (`NBCG:183`).
- Which settings have aquifers: overworld, large_biomes, amplified (`NGS:77-79`, `NGS:155`, legacy random false
  `NGS:156`). end, nether, caves, floating_islands pass `Optional.empty()` (`NGS:97`, `119`, `184`, `201`) and use
  the disabled aquifer; they are out of scope (the patch would not change them).

### Construction-time work (`A:130-163`)

1. Six `cachingSamplers.get(...)` (`A:137-142`).
2. Grid bounds (`A:144-153`): for a chunk at block X `16k`: gridX `k-1..k+1` (3), same for Z, gridY
   `floorDiv(minY+1,12)-1 .. floorDiv(maxY+1,12)+1`: overworld -64..319 -> -7..27 (35). 315 cells.
3. `aquiferCache` (FluidStatus[]) and `aquiferLocationCache` (long[], filled with `Long.MAX_VALUE`) (`A:155-157`).
4. `maxSurfaceLevel` (`A:158-160`, `A:172-196`): one `surfaceLevel.sampleVolume` over the quart grid covering
   `[fromGridX(minGridX,0), fromGridX(maxGridX,9)]` x the same in Z (about 12 x 12 points for a chunk), each value
   floored and put into `surfaceLevelCache` (Long2IntOpenHashMap), keeping the maximum.
5. `skipSamplingAboveY = fromGridY(gridY(maxAdjusted + 12) + 1, 11) - 1` (`A:161-162`).

### Call order during terrain generation

- `NBCG:399-433` per chunk: NoiseChunk (aquifer constructed) -> `doFill` -> `buildSurface` -> carvers.
- `doFill` (`NBCG:440-478`): `finalDensity.sampleVolume(volume)` once (`NBCG:448`), then **z outer, x middle,
  y descending** (`NBCG:449-455`), `computeSubstance(blockX, blockY, blockZ, density)` with the float density
  widened to double (`NBCG:458-459`). The flag is read after the call only for non-air fluid states
  (`NBCG:465-472`) and marks the position for post-processing (scheduled fluid ticks: observable in the saved chunk).
- carving mask (`NBCG:288-352`): same aquifer instance, after doFill, density **0.0** (`NBCG:317`), columns in
  `CarvingMask.visit` order, y descending (`NBCG:308`), flag read at `NBCG:320`.
- column (`NBCG:170-215`, used by `getBaseHeight`/`getBaseColumn`): a fresh NoiseChunk and aquifer per column
  (`NBCG:192`), y descending (`NBCG:198-201`), early exit at the first block the tester accepts (`NBCG:207-209`).
  Construction cost matters here: one full constructor (with `maxSurfaceLevel`) for a handful of calls.

### `computeSubstance` (`A:205-352`)

Early exits: `density > 0` -> null (`A:207-210`); `blockY > skipSamplingAboveY` -> global fluid (`A:212-216`);
global fluid is lava (y < -54) -> lava (`A:218-221`). Otherwise: 12 candidate cells, **x1 outer (0..1), y1 middle
(-1..1), z1 inner (0..1)** (`A:235-237`); per cell the location is computed lazily (`A:242-254`) and the squared
distance (`A:256-259`) is inserted into a 4-slot list with `>=` (`A:260-284`), so on equal distance the **later
visited cell ranks first**. Then statuses and barriers (`A:289-350`).

### Lazy, order-dependent state

| State | Where | Filled by | Side effects when filled |
| --- | --- | --- | --- |
| `aquiferLocationCache[i]` | `A:242-254` | first visit of cell i | one `positionalRandomFactory.at` + 3 `nextInt`; pure, no sampler calls |
| `aquiferCache[i]` (FluidStatus) | `A:452-462` | first `getAquiferStatus(i)` | `computeFluid` (`A:464-500`): up to 13 `surfaceLevel` lookups, fluid picker calls, `exclusion`, `floodedness`, `spread`, `lava` sampler calls |
| `surfaceLevelCache` | `A:165-170` | constructor (quart grid) + misses | `surfaceLevel.sampleValue` on a miss; a capturing lambda per lookup (`A:169`) |
| `shouldScheduleFluidUpdate` | every return path | every call | overwritten on every path (`A:208, 214, 219, 296/298, 304, 312, 323, 334, 343/347`) |
| barrier noise value | `A:308`, `A:409-420` | per call, first `calculatePressure` with gradient in [-2, 2] | one `barrier.sampleValue`; NaN means "not sampled" |

All cached values are pure functions of their key, so results do not depend on call order; the order only decides
**which sampler calls happen when**. Status lookups happen in this exact order: status1 (`A:289`); status2 at
`A:295` (similarity12 in [FLOWING, 0]) or at `A:309` after the water-over-lava picker call (`A:303`); status3
**unconditionally** at `A:316`; status4 only in the short-circuit at `A:343-345`. A candidate keeps that order, so
the shared sampler context sees the identical call sequence (it is shared with finalDensity and the surface system).

## 2. C2ME's techniques mapped onto 26.3

| # | Technique (C2ME) | 26.3 target | Expected gain | Verdict |
| --- | --- | --- | --- | --- |
| T1 | Eager location fill in the constructor, `M:139-176` | `A:242-254` | removes the lazy branch and the `BlockPos` unpack per candidate (12 per sampled block) | **exact** (with the BlockPos rule below); costs ~315 `at()` per chunk, a likely regression for columns |
| T2 | Packed short offsets `(r0<<8)\|(r1<<4)\|r2`, distance from grid coords, `M:167`, `M:313-317` | `A:256-259` | cheaper distance | **exact only inside the BlockPos range**; replaced by stored unpacked ints |
| T3 | Branch-free top-4 via packed int keys + min/max network, `M:295-369` | `A:226-287` | removes data-dependent branches (largest expected win) | **C2ME is NOT exact** (two deviations); exact after the fix below |
| T4 | Barrier value in a field instead of `new MutableDouble`, `M:243-245`, `M:471-488` | `A:308`, `A:409-420` | no allocation where escape analysis fails | **exact** |
| T5 | Method splitting for C2 inlining, `M:206-293` | `A:205-352` | uncertain, JIT dependent | **exact** (no semantic change) |
| T6 | `getFluidBlockY` overwrite: thresholds inlined algebraically, `M:380-405` | `A:506-540` | negligible (per cell status, <= 315 per chunk) | **NOT exact: dropped** |
| T7 | `getFluidBlockState`: `>> 6` instead of `floorDiv(.., 64)`, `M:517-531` | `A:556-571` | negligible | exact, but not worth a change: dropped |
| T8 | Reused random via `RandomUtils.derive`, `M:150`, `M:158`, `RU:44-59` | `A:247` | one allocation less per cell | **NOT exact in a corner case and needs private access: dropped** |

Exactness arguments and deviations:

- **T1.** Locations are a pure function of the grid coordinates (`at()` allocates a fresh source, see above), and
  the location fill makes no sampler or fluid picker calls, so filling early changes no value and no sampler
  sequence. `getAquiferStatus` reads the location from the same array (`A:458`), which eager fill guarantees.
  Cost control: cells above `gridY(skipSamplingAboveY + 1) + 1` can never be visited (they need blockY <= skip), so
  only rows up to that one are filled. Columns: vanilla visits only the few cells near the first air block below the
  surface; eager fill does ~15-20 rows x 4 cells. Hence a second form, **T1-lazy**: the same int arrays filled lazily
  per cell with a sentinel (one predictable branch). Both are measured; the column workload decides.
- **T2.** Vanilla routes every location through `BlockPos.asLong`/`getX/Y/Z` (`BlockPos.java:80-86, 116-118`:
  X/Z 26 bits, Y 12 bits). That wraps for Y outside [-2048, 2047]: a dimension with minY -2032 has minGridY -171 ->
  location Y down to -2052, which vanilla wraps to +2044 (then uses it both in the distance and in `computeFluid`).
  C2ME's grid arithmetic does not wrap, so it differs there. Port rule: fill `cellX/cellY/cellZ[i] =
  BlockPos.getX/Y/Z(BlockPos.asLong(...))` (the wrapped values vanilla uses) and keep the long cache for statuses.
  Exact for every input, including the wrap. The replay reaches the wrap only through the deep-sea world (section 3) and the
  wrap hardly ever changes a result (a wrapped cell lies at least 17 blocks from the lowest block), so the claim is checked
  on the stored locations themselves: `PartChecks` compares every cell location Mojang's cache holds with the candidate's
  (mutant `NO_BLOCKPOS_WRAP` is rejected there, not by the replay).
- **T3, deviation 1 (tie-break).** C2ME visits **offY outer, offZ, x inner** (`M:306-308`, x unrolled) and gives
  later cells a smaller key (`index` counts down, `M:305, 311, 319, 355`), so on equal distance the later cell *in
  C2ME's order* wins. Vanilla's order is x, y, z (`A:235-237`). Counter-example: cells P = (x1 0, y1 +1, z1 0) and
  Q = (1, -1, 0) at equal distance: vanilla visits P 5th and Q 7th, Q ranks first; C2ME visits Q 2nd and P 9th, P
  ranks first. Swapped closest1/closest2 changes the returned fluid state (`A:291`) whenever their statuses differ.
  Equal integer squared distances are common, so this is a real mismatch source.
- **T3, deviation 2 (4th slot).** C2ME only inserts when `p <= C` (`M:328`, `M:342`) and updates D only inside that
  branch (`M:338`, `M:352`). A key between C and D is dropped, so D can stay too large (even `Integer.MAX_VALUE`).
  D only feeds the final flag (`M:274-276` = `A:343-345`): `shouldScheduleFluidUpdate` can be false where vanilla
  is true, and status4 can be looked up for the wrong cell (a different sampler call sequence).
- **T3, exact form.** Key = `(distanceSqr << 4) | (11 - order)`, with `order = x1*6 + (y1+1)*2 + z1` (vanilla's
  visit order), so smaller key = closer, and among equal distances the later-visited cell. Keys are unique.
  Vanilla's `>=` insertion keeps the 4 slots sorted by (distance asc, visit order desc) and a cell pushed out of
  slot 4 never returns, so its slots are the first four of the full sort by that key; an insertion network on
  unique keys (insert when `key < D`, cascade min/max through A, B, C, D) yields the same four. The cell index is
  `baseIndex + offset[order]` (12 offsets from the grid sizes, set in the constructor), the distance `key >>> 4`.
  Range: distances stay below 2^27 even with the Y wrap (|dy| < 4200), so the int key cannot overflow; C2ME's
  `dist << 20` packing needs dist < 2048 and the index field < 65536, which the wrap case breaks.
- **T4.** Vanilla creates one `MutableDouble(NaN)` per call at `A:308`, shared by the up to three
  `calculatePressure` calls of that call. A field reset to NaN at the same point (before the barrier12 pressure)
  with the same `Double.isNaN` test (not a boolean: a NaN noise value must be resampled as vanilla does) gives the
  same number of `barrier.sampleValue` calls with the same arguments. Widening float -> double as in `A:412`.
- **T5.** Pure refactoring; kept as long as each piece keeps vanilla's expressions and order.
- **T6.** C2ME computes `g + 0.8 + (f - 1.0) * 1.2` and `g + 0.3 + (f - 1.0) * 1.1` (`M:391-392`); vanilla computes
  `g - Mth.map(f, 1.0, 0.0, -0.8, 0.4)` = `g - (-0.8 + ((f - 1.0) / (0.0 - 1.0)) * (0.4 - -0.8))` (`A:522-527`,
  `Mth.java:356-358, 590-592, 694-696`). Same algebra, different rounding (`0.4 + 0.8` is not the double `1.2`),
  so the `> 0.0` tests (`A:531, 533`) can flip. Vanilla arithmetic is kept verbatim (calls to `Mth.map` and
  `Mth.clampedMap` are exact by construction).
- **T7.** `x >> 6 == Math.floorDiv(x, 64)` for every int, so it would be exact; the call runs a few hundred times
  per chunk and the JIT already does this: not ported.
- **T8.** `RU:48-49` writes seedLo/seedHi directly, skipping the all-zero fix-up of the Xoroshiro constructor
  (`Xoroshiro128PlusPlus.java:17-24`): differs from vanilla when `getSeed(x,y,z) ^ seedLo == 0` and `seedHi == 0`.
  Practically unreachable, but not provably equal, and it needs private fields: dropped. The port calls
  `positionalRandomFactory.at(...)` as vanilla does (works for Legacy too).
- Other C2ME differences: C2ME ignores `DEBUG_DISABLE_FLUID_GENERATION` (`M:218, 238, 241, 280` return the raw state; vanilla `A:220, 292`);
  the port keeps both branches. C2ME's `0.0 + x` removal in the gradient (`M:496-501`) is exact (x is never -0.0 there:
  `f - |e|` with f > 0 rounds to +0.0 when equal, and a signed zero cannot change any `> 0.0` test). Everything
  else in `M` (the lava check, the water-over-lava check, status order including the unconditional status3 at
  `M:258`, `FluidStatus.equals`, `similarity`) matches `A`.
- Floating point: keep vanilla's widths and order. Three easy-to-miss spots: barrier is float widened
  (`A:412`); `spread * 10.0F` is a **float** multiply before widening (`A:550`); `0.5 * (l1 + l2)` sums ints
  first (`A:380`). No `Math.fma`, no algebraic rewrite (Java never contracts on its own).

## 3. Harness

Reuse the sibling's `World` (bootstrap, `VanillaRegistries.createWorldLookup`, `RandomState.create` per seed and
preset) and extend it with: the `Aquifer.Config` of the preset, the random factory
`randomState.getOrCreateRandomFactory(Identifier.withDefaultNamespace("aquifer"))`, and the real fluid picker,
read once by reflection from the `globalFluidPicker` Supplier of a real `NoiseBasedChunkGenerator(fixedBiomeSource,
settingsHolder)` (`NBCG:71, 77`), so no copy of `NBCG:80-92` can drift.

Per chunk (`Workload`):
1. Context as `NC:41-43` (caches on, own `ThreadLocalDensityBufferArena`, beardifier EMPTY; optional real
   beardifier later), wrapped in a `TracingSamplerSet` in verify (records function, x, y, z, result bits of every
   `sampleValue`/`sampleVolume`); the fluid picker wrapped likewise.
2. Construct the aquifer: VANILLA via `config.create(set, factory, volume, picker)` (`A:65-72`, public); a candidate
   via `new XxxAquifer(set, config, factory, volume, picker)`. The candidate reimplements the constructor exactly
   (`A:130-163`, same `get` order, same `maxSurfaceLevel` volume), so it needs **no private state of Mojang's
   class and no reflection**: it is a drop-in `Aquifer` for `Config.create`.
3. `finalDensity.sampleVolume(volume)` on the same context (the server does this after construction, `NBCG:448`;
   it fills the caches the aquifer's exclusion reads), densities copied to a `float[]`.
4. Replay the calls: doFill order (`NBCG:449-459`), then a synthetic carve phase on the same instance (density 0.0,
   seeded random columns and y spans, y descending, as `NBCG:302-317`).

Sanity check once per world: a real `new NoiseChunk(...)`'s `aquifer()` gives the same per-call results as the
harness-built VANILLA (proves the harness inputs equal the server's).

Parity verify (`run.sh verify`), each tally separate, reference = VANILLA, compared per call: returned `BlockState`
by identity (`==`, null included) and `shouldScheduleFluidUpdate()` after **every** call (stricter than the
server, which reads it only for fluids), plus the whole sampler and fluid picker trace at the end of each phase:

| Tally | What |
| --- | --- |
| `[FILL]` | every block of every chunk in doFill order, real densities |
| `[CARVE]` | density-0.0 calls continuing on the same instances |
| `[COLUMN]` | 1 x height x 1 volumes at random x/z, real column densities, y descending, full column |
| `[SHUFFLE]` | fresh pair per chunk, the chunk's positions in a seeded random order with fuzzed densities (0.0, -0.0, NaN, +-Double.MIN_VALUE, values in [-2.5, 0], real ones): proves order independence of the lazy caches |
| `[EDGE]` | far chunks near +-30,000,000 (X and Z, both signs); overworld settings rebuilt with `useLegacyRandomSource = true` (Legacy positional random) |
| `[DEEP-FLOOR]`, `[DEEP]` | the `deep-sea` world (below): a 16 x 4064 x 16 volume at minY -2032, synthetic densities. FLOOR = every column of the four lowest blocks with densities -2.5, -0.4 and 0.0 (their twelve candidates include the bottom grid row, whose locations wrap); DEEP = 15,000 random positions over the whole height |
| `[FLOODED-FILL]`, `[FLOODED-CARVE]`, `[FLOODED-SHUFFLE]` | the `sea-10` world (below): real chunks at sea level -10, doFill order, carving mask, shuffled fuzzed replay on one instance |

Two synthetic worlds are derived from each preset world (`World.withDeepSea`, `World.withSeaLevelMinus10`), because the presets never
reach what the review found missing. Both change the sea level (the deep one also the noise height) and multiply the floodedness noise
by 3 (the real one rarely exceeds 0.4, so almost nothing floods). `deep-sea`: sea level -2040, so the vanilla picker's lava (below
`min(-54, sea level)`) lies below every block of the volume and the cells reach the BlockPos Y wrap. `sea-10`: sea level -10, so fully
flooded cells sit exactly on the lava test's `fluidSurfaceLevel <= -10` edge and water meets lava in volume.

Presets overworld, large_biomes, amplified (the only ones with aquifers); seeds e.g. 0, 1, -7, 42 and one random
(printed). Chunks per world: near spawn, random in +-10k, far. Default sized to finish under ~60 s.

`PartChecks` (run by verify and selftest, independent of seed): the pure pieces of every C2ME-family candidate against Mojang's own
private methods, called by reflection, bit for bit: `similarity` (every difference in +-2^21 and 2M random pairs), `calculatePressure`
(all pairs of ten fluid levels x nine fluid pairs x a column of heights, three pressures sharing one barrier noise), `computeFluidType`
(12 levels x 3 global fluids x 3,000 positions; must reach the lava branch at level -10) and the cell locations after the floor calls
(must include wrapped cells). Why: a one-ulp rounding change or a `<=` / `<` flip only shows in a replay when a call lands exactly on it.

Self-test (`run.sh selftest`): PORT and the candidates must pass and each broken variant must fail:
`TIE_OLDER_WINS` (`>` instead of `>=`), `C2ME_TIEBREAK` (C2ME's y/z/x ranks), `C2ME_FOURTH_SLOT` (insert only when
`key <= C`), `STALE_BARRIER` (barrier value not reset per call), `FLAG_KEPT` (one early return does not write the
flag), `SPREAD_DOUBLE` (`* 10.0` instead of `* 10.0F`), `LAZY_STATUS3` (status3 only when similarity13 > 0: equal
blocks, different sampler trace, so only `[trace]` may catch it), `C2ME_FLOOD_ALGEBRA` (T6; reported, may survive:
then it is listed as "not caught by data", and T6 stays dropped either way), `NO_BLOCKPOS_WRAP`, `SIMILARITY_MUL` (`* 0.04` for
`/ 25.0`) and `LAVA_LEVEL_LT` (`< -10` for `<= -10`), which the part checks must reject, and `C2ME_LITERAL` (C2ME's semantics as
written; its mismatch count is the evidence that C2ME as is is not exact).

Later, not built now: an in-server dual run, `-Dkindling.verifyAquifer=true`, in the style of
`net/kindlingmc/worldgen/CheckedRuleEvaluator.java` (`-Dkindling.verifyMaterialRules`, `MaterialRuleCompiler.java:20`):
`Config.create` returns a checked aquifer holding vanilla's and the candidate's instance built from the same inputs;
each call runs both, compares state identity and flag, returns **vanilla's** result (the world stays vanilla), counts
calls and logs the first mismatches with seed, position and density. Both share the chunk's sampler context
(position-keyed caches, `CachingDensitySampler.java:10-11`), so the duplicate calls change no value. Covers doFill,
real carvers and getBaseHeight columns during a real pregen.

## 4. Variants and benchmark

| Variant | Content |
| --- | --- |
| VANILLA | Mojang's class through `Config.create` |
| PORT | line-by-line reimplementation (`PortAquifer`): checks the harness and the reimplementation cost |
| SORT | PORT + T3 exact |
| EAGER | PORT + T1 (rows up to the skip row) + T2 ints |
| LAZY_INT | PORT + T1-lazy + T2 ints |
| NOALLOC | PORT + T4 |
| C2ME | T1 (or T1-lazy, whichever wins), T2, T3 exact, T4, T5: the candidate for the server |

Shape: an abstract base with the exact constructor, `computeFluid` and helpers; each variant overrides only the hot
`computeSubstance` (no runtime technique flags in the hot path). `C2meAquifer` and `C2meLiteralAquifer` keep C2ME's
MIT notice (copyright 2021-2026 ishland and permission text) in the header.

Bench (`Bench`, after the sibling): densities precomputed per chunk; per timed sample a fresh context, then
**construction** timed alone, `finalDensity.sampleVolume` untimed, then the **fill replay** timed (all
`computeSubstance` calls of the chunk plus a flag read per call into a sink). Reported: ns per chunk for fill and for
construction, and ns per column (construction + full column replay). Rounds interleaved in rotating variant order,
per-thread warm re-verify at the end, JSON per fork. Shared call sites see several `Aquifer` classes, while the
server sees one: `--solo` forks per variant (like `--paper-solo`) check that megamorphic dispatch does not bias the
mixed numbers. On the owner's PC: smoke only, 1 thread, <= 20 s.

Verify also prints counters (from a counting PORT subclass, never timed): calls per exit (density > 0, above skip,
lava, similarity12 <= 0, water-over-lava, barrier path) and statuses computed per chunk, to put the timing into
proportion.

## 5. File plan, commands, expectation, risks

Files (all under `kindling-bench/aquifer`, shell scripts with LF line endings):

- `run.sh` (build | verify | selftest | bench), `classpath.sh` (present), `.gitignore` (present), `mutants.sh`
  (later), `DESIGN.md`, `IMPL-NOTES.md`, `README.md` (at the end).
- `java/aquifer/`: `Main`, `Options`, `World`, `Workload`, `ChunkCase` (volume, densities, carve script),
  `TracingSamplerSet`, `TracingFluidPicker`, `Trace`, `Variant`, `Variants`, `Verifier`, `SelfTest`, `Bench`,
  `Report`, `Stats`, `Counters`.
- `java/aquifer/candidate/`: `BaseAquifer` (exact constructor, statuses, helpers), `PortAquifer`, `SortAquifer`,
  `EagerAquifer`, `LazyIntAquifer`, `NoAllocAquifer`, `C2meAquifer`.
- `java/aquifer/broken/`: one small subclass per broken variant, `C2meLiteralAquifer`.

Commands:

```sh
sh run.sh build
sh run.sh verify                      # default set, < ~60 s
sh run.sh verify --seed 7 --presets overworld --chunks 8 --edge
sh run.sh selftest
sh run.sh bench --forks 1 --threads 1 --seconds 20   # smoke only on the gaming PC
```

Expectation (not measured): profile share aquifer 8.8 % of worker CPU, `computeSubstance` self time 6.6 %. If the
exact C2ME variant makes the self time 1.3x to 2x faster, the worker CPU saving is 6.6 % x (1 - 1/1.3 .. 1/2) =
about 1.5 to 3.3 %; status computation and noise sampling (the other 2.2 %) stay. That is close to what
`compare.py` can resolve, so the harness's ns per chunk is the primary evidence and a host bench decides.

Top risks:

1. Tie-break and 4th slot: C2ME's code is not exact (section 2); the port uses vanilla ranks and a full 4-slot
   insertion, and the self-test proves verify catches both.
2. Floating point drift through refactoring (float widening, `10.0F`, Mth expression order, T6 algebra): vanilla
   expressions are copied verbatim; mutants check the sensitive ones.
3. Sampler call order (lazy statuses): could break results only through side effects, not values; the trace check
   makes any reordering visible.
4. BlockPos wrap and key overflow at extreme heights: wrapped coordinates stored as vanilla uses them, int keys sized
   for the wrap; covered by `[EDGE]`.
5. Column regression from eager fill (`getBaseHeight` builds an aquifer per column): measured by the column
   workload; LAZY_INT is the fallback.
6. Benchmark bias: megamorphic call sites and JIT profiles shared between variants; solo forks check it.
7. Harness realism: no beardifier/blender in the default context. The aquifer reads neither (its six functions do
   not use them), only the density pattern differs; optional real beardifier later. The in-server dual run is the
   final check.
8. Upstream drift: a Mojang change to `Aquifer.java` must be re-ported; the future patch adds a Kindling test that
   runs the verify set against the real class.

## 6. Findings during implementation

- **Variant set: ablations from C2ME instead of PORT plus one technique.** One exact C2ME class holds T1-T5; the
  per-technique variants remove one technique each, so no hot path needs a runtime flag or a megamorphic call:
  `C2ME_LAZY` (T1-lazy instead of T1), `C2ME_BRANCHY` (vanilla's if/else insertion instead of T3). The lazy fill check
  stays in C2ME's selection too (it never fires after an eager fill: one compare on a value loaded anyway).
  Not isolated: T2 (ints are the storage of both C2ME forms) and T4 (would need a second tail copy). SORT, EAGER,
  LAZY_INT and NOALLOC as separate PORT copies were not built.
- **T3 exact form confirmed by data.** Vanilla ranks plus an unguarded min/max cascade: 0 differences in every tally.
  `key < slot` on unique keys equals vanilla's `distance >= newDistance` (C2ME_BRANCHY passes too).
- **C2ME's tie-break (deviation 1) is a trace deviation in practice.** Mutant `C2ME_TIEBREAK`: 0 state/flag differences
  in 5 and in 36 cases, but different status lookups (trace differences 4,202 / 10,595). Reason: on an exact tie
  similarity12 = 1, the pressure is symmetric and almost always makes the block a barrier (null), so the swapped first
  cell rarely shows. Not provably exact, so it stays fixed.
- **C2ME's fourth-slot skip (deviation 2) changes real output.** Mutant `C2ME_FOURTH_SLOT`: 2 (5 cases) and 7 (36 cases)
  result/flag differences, i.e. scheduled fluid ticks differ. `C2ME_LITERAL` (C2ME's ranks + fourth slot + T6) shows the
  same 2 / 7: C2ME as written is not exact on 26.3.
- **T6 and the float spread are below what the data reaches.** `C2ME_FLOOD_ALGEBRA` and `SPREAD_DOUBLE` gave 0
  differences on 36 cases. That proves nothing either way; T6 stays dropped because its rounding differs by construction.
- **STATUS3_FIRST** replaces the design's LAZY_STATUS3: fetching status3 lazily cannot reorder sampler calls (nothing
  observable happens between A:316 and its first use), while looking it up before status2 does; it is caught by the
  trace check only, as intended.
- **Smoke (not a result):** 5 rounds, 1 thread, desktop: fill replay C2ME 1.00x VANILLA, PORT 1.15x, C2ME_BRANCHY
  1.10x, C2ME_LAZY 1.13x, with round-to-round spread up to 25 %. PORT should equal VANILLA; that it does not in the
  smoke points to bench bias (shared call sites, JIT profiles) or noise, which `--solo` host forks must settle before
  any speed claim.

## 7. Open review points

A review of the harness (2026-10-30) raised five findings. Each was checked against the code; what changed is in the file named.

1. **Deep volume never reached the Y wrap: confirmed, fixed.** With the preset's sea level the picker returns lava below -54, so
   `computeSubstance` left before any cell was chosen and `NO_BLOCKPOS_WRAP` passed verify. Fix: the `deep-sea` world and the
   `[DEEP-FLOOR]` / `[DEEP]` cases (`World`, `Workload`, `ChunkCase`, `CallList.floor`), and a direct comparison of the stored cell
   locations against Mojang's `aquiferLocationCache` (`PartChecks`). Residual, stated honestly: with these cases the wrap rows are reached
   (3 to 5 wrapped cells compared per run), but the replay still shows no result difference for `NO_BLOCKPOS_WRAP`: minY cannot go below
   -2032, so a wrapped cell is at least 17 blocks away and almost never among the nearest four. The location comparison, not the replay,
   is the test for the wrap. The old preset-settings deep case was dropped from `[EDGE]` (it tested only the early exit).
2. **Column bench left out getBaseHeight's early exit: confirmed, fixed.** `Bench` now also times a column replayed only up to the first
   block that is not air (NBCG:207-209; the length is found once per column with the reference), on a fresh aquifer, as
   `us/column (early)` and `ns_per_column_early_exit` in the JSON. The 5-round desktop smoke shows the eager fill costing there
   (C2ME 0.970x, C2ME_LAZY 0.996x, VANILLA_B 0.992x on the early column; noise-level, not a result). The choice between `C2ME` and
   `C2ME_LAZY` for the patch is decided on this metric from `--solo` host forks, not on the full column.
3. **Floating-point and boundary mutants the data did not reach: confirmed for two, equivalent for two, fixed.**
   `SIMILARITY_MUL` (`* 0.04` for `/ 25.0`) is a real deviation (834,323 part-check differences); `LAVA_LEVEL_LT` is real and is also
   caught by the replay once the `sea-10` world exists (695 result differences). Both are now self-test mutants that must be rejected.
   `LAVA_NOISE_GE` is equivalent: the noise is a float and no float widens to 0.3, so `> 0.3` and `>= 0.3` agree (checked at run time,
   `PartChecks.equivalentMutantProofFailures`). `GRADIENT_BOUND` is equivalent: the gradient is never exactly +-2.0 because its centre
   point is always a half-integer (zero hits over every level pair in +-140 and every height in +-150). `BaseAquifer` stays "copy verbatim": for the pieces
   the part checks compare against Mojang's methods; for the rest (the constructor, `computeFluid`, `computeSurfaceLevel`) the rule for
   the future patch is a line-by-line diff of the method bodies against `Aquifer.java` in review, and the in-server dual run
   (`-Dkindling.verifyAquifer`, section 3) must be run on a world with lava aquifers and water over lava.
4. **Eager fill differs from vanilla outside the volume: confirmed, documented, not fixable without dropping eager fill.** Vanilla's
   `getIndex` has no bounds check, so a call outside the volume the aquifer was built for aliases into another cell's slot (or throws);
   vanilla caches the aliased location lazily, eager fill already stored the in-volume one. All server callers (NBCG:201, 317, 459) stay
   inside the volume. The future patch must say so in its class comment; `C2ME_LAZY` reproduces vanilla even there. If the early-exit
   metric of point 2 shows no clear gain for eager fill, take `C2ME_LAZY` and the question disappears.
5. **15 % gap between identical implementations in the smoke: confirmed as noise, not reproduced.** The first smoke (PORT 1.15x) was
   not repeated: the same 5-round smoke after the change gives PORT 1.015x fill and VANILLA_B 1.009x. Still a smoke on a busy desktop; no
   speed claim without `--solo` host forks, 30+ rounds, `compare.py`'s `*` criterion, with the VANILLA_B row as noise floor (the report now says so).
