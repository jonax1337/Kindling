# vanilla-diff

A differential test: it runs Mojang's official server jar and a server jar under test (Kindling, Leaf or Paper) on the same seed with the same scripted console input, then compares the two saved worlds. A difference means the jar under test does not do what vanilla does. The cases are the ones that tuned servers tend to break quietly: redstone update order, entity order, game events, block entity contents, world generation.

> [!NOTE]
> **Status.** The comparer, the fetch script and the command generators are tested (`python -I test_compare_worlds.py`, 36 tests on synthetic region files; the generated block, item and entity ids were checked against the 26.3 server jar). `run.sh` has run against real servers since 2026-10-29. Contraptions mode, 72 cells, 400 ticks: vanilla against vanilla differs in 0 blocks (80 entity and furnace-timing differences are the tool's noise), release 26.3-12 in 8 blocks and 5 chests, the Kindling build in 0 ([results](../../docs/measurements/vanilla-parity.md#vanilla-diff-against-mojangs-jar)). Worldgen mode is not conclusive: vanilla against vanilla already differs in 410 of 624 chunks. Do `./run.sh --self-check` first (vanilla against vanilla): a differential test is only worth anything once vanilla agrees with itself.

## Run it

```bash
./run.sh --jar ../jars/kindling-26.3-4rc.jar                 # the contraptions mode
./run.sh --jar ../jars/kindling-26.3-4rc.jar --mode all      # contraptions, then worldgen
./run.sh --self-check                                        # vanilla against vanilla: must print "identical"
```

Needs Java 25 on the path (or `JAVA=/path/to/java`), Python 3.10+ (or `PYTHON=...`), bash (Linux, WSL, Git Bash) and network access for the first run: the official jar is downloaded once through Mojang's version manifest, its sha1 is verified, and it is cached in `jars/` (gitignored). A Paper-based jar (Paperclip) downloads that jar again by itself on its first start in each fresh folder.

| Option | Meaning |
| --- | --- |
| `--jar FILE` | the server under test |
| `--mode contraptions\|worldgen\|all` | default `contraptions` |
| `--keep` | keep the run folder (`runs/<time>-<mode>/{vanilla,test}`) with both worlds, logs and the generated commands |
| `--self-check` | use the vanilla jar as the jar under test as well |
| `--overlay DIR` | copy `DIR` into the folder of the server under test (for example a `config/leaf-global.yml` with non-default options); the vanilla server never gets it |
| `--vanilla-jar FILE`, `--vanilla-version V` | use a jar you already have / another version (default `26.3`) |
| `--heap 2G` | `-Xmx` of both servers |

Exit code: `0` identical, `1` differences, `2` something other than a difference went wrong (a server did not start, a wait timed out, the jar is missing). In the case of `2` the run folder is kept and its path printed; otherwise it is deleted unless `--keep` is given.

Both servers listen on `127.0.0.1:25999` only, one after the other, in a fresh folder with `eula=true` and `online-mode=false`. Nothing else is configured differently between them. Expect minutes, not seconds, per mode (not yet timed on a real server).

## What a run does

1. `gen_commands.py` writes `commands.txt`, `areas.json` (the chunks that are compared) and `mode.properties` for the mode.
2. `run.sh` starts the server, waits for the `Done (...)!` line, then feeds `commands.txt` through standard input. Two directives are executed by the script itself, so that nothing depends on wall-clock sleeps:
   - `@await-loaded x1 z1 x2 z2` sends `execute if loaded ... run say LOADED x z` for every chunk that has not answered yet, until all have;
   - `@step n` reads the game time (`time query gametime`), sends `tick step n` and polls until the game time has advanced by exactly `n`.
3. The last commands are `save-all flush` and `stop`; the script waits for the server to exit.
4. `compare_worlds.py` compares the two `world` folders (the old `world_nether` / `world_the_end` layout, `DIM-1` / `DIM1` and `dimensions/<namespace>/<name>` are all found) and exits with `1` on any difference.

## The modes

### contraptions

A flat world with one stone layer (the generator settings are in `contraptions.py`), `randomTickSpeed 0`, `doMobSpawning false`, `doWeatherCycle false`, `doDaylightCycle false`, and also fire spread, tile drops, entity drops and mob loot off. The server is frozen (`tick freeze`) while 72 cells are built, so nothing runs until the first `tick step`. Each cell is a 10 x 10 footprint **in its own chunk**, on a two-layer stone platform, inside a forceloaded rectangle with a chunk of margin all around. The cell is shifted inside its chunk by a different amount each, so the `BlockPos` values (and their hash order) differ from cell to cell. Then the game is stepped 400 ticks in nine steps (1, 1, 2, 5, 11, 20, 60, 100, 200); some designs are touched again between the steps ("stages").

| Design | Cells | What it exercises |
| --- | --- | --- |
| `dust_comb` | 4 rotations | a dust line over supports, pistons beside the supports, observers watching the dust: update order of dust against pistons and observers |
| `observer_ring` | 2 | two observers that watch each other (a clock), and a chain of observers started by a placed block |
| `piston_slime` | 4 | a sticky piston moving a slime/honey structure, with and without an obsidian block in it, then pulling back |
| `hopper_chain` | 4 | hoppers into a chest, a dropper into a hopper, a lock released later, a comparator on the chest |
| `hopper_pile` | 4 | six item entities falling on one hopper, or on the border of two: entity order |
| `dropper_dispenser` | 2 | a dropper into a chest (pulsed twice), a dispenser that fills a walled pool with water, one that places a shulker box |
| `comparator_containers` | 2 | comparators on a chest, a barrel, a hopper, a furnace (smelting) and a composter |
| `pressure_plates` | 8 | an item crossing two plates toward -x or +x, each plate driving a piston against the same block (order of inside-block visits) |
| `tripwires` | 8 | an item crossing a tripwire between two hooks, toward -x or +x |
| `detector_rails` | 8 | a chest minecart over two detector rails, toward -x or +x |
| `note_sculk` | 4 | note blocks starting game events, sculk sensors (one behind wool), comparators on them, a second note block later |
| `redstone_soup#n` | 22 | seeded random redstone (dust, pistons, observers, repeaters, comparators, torches, slime): the mechanisms nobody thought of |

Every design is built with local coordinates and rotated by a quarter turn per step (`cell.py`), so the same design is also a different `BlockPos` pattern. Droppers never eject into the air and nothing destroys blocks into drops, because both use the game's unseeded random and would differ between two vanilla runs.

### worldgen

The same seed (`-4172144997902289642`, the one of `run_bench.py`), default world type with structures, and 14 forceloaded areas (624 chunks): around spawn, four 6 x 6 squares on rings at 1000, 2000 and 6000 blocks, and a 64 x 2 strip for biome borders. There is no way to name "an ocean" or "a mountain" without knowing the seed, so coverage comes from spread; edit `chunk_areas()` in `worldgen_script.py` to add areas you know. After `save-all flush` the chunks are compared. Entities are not compared in this mode (`--skip-entities`): mobs spawned by world generation walk around for the minutes the run lasts.

## What is compared, and what is not

Compared, per chunk that is `full` in both worlds and inside `areas.json`:

- **block states** at every position (palette-decoded, so palette order and bit width do not matter; a missing section equals an all-air section),
- **biomes** per quart cell,
- **block entities**, by position, as full NBT; for containers the item totals are added to the report ("totals differ" against "totals equal, slots differ"),
- **entities** (contraptions mode), per chunk, as a multiset: position, motion, item, age and the rest, in any order,
- **scheduled block and fluid ticks**.

Not compared (`python -I compare_worlds.py --list-ignored` prints the current list):

| Ignored | Why |
| --- | --- |
| `LastUpdate`, `InhabitedTime`, `DataVersion` | wall-clock and bookkeeping |
| `Status` of chunks that are not full, chunks outside `areas.json` | generation order and spawn-area size legitimately differ |
| `Heightmaps`, `structures`, `CarvingMasks`, `PostProcessing`, `UpgradeData`, `blending_data` | derived or generation-time data |
| `isLightOn`, `BlockLight`, `SkyLight` | light may be recomputed by a different light engine; add it deliberately when you want it |
| `UUID`, `keepPacked`, `Leash` (inside block entities and entities) | random per run |
| keys starting with `Paper.`, `Spigot.`, `Bukkit`, `Leaf.`, `Kindling.` | tags that only those servers write (`Paper.FailedSpawnAttempts`, `Spigot.ticksLived`, `Paper.Origin`, `BukkitValues`) |
| region file timestamps, sector layout, compression | storage details; the decoded chunk is what counts |
| `level.dat`, `playerdata`, `poi`, scoreboard, raids | not read |

A chunk that is full in one world and not full or missing in the other, and a chunk that cannot be read, are reported as differences. A tag that a Kindling, Leaf or Paper build legitimately adds can be added to `ignored.py`; add it with the reason.

## Reading the report

An example (made up) of what a report looks like:

```
RESULT: 3 differences in 2 chunks
by category: block 2, entity 1

minecraft:overworld
  chunk (4, 3) (blocks x 64..79, z 48..63): 2 differences
    block: (70, -58, 52) minecraft:piston[extended=true,facing=south] -> minecraft:piston[extended=false,facing=south]
    block: (70, -57, 53) minecraft:stone -> minecraft:air
```

`A` is the vanilla world, `B` the jar under test. Positions are world coordinates; the chunk header gives the block range, so the cell (and from `commands.txt`, the design) can be found. Categories: `block`, `biome`, `block entity`, `container`, `entity` (`only in A` / `only in B`: a moved item is one of each), `scheduled tick`, `chunk`, `dimension`. `--limit N` raises the number of lines per chunk (default 20).

How to judge a difference:

1. Run `--self-check` several times. A difference there is nondeterminism in the test (or in vanilla), not in the jar: take that design out or make it deterministic before trusting any other result.
2. Look at the first difference in the earliest cell in the report; later ones are often consequences. Re-run with `--keep` and look at the cell in a client, or at the same position in both `world` folders.
3. Compare with plain Paper and plain Leaf, to tell whether Kindling introduced it (AGENTS.md: `Kindling`, `Leaf`, `Upstream`).

## Files

| File | Job |
| --- | --- |
| `run.sh` | the runner |
| `fetch_vanilla.py` | download and verify the official jar |
| `gen_commands.py`, `commandscript.py` | write the command file, the compared areas and the mode's `server.properties` lines |
| `contraptions.py`, `designs.py`, `fuzz.py`, `cell.py` | the contraptions mode: layout, hand-made designs, seeded random redstone, rotated cells |
| `worldgen_script.py` | the worldgen mode |
| `compare_worlds.py`, `worlddiff.py`, `chunkdiff.py`, `ignored.py`, `anvil.py`, `nbt.py` | the comparer (standard library only; run with `python -I`) |
| `test_compare_worlds.py` | its tests: `python -I test_compare_worlds.py` |

## Known limits

- The designs were written against the 26.3 command syntax and ids (checked in the jar). If a design does nothing (a piston that never extends), it tests nothing, and the comparer has no way to know; the differences found on 26.3-12 (an unpowered wire network, chests filled in another order) show that at least those cells do something.
- `@step` relies on `time query gametime` answering `The game time is N tick(s)` and on commands being processed while the game is frozen.
- Bash 4+ (associative arrays) is required.
