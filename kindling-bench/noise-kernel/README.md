# noise-kernel

Microbenchmark for one question: can a native Rust kernel called through Panama FFM, or a Java Vector API port,
beat Paper's Java `addToVolume` of `PerlinNoise` and `SmearedPerlinNoise` while staying bit-identical?
Profiling attributed 10.3 % of chunk-worker CPU during world generation to these two kernels. This experiment
hooks nothing into the server: it replays the leaf calls that the overworld volume noises (the vanilla NormalNoise
definitions and the BlendedNoise terrain stacks) make over cell-corner volumes, through every variant, on the same data.

**Result (bench host, i7-7700, JDK 25.0.5, 3 forks × 60 rounds each, 2026-10-30):** all variants bit-identical
to Paper (59 M floats, cold and after warm-up). Combined ns per output sample against `PAPER` in the same fork:

| Variant | 1 thread | 4 threads |
| --- | --- | --- |
| `PAPER` (baseline) | 10.49 ns | 12.46 ns |
| `VECTOR` (Java Vector API) | 1.37× (1.36–1.38) | 1.30×, but one fork 0.94× for its whole lifetime |
| `NATIVE_SCALAR` (Rust over FFM) | 1.36× (1.35–1.36) | 1.30× (1.29–1.33) |
| `NATIVE_SIMD` (Rust AVX2 over FFM) | 1.10× (1.09–1.10) | 1.00× (0.99–1.01) |

An empty FFM call costs 17–22 ns, about 0.015 ns per sample. Native code is not faster than Java here: a plain
Rust port and a Java Vector API port end up at the same speed, and the AVX2 kernel loses on this CPU. With the
kernels at 10.3 % of worker CPU, 1.3–1.36× would mean about +2.5 % pregen throughput (expectation, not measured),
and only for the cell-corner volume shape this workload covers.

## Layout

| Path | What |
| --- | --- |
| `rust/` | the `kindling_noise` cdylib (scalar and AVX2 kernels, C ABI) |
| `java/noisekernel/` | the harness: workload, variants, verifier, benchmark |
| `classpath.sh` | extracts the bundler's libraries once into `.cache/libs` and prints the classpath |
| `run.sh` | `build`, `verify`, `bench` |

Variants: `PAPER` (the real `addToVolume` from the server jar), `VECTOR` (Java Vector API port),
`NATIVE_SCALAR` and `NATIVE_SIMD` (FFM downcalls, `Linker.Option.critical(true)` with heap arrays), and
`NATIVE_NOOP` (an empty native call, measures the FFM overhead). `VANILLA` (Paper's private `addToVolumeVanilla`)
only runs in `--verify`, as a sanity check that `PAPER` equals the vanilla path.

## Build, verify, bench

```sh
export SERVER_JAR=/path/to/leaf-server-26.3.local-SNAPSHOT.jar
export BUNDLER_JAR=/path/to/leaf-bundler-26.3.local-SNAPSHOT.jar
./run.sh build                      # cargo build --release, then javac into java-build/
./run.sh verify                     # bit-exactness of every variant; exit code 1 on any mismatch
./run.sh verify --no-native         # same without the native library
./run.sh bench --forks 3 --threads 1 --out results
```

Environment: `JAVA` (default `java`), `JAVAC`, `KN_LIB` (default `rust/target/release/kindling_noise.dll` or
`libkindling_noise.so`), `NO_RUST=1` to skip cargo. Harness options (after the command): `--no-native`, `--threads N`,
`--warmup-sec S` (default 10), `--rounds N` (default 30, below 30 only for smoke tests), `--chunks N` (default 64
spread chunks, plus 4 chunks beyond +/-30,000,000 blocks), `--fuzz N` (random calls in verify, default 2000), `--seed N`,
`--out file.json`.

`bench` starts one JVM per fork (`--enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector`) and writes
`fork-N.json` with the raw per-round numbers, JVM, OS, `kn_simd_level` and the Vector species length. Each round runs the
whole call list once per variant, in a rotating variant order. The report gives median, min and max ns per output sample
for perlin, smeared and combined, and the speedup against `PAPER` (median). The server jar is only read; nothing is copied
from Mojang code into this repository.

`--verify` starts every call from the same non-zero random buffer, runs `PAPER` as reference and compares each other
variant over the whole buffer with `Float.floatToRawIntBits`. It also checks that the recorded leaf calls reproduce the real
`NoiseStack.addToVolume` of every group, runs random fuzz calls (odd sizes, extreme coordinates and scales), and checks the
native status codes (-1 above the per-axis cap of 1024, -2 for a too short buffer).

## Bit-exact rules

- Same IEEE operation order as the Java source, operation by operation. No fused multiply-add anywhere: no `Math.fma`,
  no `FloatVector.fma`, nothing the compiler may contract. Multiply and add stay separate.
- `float` stays `float`, `double` stays `double`, casts at the same places. Java `int` arithmetic wraps (wrapping ops in Rust).
- `(int)` of a double saturates and maps NaN to 0, as Rust's `as i32` does.
- `wrap(x)` uses `HALF_ROUND_OFF = Math.nextDown(1.6777216E7)` (`DEBUG_ENABLE_FARLANDS` is false).
- The smeared Y axis keeps `relative` as double and uses `Mth.floor(fudgeLimit / fudgeYScale + 1.0E-7F) * fudgeYScale`;
  the float literal is widened to double.
