# density-compiler: design

Question: can a different evaluation of the 26.3 worldgen density graph make `finalDensity.sampleVolume` per chunk
faster while every output float stays bit-identical to Paper? This file is the design only; nothing here is
measured yet. Line references are to the applied sources under
`E:\DEV\Minecraft\cubyx-leaf\leaf-server\src\minecraft\java\net\minecraft\world\level\levelgen\` (short: `levelgen/`).

## Decisions (summary)

1. **v1 scope = two techniques, both exact by construction:**
   - **Demand-driven pruning at the cell-corner level** (inside every `InterpolatedFunction`): `RangeChoice` and
     `Lerp` evaluate their selector first and request each branch only where Paper would keep its value. Branch
     leaves (Paper's own noise/spline samplers) are called on sub-volumes (box or per-column runs). Covers the
     `caves` range choice, the slide lerps (top 9 and bottom 1 of 49 corner rows skip the whole cave graph) and the
     `BlendedNoise` lerp (one of the two 16-octave limit stacks is skipped wherever `clamp(main + 0.5, 0, 1)` is
     exactly 0 or 1).
   - **Column tiling of the block level** (above the `InterpolatedFunction`s): the element-wise tree
     `add(min(squeeze(interp), noodle(4 x interp)), beardifier)` is evaluated one block column (384 floats) at a time
     in L1-sized tiles, with the interpolation replicated op for op, instead of ~15 full passes over 98,304-float
     buffers.
2. **Hand-written node classes, no bytecode.** Dispatch happens once per node per column or per corner volume,
   never per element, so megamorphic calls are not a cost worth a hidden-class compiler in v1.
3. **COMPILED is built by destructuring Paper's real compiled sampler tree** (`randomState.getSampler(finalDensity)`),
   not by re-implementing `DensityFunctionCompiler`. Same selection of sampler kinds, same cache ids, same noise
   objects. Private record samplers are read with record-component reflection at build time only.
4. **Reference = PAPER `sampleVolume`.** Per-point `sampleValue` is *not* bit-identical to `sampleVolume` in Paper
   (interpolation, mul/div, min/max, noise association), so the naive oracle is informational only.
5. **Harness:** standalone JVM, `Bootstrap` + `VanillaRegistries.createWorldLookup()` + `RandomState.create`, context
   built exactly like `NoiseChunk`. `Blender.empty()` (caveat); `Beardifier.EMPTY` plus an optional synthetic
   rigid piece through Beardifier's public test constructor. Aquifer excluded (caveat).

---

## 1. How one chunk's terrain fill samples density

### Call path

| Step | Where | What |
| --- | --- | --- |
| RandomState | `server/level/ChunkMap.java:206` | `RandomState.create(registryAccess.lookupOrThrow(Registries.NOISE), seed, settings)` |
| compiler | `RandomState.java:72-99`, `:132-134` | one `DensityFunctionCompiler` per RandomState; `getSampler(fn)` compiles lazily, cached per function (`DensityFunctionCompiler.java:31-44`) |
| rewrite | `DensityFunctionCompiler.java:16-22`, `:46-61`; `DfRewriteRule.java:106-155` | inline `HolderHolder`; dedupe `cache(...)` by `input()` equality into `CachingDensitySampler(id, ...)`; `SLICE_UNIFORM_AXES` wraps every subtree whose domain loses axes into `Slice*Sampler` (2D functions run on a `sizeY=1` volume at y=0) |
| chunk volume | `NoiseBasedChunkGenerator.java:111-114` | `DensityVolume(16, height, 16, minBlockX, minY, minBlockZ)`, step 1; overworld `16 x 384 x 16` from y=-64 |
| context | `NoiseChunk.java:19-51` | arena: `ThreadLocalDensityBufferArena.acquire()` (`:30`) or a RandomState pool; user fields: `Beardifier.CONTEXT_KEY` (`:33`), blender keys only if the blender is non-empty (`:34-39`); `SamplerContext...enableCaches()` (`:41-43`); aquifer created from the same `cachingSamplers` (`:44-50`) |
| fill | `NoiseBasedChunkGenerator.java:440-478` | **one** call `finalDensity.sampleVolume(volume)` (`:446-448`), then per block `aquifer.computeSubstance(x, y, z, density)` (`:459`) |
| other callers | `NoiseBasedChunkGenerator.java:170-215` | `iterateNoiseColumn` (base height / base column for structures): `1 x h x 1` column volume, beardifier `null`, `Blender.empty()` |

### What `finalDensity` is (overworld)

`NoiseRouterData.java:233-241, 369-371, 294-316, 573-585, 587-603`:

```
final = add( min( squeeze( interpolated[4,8]( mul( blendDensity( slide(caves) ), 0.64 ) ) ),
                  noodle ),
             beardifier )
slide(x) = lerp(bottomFactor y[-64..-40], 0.1171875, lerp(topFactor y[240..256], -0.078125, x))   // ConstFirstSampler x2
caves    = rangeChoice(slopedCheese, -1e6, 1.5625, min(slopedCheese, 5*entrances), underground)
noodle   = rangeChoice(toggle, -1e6, 0, 64, add(thickness, mul(max(abs(ridgeA), abs(ridgeB)), 1.5)))
           toggle/thickness/ridgeA/ridgeB = interpolated[4,8]( rangeChoice(y, -60, 321, noise, const) )
