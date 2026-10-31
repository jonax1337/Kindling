# density-compiler

Question: can a different evaluation of the 26.3 worldgen density graph make `finalDensity.sampleVolume` per chunk
faster while every output float stays bit-identical to Paper? The plan is in [DESIGN.md](DESIGN.md).

## Results

**Branch pruning makes `finalDensity.sampleVolume` 1.24–1.26× faster per chunk, bit-identical; column tiling makes it
slower.** Bench host (i7-7700, JDK 25.0.5, Linux), 3 forks × 30 rounds × 588 chunks per thread count, 2026-10-30.
Verify and every fork's warm re-verify passed on the host. Median ms per chunk, speedup against `PAPER` in the same fork
(range over the forks):

| Variant | 1 thread | 4 threads |
| --- | --- | --- |
| `PAPER` (baseline) | 2.674 ms | 3.395 ms |
| `COMPILED_DENSE` | 0.984× (0.981–0.986) | 0.985× (0.984–0.986) |
| `COMPILED_PRUNE` | **1.256×** (1.254–1.258) | **1.239×** (1.236–1.243) |
| `COMPILED_TILE` | 0.892× (0.891–0.897) | 0.935× (0.926–0.937) |
| `COMPILED` | 1.161× (1.159–1.166) | 1.214× (1.206–1.217) |

`PAPER` alone in its own forks (`--paper-solo`) runs within 0.5 % of `PAPER` in the mixed forks, so the shared call
sites do not distort the baseline. Pruning evaluates about 45 % fewer leaf points. The node graph alone costs about
1.5 % (`COMPILED_DENSE`), and tiling the block level costs 6–11 %, so the candidate is `COMPILED_PRUNE` without tiling.

