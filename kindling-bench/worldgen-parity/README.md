# worldgen-parity

Compares the world generation of a server jar (Paper, Leaf, Kindling) with Mojang's own 26.3 server jar, block for
block, in a way that gives exactly 0 differences when Mojang's jar is compared with itself. Why that is hard and how it
works: [DESIGN.md](DESIGN.md). What was run and seen: [IMPL-NOTES.md](IMPL-NOTES.md).

Result on the dev profile (one 4 x 4 sweep at a desert village, seed -4172144997902289642, this PC, 2026-10-30):
Mojang's jar against itself **identical in everything compared**, 3 of 3 pairs of fresh runs (and 4 of 4 pairs of an
earlier round with the same settings); every planted change found, nothing else. Kindling (`work/kindling-hardening`
at 23a2629) against Mojang: **2 terrain blocks and 18 entities differ**; details in IMPL-NOTES.md.

## How it works, in short

1. `bootstrap`: Mojang's jar generates the row above and the column left of each location's rectangle (the "L").
2. `run`: each jar gets a copy of that world, freezes the game, and forceloads the rectangle one chunk at a time in
   raster order with one generation thread. With the L in place every step raises exactly one chunk to each
   generation status, so no scheduler can reorder anything. A JFR recording of `minecraft.ChunkGeneration` proves the
   order afterwards; a run whose order does not match gets no verdict.
3. `compare`: every saved chunk around the location, whatever its status (blocks, biomes, block entities, scheduled
   ticks, structure starts and references, heightmaps, post-processing marks, entities, every other field).

## Commands

Standard-library Python 3.10+, always with `-I`. Java 25 (`--java`, default the Adoptium 25 path of this PC or
`$JAVA`). Every server listens on 127.0.0.1:25999 only, one at a time.

```bash
M=path/to/minecraft-server-26.3.jar      # Mojang's jar; ../vanilla-diff/fetch_vanilla.py fetches and checks it
python -I parity.py bootstrap --vanilla-jar $M --profile dev --out runs/boot
python -I parity.py run --jar $M --vanilla-jar $M --bootstrap runs/boot --profile dev --out runs/v1
python -I parity.py run --jar $M --vanilla-jar $M --bootstrap runs/boot --profile dev --out runs/v2
python -I parity.py compare runs/v1 runs/v2                 # self-check: must say VERDICT: identical
python -I parity.py run --jar kindling.jar --vanilla-jar $M --bootstrap runs/boot --profile dev --out runs/k1
python -I parity.py compare runs/v1 runs/k1 --json runs/k1/diff.json
python -I parity.py plant runs/v1 --out runs/v1-planted     # self-test 6: five planted changes, exactly five found
```

`--vanilla-jar` is needed for every run: a Paperclip jar gets the verified Mojang jar in its `cache/` (its sha256 must
match Paperclip's `download-context`), so nothing is downloaded. Self-test switches of `run`: `--order whole-rectangle`
(the order check must reject it), `--without-structures` (structures off in the copied world's
`world_gen_settings.dat`), `--overlay <folder>` (copied over the server folder, e.g. `overlays/faster-random-worldgen`).

`compare` takes its locations, rectangles and bootstrap from the runs' `run.json` and refuses (exit 2) runs that are the
same folder, hold byte-identical region files, started from different bootstraps (sha256, checked against the bootstrap
on disk), swept different rectangles or differ in variant. `--allow-identical-runs` and `--allow-variant-mismatch` are
for deliberate self-tests; `--profile` and `--locations` are only a check against what the runs recorded. `bootstrap`
refuses an existing folder, `run` refuses an `--overlay` that contains `server.properties`, and the properties on disk
are checked just before every start. `--step-settle` and `--end-settle` set the two pauses (default 0.25 s and 2 s) to
show that a result does not depend on them. Java: `--java`, else `$JAVA`, else this PC's Adoptium 25, else `java` on the
PATH; symlinks are followed to find `jfr`, `--jfr` overrides it, and a missing one stops the run before any server starts.

Exit codes: 0 identical, 1 different, 2 no verdict (a server failed, the order check failed, a chunk the sweep must
make full is missing, or the runs may not be compared), 3 identical except the post-processing footprint of Mojang's
promotion race in some chunks (never plain "identical").

## Reading a comparison

- `order A/B`: whether each run proved the canonical order, how many generation events, which statuses the jar
  records (Mojang records all nine; the Paper family runs light and full outside `ChunkStep`, so seven) and on which
  threads.
- `compared (A / B)`: what was looked at; a comparison over nothing cannot pass unnoticed.
- `PARTLY COMPARED`: a chunk Mojang's jar left unprocessed (its promotion race) while the other jar post-processed it.
  It is compared except the positions its marks name and their neighbours; differences there are listed as `MASKED`.
  Only Mojang's side can be excused: a jar under test that skips post-processing shows up as differences.
- `unexplained`: the chunks differ but no comparison names it, or counts differ although no difference was found.
- The header names both jars (path, sha256, version line), java, variant, pauses and the bootstrap hash.
- Each differing chunk says whether the jar computed some of its statuses itself or only loaded it from the bootstrap
  and saved it again.
- `not compared:` lists every field and normalisation left out, with the reason.

## Tests

```bash
python -I test_sweep.py && python -I test_jfr_order.py && python -I test_mutate.py && python -I test_review_fixes.py
python -I ../vanilla-diff/test_compare_worlds.py
```

## Not built yet

Mode B (features-off datapack, `terrain_pack.py`), `fetch_paper.py`, the `suite` command, `climate_probe`, and the
nether, end and standard-profile locations. See DESIGN.md, "Implementation status".
