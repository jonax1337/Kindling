# preview-chunks

Question: a server could send far chunks as previews, generated only in a vertical band around the surface, and the
real chunk once a player comes close. How much cheaper is such a preview than the TERRAIN work on the whole chunk, are
its blocks the same as vanilla's, and how big is it on the wire? This harness answers that without a server. The
server side (sending previews, replacing them, the client) is not part of it.

## Results

**A preview costs 2.6 to 2.8 times less generation work than the TERRAIN work on the whole chunk, and its zlib packet is
about 6 times smaller than a saved full chunk's.** Inside the band the density is bit-identical to the full chunk, and
of 90.7 million blocks two differ (one overhang above the band).
Bench host (i7-7700, 8 threads, JDK 25.0.5, Linux), server jar `3d026ba9…` (Kindling, commit 86711db), 300 chunks
(100 random chunks per seed, seeds 1, 2, 3, within 2000 chunks of 0, 0), 3 forks of 30 rounds after 3 warmup rounds.

| Possible biomes | Threads | TERRAIN ms per chunk | PREVIEW ms per chunk | TERRAIN / PREVIEW (range over forks) | Lowest single round |
| --- | ---: | ---: | ---: | ---: | ---: |
| none | 1 | 17.21 | 6.28 | **2.74** (2.73–2.76) | 2.48 |
| none | 4 | 21.23 | 7.60 | **2.80** (2.79–2.81) | 2.31 |
| own chunk | 1 | 10.33 | 3.91 | **2.64** (2.61–2.66) | 2.14 |
| own chunk | 4 | 12.87 | 4.81 | **2.67** (2.65–2.69) | 2.16 |