What this means for a server is an expectation, not a measurement: if `finalDensity` with its noises is 25–31 % of chunk
worker CPU (the profile's density graph plus noise primitives, an upper bound), 1.25× saves 5–6 % of that CPU. Not
covered here: blending, the aquifer's use of the context afterwards, other routers' timings, and per-thread state (the
evaluator is not yet safe to share across threads; DESIGN.md review point 9).

## Variants

| Variant | What it does | Role |
| --- | --- | --- |
| `PAPER` | Paper's own compiled `finalDensity` sampler | reference |
| `PAPER_UNCACHED` | the same, context without caches | verify only: Paper's caches are value-transparent |
| `COMPILED_DENSE` | corner volumes through the node graph, every branch on the full volume; block level is Paper's tree | evaluator overhead |
| `COMPILED_PRUNE` | as DENSE, range choices, lerps and interval selects evaluate a branch only where Paper keeps it | pruning alone |
| `COMPILED_TILE` | as DENSE, block level per column in small tiles (interpolation replicated op for op) | tiling alone |
| `COMPILED` | pruning and tiling | the candidate |

The block stage (tiling) handles step-1 volumes; any other step, or a volume an interpolated function passes through
unchanged (single aligned blocks), takes the step-1 tree. Block-level samplers it does not model are called once on
the full volume, as Paper would. DESIGN.md has the exactness argument ("COMPILED design", "Findings during
implementation").

## Layout

```
run.sh                         build | verify | selftest | dump | bench
classpath.sh                   server jar + libraries extracted from the bundler jar
java/densitycompiler/          harness
  Main, Options                dispatch and command line
  World, Chunk, Workload       bootstrap, RandomState per seed, chunk list, synthetic beardifier
  ChunkCall                    one doFill-equivalent density call (arena, NoiseChunk context, sampleVolume)
  Variant, Variants, PaperVariant, CompiledVariant, BoundVariant   the variant interface, its registry, the variants
  Verifier, NaiveOracle        bit compare against the reference; informational per-point oracle
  Bench, Measurements, Stats, Report              interleaved rounds, JSON per fork
  SelfTest, BitFlipVariant, BrokenVariant, FailingVariant   prove the verifier detects wrong variants and the bench ends when a thread fails
  PoisonedArena, Probe         verify-only: poisoned buffers; point queries on the context after the call
java/densitycompiler/eval/     the evaluator
  CompiledSampler, Techniques  DensitySampler facade; which techniques are on
  TreeBuilder, SamplerRecords, Purity, TreeDump   destructuring Paper's sampler records; leaf purity; dump
  Node and *Node, Frame, Demand, CornerSampler, LeafCalls, ResizableBuffer, Tuning, PruneCounter   corner level (pruning)
  BlockLevelRebuild            Paper's block-level tree around the corner samplers (step-1 block level, fallback)
  TileBuilder, TileStage, TileFrame, Tile and *Tile   block level per column (tiling)
```

Adding a variant: write a class implementing `Variant` (its `sampler(World)` returns a `DensitySampler`, called once
per world and thread) and list it in `Variants`. The first entry is the reference; every other entry is verified
against it, and `Variant.benchmarkedByDefault` decides whether bench runs it without `--variants`.

## Run

```bash
sh run.sh build
sh run.sh selftest                      # the verifier must catch a deliberately wrong variant
sh run.sh verify                        # every candidate vs PAPER, bit for bit, plus the NAIVE info line and pruning counters
sh run.sh verify --synthetic-only      # only the synthetic selector trees (thresholds, NaN, signed zero, repeated volumes)
sh run.sh mutants                       # builds deliberately broken copies; each must fail the synthetic verify (about 4 min)
sh run.sh dump --seed 0                 # Paper's compiled sampler tree of finalDensity
sh run.sh bench --forks 3 --threads 1   # writes results/fork-N.json
sh run.sh bench --forks 3 --paper-solo  # plus paper-solo-fork-N.json: PAPER alone, to compare with PAPER in the mixed forks
```

Env: `JAVA`, `JAVAC`, `SERVER_JAR`, `BUNDLER_JAR`. Without them `run.sh` uses the jars of `E:/DEV/Minecraft/cubyx-leaf`
when this worktree has no `leaf-server/build` of its own.

Useful options (all subcommands): `--seeds a,b,c`, `--dimension overworld|amplified|large_biomes|nether|end|caves|floating_islands`,
`--chunks N` (random chunks per seed), `--square N`, `--far N`, `--beard-every N` (0 = no synthetic structure), `--fuzz N`
(verify only), `--columns N`, `--boxes N` (verify only), `--naive-chunks N`, `--naive-points N`, `--seed N` (one seed). Bench: `--threads N`, `--warmup-sec S`, `--rounds N`,
`--variants A,B` (e.g. `PAPER,COMPILED_DENSE,COMPILED_PRUNE,COMPILED_TILE,COMPILED`), `--out dir`. Fewer than 30 rounds
prints a smoke-test note. The pruning cost model is `Tuning.DEFAULT` (call cost 8 points, run gap 1) unless `--tuning callCost,runGap`
says otherwise (`--tuning 0,0` and `--tuning 100000,8` send leaf calls to the run and the full plan); `--synthetic-only` skips the real worlds.

Local smoke run on a development PC (about 12 s): `sh run.sh bench --forks 1 --warmup-sec 3 --rounds 3 --chunks 16 --square 2 --far 2`.
Do not run long benchmarks on a gaming PC.

## How it verifies

* Reference is `PAPER`: Paper's own `DensityFunctionCompiler` sampler, real caches, real thread-local arena, called
  exactly like `NoiseBasedChunkGenerator.doFill` does for density (context as `NoiseChunk` builds it).
* `Float.floatToRawIntBits` must be equal. NaN is allowed only where the reference is NaN too; those pairs are counted,
  and pairs with different NaN payloads are counted separately.
* `PAPER_UNCACHED` runs the same sampler with a context without caches. It must equal `PAPER`; it shows that Paper's
  caches are value-transparent, which the pruning design relies on.
* `NAIVE` compares `sampleValue` at K random points with the `sampleVolume` result. Paper's two paths are not
  bit-identical, so this line is information, not pass/fail.
* Every buffer the arena hands out is poisoned (a finite float, `0x7f7fdead`) in the verify path, so a variant that
  forgets to write an element, a tile or a column fails instead of inheriting the reference's old values. The order
  of reference and candidate alternates per chunk.
* Besides chunks, verify runs `1 x height x 1` columns (what `iterateNoiseColumn` asks for: any block, no beardifier) and
  random unaligned boxes, and compares aquifer-style point queries on the context after the call (`[probe]`). Tallies
  are printed per shape.
* A second pass repeats every volume on the same instances, in reverse order and twice in a row (`[repeat]`), because
  the server repeats volumes and state left by an earlier call only shows then.
* The real routers never put a value on a selector threshold and never produce NaN, so `SyntheticSuite` also checks
  synthetic trees (range choice, interval select, lerp, shared cache ids, block level and corner level) whose inputs
  are exact small integers, `+-0.0`, NaN and infinities, against Paper's samplers of the same trees, at three
  cost-model tunings. `sh run.sh mutants` proves it can fail: each of 11 deliberately broken copies of the evaluator
  must be rejected.
* After the timed rounds every bench thread verifies the very instances it timed against the reference (`WARM VERIFY`,
  same thread and arena, chunks plus the verify-only volumes). It covers only the variants bench ran.

## Caveats

* Registries come from `VanillaRegistries.createWorldLookup()`, not from a loaded datapack.
* No blending (`Blender.empty()` equivalent: no blender keys in the context), no aquifer, no surface or carvers. With a
  real blender the evaluator falls back to Paper's own sampler for the blended subtree; that path is not exercised here.
* The beardifier is `EMPTY` for most chunks and a synthetic rigid piece for every N-th chunk.
* The JIT profile of one router in a clean JVM differs from a running server. The final answer needs a Kindling build
  on the bench host.
* Run-to-run numbers on a shared PC mean nothing; only the host runs count.