slopedCheese = cache( initialDensity(splines of continents/erosion/ridges, jagged) + base_3d_noise )
base_3d_noise = BlendedNoise -> LerpFunction.Sampler(clamp(main + 0.5, 0, 1), minLimit16oct, maxLimit16oct)
                (synth/BlendedNoise.java:98-109)
```

### Volume shapes and order (one chunk)

1. Root `AddSampler` -> `MinSampler` -> `SqueezeSampler` -> outer `InterpolatedFunction.Sampler` with the block volume
   (`op/InterpolatedFunction.java:56-69`). Not cell-aligned in step, so `sampleWithBlockStep` (`:98-148`) builds the
   **cell-corner volume** `5 x 49 x 5`, step `4/8/4`, min `(chunkX*16, -64, chunkZ*16)` (`:108-118`) and calls
   `input.sampleVolume(cellVolume)` **once** (`:121`), then `fillCell` per cell (`:150-199`).
2. Everything below the outer interpolated (`mul 0.64 -> blendDensity -> slide -> caves -> ...`) runs over that
   1,225-point corner volume. Paper evaluates **both** branches of every non-constant `RangeChoice`
   (`op/RangeChoiceFunction.java:89-104`) and **all three** inputs of every `Lerp` (`op/LerpFunction.java:63-96,
   131-170`) over the whole volume. 2D subtrees (splines, continents, erosion, ...) run over the sliced
   `5 x 1 x 5` volume.
3. `noodle`: `RangeChoiceFunction.Sampler` at block level: `whenInRange` (const 64, `fill`), `input` = toggle
   interpolated (own `5 x 49 x 5` corner call + `fillCell` over 98,304), `whenOutOfRange` = 3 more interpolated
   (3 corner calls + 3 full `fillCell`s) + abs, abs, max, mul, add passes; merge pass.
4. `min` pass, beardifier (`ContextBoundSampler` -> `Beardifier.sampleVolume`, `Beardifier.java:120-145`: `fill(0)`
   then per point inside `affectedBox`), `add` pass.

Totals per chunk: 5 corner calls (1 expensive, 4 cheap), 5 `fillCell` sweeps and ~10 element-wise passes over
98,304 floats (393 KB each), every intermediate buffer from the arena.

**Caches** (`SamplerContext.java:58-75`): one cell per cache id, keyed by the exact `DensityVolume`
(`volume.equals(cell.volume)`); a different volume recomputes and evicts. `sampleValueCached` (`:77-99`) answers
from the cached volume buffer if the point is inside it.

**Beardifier**: `Beardifier.forStructuresInChunk` (`Beardifier.java:50-102`) returns `EMPTY` unless a structure start
with terrain adaptation touches the chunk; then rigid pieces + junctions, `affectedBox = pieces.inflatedBy(24)`.
**Blender**: only for chunks next to pre-existing old-format chunks; `BlendDensityFunction.Sampler`
(`op/BlendDensityFunction.java:45-75`) calls `blender.blendDensity` per point; `blend_alpha/offset` resolve through
`ContextBoundSampler` with fallbacks 1.0 / 0.0 (`generator/SimpleDensityFunction.java:24-31`).
**Aquifer**: not part of the `finalDensity` call; it samples its own functions per block afterwards.

### What the harness reproduces

| Part | Faithful? | How |
| --- | --- | --- |
| registries, router, noises | yes (data-identical, object identity differs) | `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();` then `HolderLookup.Provider p = VanillaRegistries.createWorldLookup();` (`data/registries/VanillaRegistries.java:175`), `NoiseGeneratorSettings s = p.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();`, `RandomState rs = RandomState.create(p.lookupOrThrow(Registries.NOISE), seed, s);` (same `create` as `ChunkMap.java:206`) |
| sampler | exact | `DensitySampler paper = rs.getSampler(s.noiseRouter().finalDensity());` |
| context + arena | exact | `ThreadLocalDensityBufferArena.acquire()`; `SamplerContext.builder().setUserFields(ContextMap.builder().set(Beardifier.CONTEXT_KEY, beard).build()).useBufferArena(arena).enableCaches().build()`; `ThreadLocalDensityBufferArena.release(arena)` after the chunk |
| chunk volume | exact | `new DensityVolume(16, 384, 16, cx*16, -64, cz*16)` |
| beardifier | `EMPTY` exact; real pieces no | default `Beardifier.EMPTY`; option: every K-th chunk gets `new Beardifier(List.of(new Beardifier.Rigid(box, TerrainAdjustment.BEARD_THIN, 0)), List.of(), box.inflatedBy(24))` (public `@VisibleForTesting` constructor, `Beardifier.java:108`) |
| blender | no | `Blender.empty()`: no blend keys, `blendDensity` is identity. **Caveat v1.** |
| aquifer, surface, carvers | not measured | **Caveat v1**: they do not call `finalDensity.sampleVolume`, COMPILED does not touch them |

Decision: v1 runs `Blender.empty()` and `EMPTY` + synthetic beardifier (it is easy). Risk to check first when
building: Paper/Leaf static init during `Bootstrap` outside a server (Paper's own tests do exactly this,
`paper-server/src/test/java/org/bukkit/support/RegistryHelper.java:94-95`, so it should work on the runtime
classpath). The server jar seals only `org/bukkit`, `net/bukkit`, `com/bukkit` (manifest checked), so classes in
`net.minecraft.*` packages would be allowed, but v1 does not need them.

## 2. Workload and measurement

- **Seeds:** 3 world seeds (default `0`, `12345`, `-4172144997902289642`), one RandomState each, built before timing.
- **Chunks per seed:** 128 spread uniformly in +/-20,000 blocks (fixed PRNG seed), an 8 x 8 square at the origin,
  4 far chunks at +/-29,999,000 blocks (exercises `wrap()`), every 8th chunk with the synthetic beardifier
  (`--beard-every 8`, 0 = off). Verify adds, per run, `--fuzz N` random chunks (negative/far coordinates), `--columns N`
  1 x 384 x 1 columns at any block (negative, far, unaligned; no beardifier key in the context, as `iterateNoiseColumn`
  builds it) and `--boxes N` random unaligned boxes (1-24 across, any height and y offset, no beardifier). Bench times
  only the chunk list.
- **Verify-only hygiene:** every buffer the arena hands out is first filled with a finite poison value (`0x7f7fdead`,
  not a NaN, because NaN against NaN passes), so a variant that skips an element, a tile or a column, or reads an
  unwritten scratch buffer, fails. The reference and the candidates alternate order per chunk. After each call the
  point queries the aquifer would make (its six config functions plus erosion and depth, 48 points each, half of them
  quart aligned) run on the same context and are compared bit for bit with the reference context (the `[probe]` tally).
- **One call = what `doFill` does for density:** acquire arena, build the `NoiseChunk`-identical context, acquire
  the output buffer, `sampler.sampleVolume(ctx, out, chunkVolume)`, fold a few output bits into a sink, release the
  arena. Context creation is inside the timing for both variants (the server pays it per chunk too).
- **Rounds:** after `--warmup-sec` (default 10) of interleaved warm-up, each round runs the whole chunk list once per
  variant in a rotating variant order (as in `native-noise/kindling-bench/noise-kernel`). ns per chunk =
  `(t1 - t0) / chunks` per variant per round. Report: median, min, max over rounds; speedup = PAPER median /
  variant median in the same fork. `--threads N`: each thread runs the full list with its own evaluator and arena;
  report per-thread ns per chunk and aggregate chunks/s.
- **Warm re-verify**: after its last measured round every bench thread verifies the very `BoundVariant` instances it timed
  (same thread, same arena, poisoned buffers) against the reference, on the chunk list plus the verify-only volumes. Only
  the variants that bench ran are verified (the reference itself is not compared with itself).
- Local runs on this PC: build, verify, and `bench --forks 1 --threads 1 --warmup-sec 3 --rounds 3 --chunks 16`
  only (about 20 s including ~5 s bootstrap). Real numbers come from the bench host.

## 3. Variants and oracles

| Variant | Role |
| --- | --- |
| `PAPER` | the real `DensitySampler` from the real `DensityFunctionCompiler`, real caches, real arena. **Reference.** |
| `PAPER_UNCACHED` | same sampler, context without `enableCaches()`. Verify only. Must equal `PAPER` bit for bit; checks that Paper's caches are value-transparent, the premise of pruning through caches. |
| `COMPILED` | pruning + column tiling. |
| `COMPILED_DENSE` | COMPILED with pruning off (all demand full): isolates the tiling gain and the evaluator overhead. |
| `NAIVE` (oracle, verify only) | `DensitySampler.sampleVolumeNaive` semantics (`DensitySampler.java:6-21`) at K sampled points per chunk (full per-point evaluation is ~1000x slower). Reports mismatch count and max ulp distance; **not** pass/fail. |
| `PURITY` (check, verify only) | for every leaf COMPILED calls on sub-volumes: full-volume result vs random box/run sub-volumes, bit for bit. Directly tests the assumption that makes pruning exact. |

Pass criterion: `Float.floatToRawIntBits` equal, or both NaN (counted and reported separately).

**`sampleValue` vs `sampleVolume` in Paper are not bit-identical:**

| Node | Difference |
| --- | --- |
| `InterpolatedFunction.Sampler` | volume: per-cell incremental `value = v_0 + step*y0; value += step` (`:190-195`); point: `Mth.lerp3` of a 2x2x2 corner call (`:202-230`). Different rounding off the corners. |
| `BinaryFunction.MulSampler`, `DivSampler` | point returns `+0.0F` when `left == 0` and skips `right` (`:359-360`, `:290-291`); volume computes `left*right` (`-0.0`, NaN from `0*inf`). |
| `MinSampler`, `MaxSampler` | point: short-circuit on `rightMin/rightMax`, then `Math.min/max` (NaN-propagating, orders `-0.0 < 0.0`); volume: `if (r < out)` / `if (r > out)` (keeps left on NaN right, ignores zero sign) (`:295-341`). |
| `ConstMinSampler`, `ConstMaxSampler` | `Math.min/max` vs comparison (signed zero) (`:206-240`). |
| `NoiseFunction.Sampler` and shift samplers | volume = `0.0F + sum((amp*layerAmp) * lerp3(dotXz + gy*ry ...))` via `addToVolume` (`synth/PerlinNoise.java:155-312`, `NoiseStack.java:60-65`); point = `sum(layerAmp * get(...))` with a different gradient-dot association. Paper's own `DEBUG_NOISE` check compares against the *vanilla volume* path, not `get`. |
| `CachingDensitySampler` | point may return a value cached from a volume call (`SamplerContext.java:88-93`), so mixes both paths. |
| identical | unary, clamp, range choice, lerp, slice, gradient, spline, blend, beardifier, constant (same per-element code; differences only through children). |

So `PAPER sampleVolume` is the reference; `PAPER_UNCACHED` is the independent second path.

## 4. COMPILED design

### Purity: why pruning is exact

Call a sampler **volume-pure** if the value it writes for a point depends only on that point, not on the other points
of the volume it is called with. Then evaluating a branch on any sub-volume (or skipping points) gives the same bits
at the points that are evaluated. Checked per node kind:

- Pure: every element-wise op (unary, binary, clamp, range choice, lerp, constant: per-index loops); gradients
  (per-coordinate `compute`, `generator/GradientFunction.java:99-131`); slices (they build their own input volume
  from the requested one and broadcast, `op/SliceFunction.java:75-224`); splines (per index, coordinates from
  buffers over the same volume, `op/SplineFunction.java:163-170`); `Perlin/SmearedPerlin.addToVolume`: coordinates
  per axis from `xv = min; xv += step` in `double`, exact for integer block coordinates (`PerlinNoise.java:193`,
  `SmearedPerlinNoise.java:103-129`), the smear depends only on the y coordinate, the `lastFloorY` reuse recomputes
  the same values, octaves accumulate per element in a fixed order (`NoiseStack.java:60-65`); shifted noises;
  `BlendDensity` (per point); `Beardifier` (per point); caches (value of a pure input, see `PAPER_UNCACHED`).
- **Not pure: `InterpolatedFunction.Sampler`.** Its output depends on the volume: the cell volume is derived from it,
  and inside a cell the value is an accumulation that starts at the first in-volume y (`y0`, `:169-195`). A
  sub-volume starting mid-cell gives different bits. Its *input* is called on the corner volume, which is pure
  below.
- `ContextBoundSampler` for blender keys resolves to blender buffers: treated as impure (blending is off in v1).

Consequences: pruning is applied **only below an interpolated** (corner level), where everything is pure for the
vanilla routers. Above the interpolated (block level) nothing is pruned; the interpolation is replicated exactly for
the same full volume Paper uses. Impure or unknown nodes are always called on the full region, which equals the
volume Paper would pass (our slice nodes mirror Paper's), so an impure node under a pruned branch is still exact.
A static guard scans the inlined `DensityFunction` tree (children enumerated generically with
`fn.rewriteChildren(recordingRule)`): if an `InterpolatedFunction`, `FindTopSurfaceFunction` or unknown type appears
below a spline coordinate or a noise shift, those opaque leaves are classified impure.

### Candidate techniques

**a) Branch pruning (v1).** Node API: `eval(frame, region, out[], demand)` where `demand` is a boolean mask over the
region (plus count, `all` flag). Rules, each mirroring Paper's volume semantics exactly:

- `RangeChoice.Sampler`: evaluate `input` with `demand`; `in = input >= min && input < max` (Paper's
  `!(input >= min) || !(input < max)` is "out", `:100`); `whenInRange` with `demand & in`, `whenOutOfRange` with
  `demand & !in`; merge per element as Paper does. NaN input = out of range, same as Paper.
- `Lerp` (all three kinds): evaluate `alpha`; `first` needed where `alpha != 1.0F`, `second` where `alpha != 0.0F`
  (NaN needs both, as in Paper); merge with Paper's exact `alpha == 0 / == 1 / Mth.lerp` cascade.
- Element-wise nodes ignore the mask: they run over the whole region (cheap, branch-free); values outside the demand
  are garbage and never read.
- **Caches (the trap):** every `CachingDensitySampler` in the corner tree becomes our `CacheNode` keyed by Paper's id:
  one region buffer + a computed-stamp per point per frame. On demand it evaluates only `demand & !computed` into
  scratch and copies those points in. A request over a different region (cannot happen for vanilla, slices are
  static) evaluates the child directly, uncached. So a branch evaluated on a subset never misses a shared cache that
  a sibling already filled (`slopedCheese` is used by the `caves` selector, the surface branch and `underground`;
  `entrances` by both branches), and a cache miss costs time only.
- **Leaves** (`OpaqueNode` = Paper's sampler object: noises, shifted noises, splines, gradients, context-bound):
  `FULL` (one call on the region), `BOX` (bounding box of the demand, same steps) or `RUNS` (per column, maximal
  y-runs merged over gaps <= `--run-gap`), picked by `cost = points + callCost * calls` (`--call-cost`, default 8
  point-equivalents). Calls go to Paper's code with a `DensityVolume` of the sub-box, into a reusable
  `DensityBuffer` subclass whose `size` is set per call (`size` is `protected`). Impure leaves always `FULL`.
- Slices: project the demand (a column is needed if any point in it is), evaluate the child on the projected region,
  broadcast, exactly like `Slice*Sampler`.

Expected effect (to be measured): `caves` underground branch skipped at ~71 % of corners; whole cave graph (incl.
`slopedCheese` and `BlendedNoise`) skipped on 10 of 49 corner rows (top slide `alpha == 0` for y >= 256, bottom slide
at y = -64); one 16-octave limit stack of `BlendedNoise` skipped wherever the clamp saturates; noodle/ore inner
`rangeChoice(y, ...)` skip their noise outside the y range. The "all points chose one branch" shortcut is not used:
masks and runs work point-wise.

**b) Fusion of element-wise chains.** At corner level buffers are 1,225 floats (L1): fusing saves little; v1 only
replaces Paper's arena acquire/release per binary node (hash table + deque + linked set per call,
`ThreadLocalDensityBufferArena.java:76-111`) with a per-thread LIFO float stack. At block level the win is real but
comes from **column tiling** (d), which gives the cache locality of full fusion without per-element dispatch.
No algebraic folding (no `x + 0 -> x`, `x * 1 -> x`): `-0.0F + 0.0F = +0.0F` and NaN rules forbid it; Paper's own
constant handling (`ConstAdd`, `SUB c -> ConstAdd(-c)`, `DIV c -> ConstMul(1/c)`) is inherited by destructuring.

**c) Bytecode vs hand-written.** Hand-written. Calls are per node per region (corner volume or one column), about
`nodes x 256` per chunk at block level, so dispatch is not a measurable cost. Revisit hidden classes (pattern:
`net.kindlingmc.worldgen.MaterialRuleCompiler`/`RuleClassWriter`) only if a profile of COMPILED shows node dispatch.

**d) Others.**
- **Column tiling at block level (v1).** For a step-1 volume whose root tree (above interpolated leaves) contains only
  element-wise nodes, range choices, lerps, caches, constants, context-bound and pure opaque leaves: for each
  column `(x, z)`, every node fills a 384-float tile; `InterpolatedLeaf` fills its tile from its corner buffer by
  replicating `sampleWithBlockStep`/`fillCell` op for op (same cell volume formula `:99-118`, same `Math.min(cell+1,
  size-1)` clamping, `alphaZ = z * cellSizeXzInv`, `Mth.lerp` order, `value = v_0_ + valueStep * y0` literally even
  for `y0 = 0`, then `value += valueStep`). Each output element is produced by the same float operation sequence as
  in Paper, so the bits match. The root writes straight into the output buffer (a column is contiguous:
  `indexUnchecked = y + (x + z*sizeX)*sizeY`, `DensityVolume.java:33`). Corner buffers of all interpolated leaves are
  computed once per call (prepare phase) through the pruning evaluator with full demand. Any other volume shape
  (non-step-1, e.g. the `1 x 1 x 1` corner volumes inside `InterpolatedFunction.sampleValue`) falls back to Paper.
  Range choices at block level skip a branch for a column whose branch demand is empty (exact: no state carries
  between tiles).
- 2D / y-only subtrees: already per column/per row through Paper's slicing; nothing to add.
- Interpolation cost itself (`fillCell` 4.7 %): same arithmetic, but into an L1 tile instead of five 393 KB buffers.
- Later, not v1: min/max pruning by `range()` bounds (`MinSampler.rightMinValue`: needs a proof that `Interval` bounds
  are sound in float); pruning noodle ridge/thickness corner calls per cell from the toggle's block values; aquifer;
  `chunkSurfaceLevel`/`FindTopSurface` (surface stage); real blending.

### v1 scope

1. Builder: destructure `rs.getSampler(finalDensity)` into nodes (public records through accessors; private records
   `RangeChoiceFunction$Sampler/$ConstSampler`, `InterpolatedFunction$Sampler`, `BlendDensityFunction$Sampler`
   through `RecordComponent.getAccessor().setAccessible(true)` at build time; classpath = unnamed module, so this is
   allowed). Memoize by identity (the tree is a DAG). Unknown sampler classes become `OpaqueNode` (impure unless
   whitelisted).
2. Corner-level pruning evaluator (RangeChoice, Lerp, CacheNode, Slice, leaves with FULL/BOX/RUNS).
3. Block-level column tiling with `InterpolatedLeaf`.
4. `CompiledSampler implements DensitySampler`: `sampleVolume` -> block stage if supported, else Paper;
   `sampleValue` -> Paper. Immutable node graph shared across threads; one `Frame` (buffer stack, masks, cache
   stamps, counters) per thread.
5. Verify (PAPER, PAPER_UNCACHED, COMPILED, COMPILED_DENSE, NAIVE info, PURITY), bench, report.

Element ops call Paper's public helpers where they exist (`Mth.clamp`, `Mth.lerp`, `Mth.cube`, `Mth.square`,
`Mth.sqrt`) and copy private one-liners verbatim (squeeze `c/2 - cube(c)/24`, leaky ReLU, `addTo(i, -r)` for sub).
No `Math.fma`, no reassociation, float stays float.

## 5. File plan (`kindling-bench/density-compiler/`)

```
DESIGN.md            this file
README.md            result line (TBD), what/why, layout, build/verify/bench, bit-exact rules, caveats
run.sh               build | verify [opts] | bench [--forks N] [--threads N] [--out dir] [opts] | dump [opts]
classpath.sh         (exists) server jar + bundler libraries; run.sh defaults SERVER_JAR/BUNDLER_JAR to
                     E:/DEV/Minecraft/cubyx-leaf/leaf-server/build/libs/... when the worktree has no build