TERRAIN is the reference: biomes for the whole chunk, the density fill (`doFill`) and the surface rules
(`MaterialSystem.buildSurface`) over all 384 blocks of height, as the server's BIOMES and TERRAIN statuses run them,
minus carvers. PREVIEW is the same vanilla code over the band, plus stone below the band. "Possible biomes" is the set
the surface rules fold biome conditions with: the server passes the biomes of the 3 × 3 chunks around, which the
harness does not have, so it measures both bounds: none (no folding, slower than the server) and the chunk's own biomes
(fewer biomes than the server's set, so faster). The absolute times move by 40 %, the ratio by 0.1. The gap between the
variants is more than 20 times the larger fork range.

Exactness, 1200 random chunks (400 per seed):

| Check | Compared | Different |
| --- | ---: | ---: |
| density inside the band, bit for bit against the full chunk | 19,146,752 points | 0 |
| noise biomes of the band's sections | 335,936 quarts | 0 |
| blocks from the band's bottom to the top of the world | 90,697,728 blocks | 2 |
| worldgen heightmaps (`WORLD_SURFACE_WG`, `OCEAN_FLOOR_WG`) | 307,200 columns | 1 |

The two blocks and the heightmap column are one overhang: dirt and grass at y 104–105 in one column of one chunk
(seed 3, chunk −1840, −1478), above the band with air between. The band grows only while terrain touches its top
layer, so it does not see terrain that floats above it. With 40 instead of 24 blocks above the preliminary surface
the chunk is identical, and its band is 16 blocks higher. A first run, where the full chunk and the preview computed
their neighbour biomes separately, showed 22 differing blocks; the 20 that went away when both read the same
neighbour biomes were all printed from one badlands chunk edge, and that chunk is identical when they share them.

The band is 62 blocks high on average (16 % of the world's 384; minimum 56, 99th percentile 96, maximum 136). It
starts at the chunk's preliminary surface levels ± 24 blocks and at least the sea surface; it grew in 5.2 % of the
chunks (at most 3 times), because the terrain top lies up to 90 blocks above the preliminary surface (99th percentile
41) and up to 51 below it.

Packet sizes (Java `Deflater` at its default level, the body of a chunk-with-light packet):

| Chunks | Packet | Raw median | zlib median (mean) |
| --- | --- | ---: | ---: |
| 1500 saved full chunks (uniform sample of a Kindling-generated world) | saved chunk with its stored light | 33,403 B | **4,521 B** (4,931) |
| the same 1500 positions | preview | 13,428 B | **761 B** (810) |
| 300 generated chunks | TERRAIN chunk (no ores, carvers or features), estimated light | 23,694 B | 1,417 B (1,511) |
| the same 300 | preview, estimated light | 13,426 B | 693 B (749) |

Against the saved world, the preview's terrain top (the highest block that is not air, fluid, leaves, a log or
passable) is at the same height in 94.4 % of 384,000 columns, 1 off in 1.1 %, 2–3 off in 1.8 %, 4–7 off in 1.7 % and 8
or more off in 1.1 %. The highest non-air block is the same block at the same height in 78.9 %: the preview has no
trees, plants, snow layers or structures.

What this means for a server is an expectation, not a measurement. Pregeneration on the same host makes about 140
chunks per second with 5 workers ([pregeneration](../../docs/measurements/pregeneration.md)), about 36 ms of worker
time per full chunk with features, light and saving. A preview at 4 to 8 ms would then be roughly 5 to 9 times cheaper
than a full chunk, and a preview packet about a sixth of a full chunk's bytes.

<details>
<summary><b>Method</b></summary>

- **Registries** come from the server jar's built-in data pack, loaded as Paper's test `RegistryHelper` loads them
  (static layer with tags, then the worldgen registries), with the overworld noise settings and the multi-noise biome
  source. Leaf and Kindling switches keep their code defaults, which are their config defaults
  (`compiledDensity` and `compiledMaterialRules` on, `biomeZoomShortcut` and FastRNG worldgen off); `setup` lines and
  the JSON record them with the jar's SHA-256.
- **TERRAIN** calls the server's own code: `ChunkGenerator.createBiomes`, the private
  `NoiseBasedChunkGenerator.doFill` through a method handle, and `MaterialSystem.buildSurface` with a
  `WorldGenerationContext`. Paper's flat-bedrock rule reads the world config from a stand-in level (flat bedrock off,
  Paper's default). Left out for both variants: carvers (they need a `WorldGenRegion`), blending, and structures
  (the beardifier is empty).
- **PREVIEW** samples the preliminary surface level of the 256 columns, takes the cells from its minimum minus 24 to
  its maximum plus 24 (and at least to the sea surface), runs the same three steps on that band only, and starts again
  16 blocks bigger while terrain touches the band's top layer or a column has fewer than 16 blocks of terrain inside
  the band. Interpolation cell corners sit at absolute multiples of 8 (`InterpolatedFunction`), so the density at a
  position does not depend on the volume it is computed in. The bench times it including the stone below the band.
- **Biomes outside a chunk's generated sections** (neighbours, and the sections a preview leaves out) are computed
  from the climate once per quart and kept. The climate's biome search remembers its last result (MC-241546), so a
  quart can come out differently depending on what was computed before it. Verify shares these neighbour biomes
  between the full chunk and its preview, as a server's stored neighbours would be shared; the bench gives each
  generation its own. (Bench and packets ran before Verify's sharing was added; it does not touch their code path.)
- **Bench**: every thread generates the whole workload once per variant per round; all threads start a pass together
  (a `CyclicBarrier`), the order of the variants alternates per round, a failing thread ends the run. One JVM is one
  fork. `report` takes per fork the median over rounds and the median per-round ratio, then the median and range
  over forks.
- **Verify** compares every block from the band's bottom to the top of the world, counted by depth below the column's
  terrain top, plus the noise biomes of the band's sections, the two worldgen heightmaps, and the band's density bit
  for bit against the full chunk's. Exit code 1 when anything differs.
- **Packets** lays out a chunk-with-light packet body as `ClientboundLevelChunkPacketData` and
  `ClientboundLightUpdatePacketData` write it (three heightmaps, sections, no block entities, light masks and arrays,
  one light section below and one above). Generated chunks get an estimated sky light (straight down, one less per
  fluid block), saved chunks their stored light. The saved chunks are a uniform random sample of a region directory's
  full chunks.

</details>

<details>
<summary><b>Caveats</b></summary>

- The reference is the TERRAIN work without carvers, so the server's real per-chunk cost is higher than TERRAIN here;
  the ratio against a full chunk is larger than the ratio measured.
- Neighbour biomes are computed in the harness and stored in the server; both variants pay that the same way.
- A preview that needs structures (villages flatten terrain through the beardifier) needs the chunk's structure starts,
  which this harness does not compute.
- The growth loop starts again from an empty chunk, so PREVIEW is an upper bound for a smarter implementation.
- The sample is 3 seeds, random positions, overworld only. The saved world is the pregenerated flight-corridor world of
  the elytra test (seed -4172144997902289642), not a lived-in world.
- The bench host ran its usual background programs (12–28 % CPU); variants alternate per round, so both see the same.
- The estimated light and the stand-in heightmap types change sizes by a few bytes; the light a client needs for a
  preview is untested.

</details>

## Layout

```
run.sh                       build | verify | bench | report | packets
classpath.sh                 server jar + the libraries extracted from the bundler jar
java/previewchunks/
  Main, Options, Setup       dispatch, command line, what a run measured
  Vanilla, World, StandInLevel   registries and generator, RandomState per seed, Paper's flat-bedrock config
  Terrain, ChunkBiomes       the server's BIOMES/TERRAIN steps on any part of a chunk; biomes the surface rules read
  Band, Margins, Preview, Skin   the band, its sizing, the preview generator, stone below the band
  Workload                   chunk positions per seed
  Verify, Bench, Report, Stats   checks; timing; combining forks
  ChunkPacket, RegionReader, Packets   packet layout and size; reading saved chunks; the size comparison
```

## Run

```bash
sh run.sh build
sh run.sh verify --seeds 1,2,3 --chunks 400
sh run.sh bench --forks 3 --threads 1 --possible-biomes none --seeds 1,2,3 --chunks 100 --out-dir results/t1-none
sh run.sh report --dir results/t1-none
sh run.sh packets --seeds 1,2,3 --chunks 100 --world <region dir> --world-seed <seed> --limit 1500
```

Options: `--seeds a,b,c`, `--chunks N` (per seed), `--radius R` (chunks around 0, 0), `--at "x,z;x,z"` (fixed chunks),
`--above N`, `--below N`, `--skin N`, `--grow N` (band sizing), `--possible-biomes none|own`, `--threads N`,
`--rounds N`, `--warmup-rounds N`. Env: `JAVA`, `JAVAC`, `SERVER_JAR`, `BUNDLER_JAR`; without them `run.sh` uses the
jars of `E:/DEV/Minecraft/cubyx-leaf` and says so. Do not run long benchmarks on a gaming PC.
