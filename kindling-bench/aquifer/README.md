# Aquifer experiment

Does C2ME's aquifer optimisation, ported to 26.3, make `Aquifer.NoiseBasedAquifer.computeSubstance` faster while
returning exactly vanilla's results? Plan and exactness analysis: [DESIGN.md](DESIGN.md). Running log:
[IMPL-NOTES.md](IMPL-NOTES.md).

Parity is the hard requirement. A technique that cannot be shown exact is dropped, not tuned.

## Results

Parity: PORT, C2ME, C2ME_LAZY and C2ME_BRANCHY give 0 differences against VANILLA (state, flag, sampler and picker
trace) on overworld, large_biomes and amplified, two seed sets (2026-10-30). C2ME's code as written is not exact on 26.3
(self-test mutant `C2ME_LITERAL`: 7 result/flag differences on 36 cases).

Speed (bench host, i7-7700, JDK 25.0.5, 1 thread, 30 rounds per fork, 2026-10-30): the fill replay is **bimodal**.
In 13 of 21 forks C2ME is 1.13–1.23× VANILLA, in the other 8 it is 0.98–1.01×; VANILLA_B stays within 0.99–1.01× in
every fork. Construction is 2–4 % slower (about 150 µs against 1 770 µs of fill per chunk), the column replays are
unchanged (0.99–1.02×). Construction plus fill: median 1.14×. C2ME_LAZY behaves the same (1.136×). The intended solo
forks did not run solo (the host command passed `--variants` after run.sh's own, which overrode it), so all 21 forks are
mixed; per-fork VANILLA medians agree within 2 %.

Verdict: exact, but small and JIT-dependent. With the aquifer at 8.8 % of worker CPU, 1.14× saves about 1 % of worker
CPU (expectation, not measured), below what a pregeneration A/B can resolve. Not worth a patch on its own.

## How it works

- `World` builds a real `RandomState` per seed and preset (overworld, large_biomes, amplified), the preset's
  `Aquifer.Config`, the aquifer random factory and the real fluid picker (read by reflection from a real
  `NoiseBasedChunkGenerator`).
- A `Variant` builds the aquifer from the arguments of `Aquifer.Config.create`. `VANILLA` is Mojang's class through
  `Config.create`; `VANILLA_B` is an independent second instance of it (the harness sanity check and the noise floor).
  A new variant is one class plus one line in `Variants`. Candidates (`candidate/`): `PORT` (Mojang's code line by
  line on `BaseAquifer`), `C2ME` (all exact C2ME techniques, locations filled in the constructor), `C2ME_LAZY` (same,
  locations filled on first visit), `C2ME_BRANCHY` (same as C2ME but vanilla's if/else insertion, to isolate T3).
- `ChunkCase` + `CallList`: the replayed `computeSubstance` calls with real `finalDensity` values. Groups:
  `FILL` (doFill order) then `CARVE` (density 0.0 on the same instance), `COLUMN` (1 x height x 1), `SHUFFLE`
  (random order, fuzzed densities), `EDGE` (+-30,000,000, Legacy random source), `DEEP` (a 4064-high volume at minY -2032 in a
  derived `deep-sea` world, so the bottom cells reach the BlockPos Y wrap) and `FLOODED` (chunks of a derived sea-level -10 world:
  fluid levels on the lava test's edge, water against lava).
- `Verifier` replays each case on the reference and on each candidate (own sampler context, own logs) and compares per
  call the returned `BlockState` by identity and `shouldScheduleFluidUpdate()`, and per phase the full sampler call
  trace and fluid picker call trace. `RealChunkCheck` compares the harness VANILLA with a real `NoiseChunk`.
- `PartChecks` compares the pure pieces of the C2ME-family candidates (similarity, pressure, fluid type, stored cell locations)
  with Mojang's private methods and cache by reflection, bit for bit, on grids that put every boundary under test.
- `SelfTest` requires every registered candidate to pass and runs 20 deliberately broken variants: eight generic ones
  (`BrokenAquifer`) and twelve on the C2ME candidate (`broken/BrokenC2meAquifer`: C2ME's tie-break, its fourth-slot skip,
  C2ME as written, stale barrier, kept flag, status order, ...). Each must be rejected by the expected check; two
  float-rounding mutants (`SPREAD_DOUBLE`, `C2ME_FLOOD_ALGEBRA`) are only reported, because the data rarely reaches them;
  `NO_BLOCKPOS_WRAP`, `SIMILARITY_MUL` and `LAVA_LEVEL_LT` must be rejected by the part checks. Two single-token mutants
  (`> 0.3` to `>= 0.3`, `< -2.0` to `<= -2.0`) are proven equivalent at run time.
  `--chunks/--columns/--shuffles` make it use a bigger case set.
- `Bench` times, per round and variant in rotating order, aquifer construction (per chunk), the fill replay (per chunk)
  and construction plus the replay of a column, whole (getBaseColumn) and up to the first block that is not air (getBaseHeight); finalDensity runs between construction and replay, untimed.
  After the last round each thread verifies the timed variants against the reference on its warm code.

## Commands

```sh
sh run.sh build
sh run.sh verify                                   # about 10 s
sh run.sh verify --seed 7 --presets overworld --chunks 8
sh run.sh selftest
sh run.sh bench --forks 1 --threads 1 --rounds 5 --warmup-sec 3 --out results/smoke   # smoke only: about 16 s
sh run.sh bench --forks 3 --solo ...               # host runs only: solo forks per variant
```

`run.sh` uses `JAVA_HOME`, else an installed JDK 25, else `java` on the PATH. `SERVER_JAR` and `BUNDLER_JAR` default to
the cubyx-leaf build.

## Caveats

- The harness has no beardifier or blender in its sampler context (the aquifer's six functions do not read them).
- The server jar under test was built at commit 23a2629d; `Aquifer.java` is Mojang's unpatched class there.
- Bench numbers from fewer than 30 rounds on a shared desktop are a smoke test, not a measurement.