.gitignore           (exists) .cache/ java-build/ logs/ results/
java/densitycompiler/            harness
  Main.java          dispatch verify | bench | dump
  Options.java       --seeds, --dimension (overworld; amplified, large_biomes, nether, end for coverage),
                     --chunks, --range, --far, --beard-every, --fuzz, --threads, --warmup-sec, --rounds,
                     --variants, --dense-threshold, --run-gap, --call-cost, --naive-points, --purity-runs, --out
  World.java         bootstrap, registries, RandomState per seed, router
  Workload.java      chunk list per seed, synthetic beardifiers
  ChunkCall.java     one doFill-equivalent density call (arena, context, buffer, sink)
  Variant.java       PAPER, PAPER_UNCACHED, COMPILED, COMPILED_DENSE
  Verifier.java      cold + warm bit compare, first mismatch as (x, y, z), NaN counts
  NaiveOracle.java   sampled sampleValue comparison (info)
  PurityCheck.java   full vs sub-volume for every leaf COMPILED splits
  Bench.java         warm-up, rotating rounds, threads
  Stats.java, Report.java   median/min/max, speedup, JSON writer (fork-N.json: meta incl. server jar
                     Implementation-Version, JVM, OS, CPUs; options; workload; per-round ns per chunk per
                     variant; verify summary; pruning counters per RangeChoice/Lerp node)
java/densitycompiler/eval/       the evaluator (written so it can move to net.kindlingmc later)
  CompiledSampler.java   DensitySampler facade, fallback rules
  TreeBuilder.java       destructuring, memoization, purity classification, static guard
  Frame.java             per-thread state: float stack, mask stack, cache stamps, Paper SamplerContext, counters
  Region.java            mutable volume description + toDensityVolume for leaf calls
  Demand.java            mask + count + plan (FULL/BOX/RUNS) selection
  ResizableBuffer.java   DensityBuffer subclass with settable size
  Node.java              eval(frame, region, out, demand)
  ConstNode, UnaryNode, BinaryNode, ClampNode, RangeChoiceNode, LerpNode, SliceNode, CacheNode,
  BlendNode, OpaqueNode, InterpolatedLeaf, BlockStage
```

`run.sh` mirrors the sibling: plain `javac -cp "$(classpath.sh)"` into `java-build/`, `java -Xmx2g -cp ...
densitycompiler.Main`, no `--add-modules`, no native access. `bench` starts one JVM per fork and writes
`<out>/fork-N.json`. `dump` prints the compiled node tree (kind, purity, cache id, region shape) for review.

No package-private access is needed: COMPILED never calls `SamplerContext.sampleVolumeCached`; it evaluates
`CachingDensitySampler.input()` itself.

## 6. Expected gain (expectation, not measured)

Profile shares of worker CPU: density graph 17.7 %, noise primitives 13.6 %, aquifer 8.8 %.

| Part | Basis | Expected saving (worker CPU) |
| --- | --- | --- |
| `caves` range-choice pruning | measured discarded work 7.3-9.6 %; box/run granularity and per-call overhead keep maybe 70-90 % of it | 5-8 % |
| slide lerps (10/49 corner rows) | remaining cave graph after (a) on ~20 % of rows | 1-2 % |
| `BlendedNoise` limit-stack pruning | 16 of 40 octaves skipped where the clamp saturates (fraction unknown, logged by the pruning counters) | 0.5-1.5 % |
| block-level column tiling | `fillCell` 4.7 + squeeze 0.8 + max 0.7 + `DensityBuffer` get/set 1.35 + `indexUnchecked` 0.6 + fill 0.7 + other passes ~1.5, roughly 10 %; L1 tiles may halve it | 3-5 % |
| arena traffic | per-node acquire/release removed | <1 % |

Total about 10-16 % of worker CPU. Amdahl: if pregen is worker-bound, throughput x 1/(1-0.10..0.16) = **+11-19 %**;
per call, `finalDensity.sampleVolume` (roughly 25-30 % of worker CPU) would get about 1.4-1.7x faster. Realistic
planning number: +10 %. The ablation `COMPILED_DENSE` vs `PAPER` vs `COMPILED` separates tiling from pruning.

## Risks

1. **Per-call overhead of split leaf calls** (`RUNS`/`BOX`): each Paper noise call sets up coordinate caches per octave;
   `NormalNoise` = two stacks of up to ~10 octaves, so 25 column calls cost hundreds of octave calls per leaf per
   chunk. Mitigated by the cost model and `BOX`; `--call-cost` tunable; the counters show plan choices.
2. **A purity assumption fails** for some leaf on some coordinates (far chunks, smear, shifted noise caches): pruned
   values would differ. Caught by `PURITY` and full verify; such a node gets classified impure (always `FULL`).
3. **Harness fidelity:** VanillaRegistries instead of datapack-loaded registries, no blending, synthetic beardifier,
   aquifer excluded, and a JIT profile that sees only one router (the server mixes dimensions, polluting call
   sites). Partly mitigated by `--dimension` coverage runs; the final answer needs a Kindling build on the bench host.

Smaller: `Bootstrap` outside a server may touch Paper/Leaf config statics; exact replication of the interpolation
index math (verify catches it); NaN payload differences (counted, allowed only where Paper also gives NaN).

## Open review points

Review of the harness, checked against the code. Fixed items are listed so the next reviewer does not redo them.

| # | Finding | Verdict | State |
| --- | --- | --- | --- |
| 1 | Verifier passed a variant that writes nothing (the arena hands out the reference's leftover buffer) | confirmed: `NO_OP` and `HALF_COLUMN` self-test variants passed with 0 mismatches before the fix | fixed: `PoisonedArena` in the verify path, order alternates per chunk, `BrokenVariant` kinds in the self-test must fail |
| 2 | Verify never checks `1 x h x 1` columns or other non-chunk volumes | confirmed: `iterateNoiseColumn` builds them with no beardifier | fixed: `COLUMN` and `BOX` calls (`--columns`, `--boxes`), tallied separately |
| 3 | Cache state Paper's `SamplerContext` holds after the call is observable later in the chunk | partly confirmed: observable by design (`sampleValueCached` answers from the volume buffer), no bit difference seen for the aquifer functions | partly fixed: `[probe]` tally compares aquifer-style point queries on the used context. **Open:** aquifer construction is not run before the call (the real one samples `surfaceLevel` on a quart volume first, which fills shared cache ids that `finalDensity` then evicts), and the cost of per-point recomputation in the aquifer is not timed. Once `COMPILED` exists, time an aquifer construction plus a `computeSubstance` sample per chunk, or count cache hits |
| 4 | Warm re-verify checked fresh instances on the main thread | confirmed | fixed: each bench thread verifies its own timed instances |
| 5 | A failing bench thread could hang the others | confirmed by reading (a late thread reached `await()` after `reset()` on a fresh barrier); the self-test now runs the failing case | fixed: `Phaser.forceTermination()`; `SelfTest` fails if the bench does not end |
| 6 | `PAPER` and `COMPILED` share Paper's call sites in one JVM, so `COMPILED` can change `PAPER`'s JIT profile | plausible, not measured | mitigation: `run.sh bench --paper-solo` adds forks with `--variants PAPER`; report PAPER mixed against PAPER solo. Constraint for the evaluator: `ResizableBuffer` must not override `get`, `set`, `fill` or any other `DensityBuffer` method (only set `size`), so Paper's call sites stay monomorphic |
| 7 | Verify cannot see selector boundary or NaN mistakes (`>=` vs `>`, NaN counted in range, `<` vs `<=`; found by mutating the code, four mutants passed) | confirmed: real routers never put a value on a threshold and never produce NaN | fixed: `SyntheticSuite` (659 synthetic trees: range choice, interval select, lerp, shared cache ids; block level, corner level, interpolated branches; inputs exactly on thresholds, +-0.0, NaN, +-inf, alpha 0, 1, NaN) checked against Paper's samplers of the same trees, part of every `verify`; `run.sh mutants` builds 11 broken copies and each must fail `verify --synthetic-only` (11 of 11 killed; the four reported mutants among them) |
| 8 | Plain verify never runs a volume twice on one sampler instance (mutant: `CacheNode` trusting a finished context) | confirmed | fixed: second pass in `Verifier` (reverse order, every volume twice in a row by every candidate), tallied as `[repeat]`; self-test variant `SKIPS_REPEATED_VOLUME` must pass the first pass and fail `[repeat]` |
| 9 | Mutable per-instance state (`CacheNode`, `Frame`, `TileFrame`, `InterpolatedTile`, `FullVolumeTile`), unsafe next to Paper's shared `getSampler()`; last context kept alive per instance | confirmed, harness-only today (one instance per bench thread) | partly fixed: `CompiledSampler` is confined to the first thread that calls it and throws `IllegalStateException` from any other, so a misuse cannot race silently. **Open, blocks any server move:** move all per-call state into a frame looked up per `SamplerContext` (DESIGN 4, v1 item 4), keep the node graph immutable, drop the context references at the end of the call (needs a decision on the cross-call cache hit Paper's context cells give for a repeated volume) |
| 10 | Corner-level cache ids leave Paper's context cells empty, so the aquifer's real query points are not checked | confirmed; same as point 3, which stays **open**. New evidence: with every probe point forced onto the 4 / 8 / 4 grid (the only points where Paper's cells answer) the `[probe]` tallies stayed at 0 mismatches for PAPER_UNCACHED and all COMPILED variants (reviewer's run), and the synthetic trees' `[probe]` tallies (point queries on `finalDensity` of the used context, 4,613 calls) are bit-identical too. Still not run: `Aquifer.create` before the call and `computeSubstance` for all 98,304 blocks of a chunk, compared as BlockStates and timed (the cost of per-point recomputation is unmeasured) |
| 11 | Paths verify did not reach: active blender, `TileStage.stepOne`, non-empty beardifier in COLUMN / BOX, the RUNS leaf plan | confirmed | fixed except the blender: `--tuning callCost,runGap` (also used by the synthetic suite at 8/1, 0/0 and 100000/8, so RUNS and BOX plans both run), box kinds 4 / 8 / 4 stepped and single cell-aligned points (pass-through) in real worlds and in the synthetic suite, synthetic structures under COLUMN and BOX volumes (every `--beard-every`-th). **Open:** `BlendNode` with an active blender (by reading exact: it calls Paper's sampler on the full region; a synthetic blender would be needed) |
| 12 | `COMPILED` shares Paper's leaf objects, so PAPER's JIT profile is polluted in mixed forks | same as point 6 | unchanged: report `PAPER` solo against `PAPER` mixed in every host run (`--paper-solo`); for final numbers run each COMPILED variant in its own fork against a PAPER solo fork |

Known limits left as they are: stepped boxes (4 / 8 / 4) are a robustness check only (Paper evaluates `finalDensity`
with other steps nowhere), heights are the settings' full height (no `clampToHeightAccessor` clamp), and a bench without `--variants` verifies only the
variants it timed (`PAPER_UNCACHED` is not benchmarked by default; `verify` covers it).

## Findings during implementation

Step 1 (builder, COMPILED_DENSE, COMPILED_PRUNE). No part of the exactness argument had to be dropped: both
variants are bit-identical in every tally of `verify` (seeds 0, 12345, -4172144997902289642 and seed 7). Details:

- **Block level is Paper's own tree in step 1.** `CompiledSampler` rebuilds Paper's records above the interpolated
  functions and only swaps each `InterpolatedFunction$Sampler.input` for a `CornerSampler`. The column-tiling block
  stage (step 2) replaces `CompiledSampler.blockLevel`; the corner samplers stay as they are.
- **IntervalSelect is pruned as well** (both of Paper's samplers). It selects per element exactly like RangeChoice
  (`input < threshold[i]`, else the last branch, NaN included); the spaghetti rarity selects in `entrances` are inside
  the caves graph, and as an opaque leaf all of their branch noises would run on the whole volume.
- **Paper's cache cells are left empty for the ids the evaluator owns.** After a COMPILED call, the context's cells
  for corner-level cache ids hold nothing, so later point queries (aquifer) take the point path instead of reading
  the volume buffer, as with `PAPER_UNCACHED`. The `[probe]` tally shows 0 mismatches; open review point 3 stays open.
- **Purity of sub-volume leaf calls** is checked by verify itself (BOX was the chosen plan for most partial noise
  calls), not yet by the separate `PURITY` check of section 3.
- **Pruning counters** count only the calls that happen: leaves under a skipped branch do not appear, so the
  "skipped" share of COMPILED_PRUNE understates the saving; compare its evaluated points with COMPILED_DENSE's total.

Step 2 (column tiling, COMPILED_TILE and COMPILED = pruning + tiling). Exact in every tally of `verify`; details:

- **Block stage = `TileStage`** (seam `CompiledSampler.blockLevel`). Per `(x, z)` column every tile writes all
  `sizeY` values into a column tile; the root's column is copied into the output (`indexUnchecked(x, 0, z)`).
  Element-wise tiles call the step-1 nodes' element loops (one code path for both levels).
- **`InterpolatedTile`** computes Paper's cell volume for the whole call volume and evaluates its `CornerSampler`
  on it once per call. Per column it derives `cellX`, `nextCellX = min(cellX + 1, sizeX - 1)` and `alphaX = xInCell *
  cellSizeXzInv` (same for z), then per corner level `Mth.lerp(alphaX, lerp(alphaZ, c[x,y,z], c[x,y,z+1]),
  lerp(alphaZ, c[x+1,y,z], c[x+1,y,z+1]))`, which is `fillCell`'s `v_0_`/`v_1_` (same operands, same order, so the same
  bits whether computed once per level or per cell), and per cell `value = v_0_ + valueStep * y0; value += valueStep`
  from `y0` exactly as `fillCell`. A cell's y loop never depends on other columns, so per-column order is exact.
- **Not tiled (step-1 tree instead):** any step other than 1, and step-1 volumes an interpolated function passes
  through unchanged (Paper's first branch: size 1 on every axis and cell-aligned). The `[BOX]` and `[COLUMN]` shapes
  are tiled (unaligned, no beardifier).
- **Block-level samplers without a tile** (none in the overworld tree besides the beardifier) become
  `FullVolumeTile`: their step-1 rebuild is called once on the full call volume and columns are copied out. The
  beardifier's `ContextBoundSampler` resolves per call; a constant or `Beardifier.EMPTY` (`fill(0.0F)`) becomes a
  constant column.
- **Block-level pruning (COMPILED only):** a range choice skips a branch in a column where none of its values
  survive (`whenInRange` if every input is out of range, `whenOutOfRange` if none is), and an interpolated function's
  corner volume is evaluated lazily, on the first column that needs it, so a branch no column needs never runs its
  corner graph. The noodle choice skips few columns (counters: "block range_choice ... columns").
- **Tile memory:** column tiles and corner buffers are taken from the context's arena once per call and released
  at its end, so verify poisons them; tiles are reused across columns, which the poison does not cover beyond the
  first column (verify still catches a skipped write: see IMPL-NOTES mutation checks).
- **First smoke says tiling does not pay as built** (SMOKE ONLY, 3 rounds, 1 thread): TILE 0.878x vs DENSE 0.962x,
  COMPILED 1.104x vs PRUNE 1.241x against PAPER. Not measured; the host run decides, and the IMPL-NOTES list the
  suspects to profile before any tuning.
