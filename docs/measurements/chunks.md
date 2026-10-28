# 🧭 Chunk generation and loading

[← Overview](../MEASUREMENTS.md)

How fast players are kept over generated land and how saved chunks are read: directional loading, chunk workers, the generation cap, the palette decoder and the copies of unreadable chunks, plus client-paced sending, which was dropped.

**Contents**

- [Elytra test: new chunks ahead of fast players](#elytra-test-new-chunks-ahead-of-fast-players)
- [Default number of chunk workers](#default-number-of-chunk-workers)
- [Concurrent generations per player](#concurrent-generations-per-player)
- [Loading chunks from disk](#loading-chunks-from-disk)
- [Copies of unreadable chunks](#copies-of-unreadable-chunks)
- [Client-paced chunk sending](#client-paced-chunk-sending)

---

## Elytra test: new chunks ahead of fast players

> [!NOTE]
> 🧪 **Time over void 9 % → 0 % at the same CPU (5 workers, five elytra players, 90 s)** · against the same build without directional loading · 2 runs each, one line ahead of each player, Windows machine pinned to 8 threads · 2026-10-26/27

Chunks ahead of a moving player are queued before chunks behind them (`performance.directional-chunk-loading`, on by default, `look-ahead-ticks: 60`). It only reorders the queue: which chunks get loaded does not change. It is not a guarantee: on a 4-core, 8-thread Linux host the same kind of flight at 1.7 blocks/tick spent about half of the time over the void, because chunk generation there manages about 86 chunks/s against the roughly 225 needed. Against plain Leaf, together with the worker count and the generation cap: [final round](vs-leaf.md#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf).

| Variant | Lead avg / min | Time in the void | Tick time avg / p95 | CPU |
| --- | ---: | ---: | ---: | ---: |
| Leaf, 2 workers | 0.2 / 0 | 92 % | 19.8 / 95 | 394 s |
| Kindling, 2 workers | 0.2 / 0 | 94 % | 24.5 / 113 | 413 s |
| view distance 8 instead of 10 | 0.3 / 0 | 89 % | 8.5 / 36 | 408 s |
| 5 workers | 3.5 / 0 | 9 % | 18.5 / 36 | 831 s |
| 7 workers | 5.1 / 0 | 1.5 % | 34.9 / 79 | 986 s |
| **5 workers + directional loading** | 4.4 / **1** | **0 %** | 19.2 / 35 | 828 s |
| **pregenerated world**, 2 workers | **8.1 / 4** | **0 %** | 24.4 / **33** | 503 s |

<details>
<summary><b>Method</b></summary>

Five players fly 90 s straight outward in five directions at elytra speed (1.7 blocks/tick, about 34 blocks/s), on a stand-in for an 8 vCPU VPS (`kindling-bench/elytra.sh`: server pinned to 8 logical cores, `-XX:ActiveProcessorCount=8`; a typical VPS core is about 1.4 times slower than one here). The measure is the **lead**: how many chunks in flight direction, gap-free from the player, have already reached the client. A lead of 0 means the player flies into the void.

At view distance 10 one elytra needs about 45 new chunks per second, five need about 225. Paper uses a quarter of the cores for chunk generation (`chunk-system.worker-threads: -1` gives **2 threads** on 8 cores, log line "Paper is using 2 worker threads"), which manages about 75 chunks/s.

</details>

<details>
<summary><b>All numbers</b></summary>

Conclusions:

- The bottleneck is chunk generation, not the main thread. A smaller view distance relieves the ticks but does not stop the void.
- A **pregenerated world** solves it completely: loading from disk is many times cheaper than generating. The five flight corridors (about 3.7 km each) took 357 MB; a square with a 5 000 block radius comes to about 5 GB, 10 000 blocks to about 21 GB. No world border is needed: beyond the pregenerated area, 5 workers and directional loading keep fliers out of the void as well.
- Where generation does happen (the edge, the Nether, the End), the server needs more workers than Paper's default. 5 keep the main thread calm; 7 buy lead with noticeably worse ticks, because workers and main thread share the cores.
- **Directional loading** only reorders. It weights a chunk's distance by `1 − w·cos θ` (θ the angle to the flight direction, w grows with speed, at most 0.75), so chunks ahead come earlier while every direction keeps its order by distance. With 5 workers it turns 9 % void into 0 % at the same CPU; with 2 workers there is not enough capacity for any order to help. A first draft that shifted the centre forward was worse (55 % void), because it put chunks right in front of the player behind farther ones.

</details>

<details>
<summary><b>Caveats and open points</b></summary>

**What "0 %" means.** Measured on the 12-thread Windows machine, pinned to 8 threads, two runs per variant, 90 s of flight, and the lead followed **one line straight ahead** of each player; sides and turns were not checked. The flight starts with a full cushion of chunks from the warm-up: about 45 new chunks per second are needed per player against about 34 offered, so a longer flight would eat into it. The row combines 5 workers with directional loading, and the 0 % holds with that machine's generation capacity.

**On the bench host the same load is not at 0 %.** Elytra flights (5 players at 1.7 blocks/tick) with the current defaults on the 4-core, 8-thread bench host under Linux spent 52.3 ±4.7 % (worker priority off) and 49.1 ±5.3 % (on) of the time in the void by the old line-ahead measure (3 runs each, [worker priority](host.md#worker-priority-and-the-main-threads-wait-for-a-cpu)), with a lead of 1.4 and about 86 new chunks per second against the roughly 225 five elytras need. Chunk generation capacity of that machine is the limit, which is what [the next sections](#concurrent-generations-per-player) say about it. A pregenerated world is the answer there (2 workers, 0 % void in the table above). On this host the cap on concurrent generations took eight fliers at 1 block/tick from 31.5 % to 1.2 % void; at 1.7 blocks/tick the same host is out of capacity.

</details>

## Default number of chunk workers

> [!NOTE]
> 🧪 **Time over void 24 % → 0 % with eight fliers on 12 cores (5 workers against Paper's 3)** · against Paper's default number of workers · 2 runs each, Windows machine · 2026-10-26/27

Paper starts about a quarter of the cores as chunk workers (`chunk-system.worker-threads: -1`). [The elytra test](#elytra-test-new-chunks-ahead-of-fast-players) shows that 2 cannot keep up with even one elytra. Kindling's default is five eighths of the cores, at most five, and never fewer than Paper's quarter. A value in `paper-global.yml` still wins.

Why at most five: the mixed load (eight players flying outward, 12 logical cores, not pinned), two runs each:

| Workers | Time in the void | Tick time flying avg / p95 | Tick time standing avg / p95 | New chunks / s |
| --- | ---: | ---: | ---: | ---: |
| 3 (Paper's default here) | 24 % | 13.4 / 29.5 | 29.8 / 39.9 | 125 |
| **5 (Kindling's default)** | **0 %** | 30.6 / 43.7 | **26.6 / 31.2** | 203 |
| 7 (five eighths of 12) | 0 % | 40.1 / 64.9 | 26.9 / 32.3 | 244 |

<details>
<summary><b>All numbers</b></summary>

| Cores | 4 | 8 | 12 | 16 | 24 | 32 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Paper | 1 | 2 | 3 | 4 | 6 | 8 |
| **Kindling** | **2** | **5** | **5** | **5** | **6** | **8** |

With 3 workers the ticks look cheap only because a quarter of the time there is nothing to tick: the players fly into the void. Five workers keep them out of it; the main thread then does the vanilla work of the chunks they really reach (loading, entities, block ticks), within the 50 ms of a tick. Seven only build more lead than anyone needs and push the 95th percentile over 50 ms, because workers and main thread share the cores.

</details>

## Concurrent generations per player

> [!NOTE]
> 🧪 **Time over void 31.5 % → 1.2 % with eight fliers (cap of 32 against Paper's automatic limit of 88)** · against Paper's automatic limit · 2 runs each, bench host · by 2026-10-27

Kindling caps the automatic value at 32; an explicit value in `paper-global.yml` still wins. Which chunks are generated, and how, does not change.

Flight only (eight players, no mobs), two runs each:

| Variant | Time in the void | Lead avg | Tick time flight avg / p95 | Tick time standing | New chunks / s |
| --- | ---: | ---: | ---: | ---: | ---: |
| Paper's automatic limit (88) | 31.5 % | 1.6 | 14.9 / 40.2 | 38.7 | 112 |
| limit 32 | **1.2 %** | 3.0 | 24.0 / 49.6 | 35.7 | 103 |
| limit 16 | 3.5 % | 3.1 | 24.8 / 51.2 | 35.1 | 101 |
| 7 workers, automatic limit | 32.2 % | 1.8 | 31.4 / 63.6 | 50.4 | 116 |
| 7 workers, limit 16 | 0.9 % | 4.2 | 44.0 / 94.6 | 51.8 | 118 |

<details>
<summary><b>All numbers</b></summary>

On the 8-thread bench host (Intel i7-7700, Linux, the machine of [Leaf's options one by one](vs-leaf.md#leafs-options-one-by-one)) eight players flying outward still spent 30–40 % of the time over the void with Kindling's five workers, while the same load on 12 threads stayed at 0 %. A profile showed the five workers busy with vanilla world generation, the machine only 70–80 % busy, and about 110 new chunks per second against roughly 220 that eight fliers need at view distance 10.

More workers do not help: they share the four cores with the main thread. What helps is the order. Paper lets every player have a fifth of the chunks of the view distance in generation at once (`chunk-loading-advanced.player-max-concurrent-chunk-generates: 0`, 88 at view distance 10). With that many already handed to the workers, [directional loading](#elytra-test-new-chunks-ahead-of-fast-players) has nothing left to re-sort: the workers spend half their time on chunks beside and behind the flight that the player has left before they are done. A shorter queue keeps re-sorting toward the flight direction.

Mixed load (flight, then 1 200 mobs, then idle), two runs each; the last row is the Kindling jar with the cap and no configuration overlay:

| Variant | Time in the void | Tick time flight avg / p95 | Tick time standing avg / p95 | Peak RSS | CPU time |
| --- | ---: | ---: | ---: | ---: | ---: |
| Paper's automatic limit | 38.5 % | 14.7 / 40.5 | 37.7 / 71.8 | 2 783 MB | 1 322 s |
| limit 32 set in `paper-global.yml` | 1.4 % | 23.1 / 50.0 | 36.5 / 65.8 | 2 784 MB | 1 339 s |
| **Kindling default (cap 32)** | **0.9 %** | 23.6 / 48.2 | **35.9 / 67.5** | 2 766 MB | 1 327 s |

The flight ticks get more expensive because the players now fly over chunks that exist: the main thread loads, ticks and sends what they reach instead of waiting for it. They stay below 50 ms at the 95th percentile. Standing ticks, memory and CPU time do not get worse.

</details>

## Loading chunks from disk

> [!NOTE]
> 🛡️ **Same result as the codec (no difference in thousands of sections); parsing chunks fell from 13.9 % to 10.0 % of the worker time in the pregeneration profile** · against the codec path · flying through a generated world shows no measurable difference · undated

Block and biome palettes of saved chunks are read straight from NBT instead of through Mojang's codec library. The result is the same: anything unusual still goes through the codec, and `-Dkindling.verifyPaletteDecoder=true` checks it.

- **Same result:** with `-Dkindling.verifyPaletteDecoder=true` every section is decoded both ways and compared block by block; flying five players through a pregenerated world (thousands of sections) found no difference.
- **Cost:** in the pregeneration profile, parsing chunks fell from 13.9 % to 10.0 % of the worker time; the codec frames vanished and the decoder itself takes about 1 %.
- **Flying through an already generated world** (five players at elytra speed, 8 cores) showed no measurable difference (tick time 37.2 vs 36.6 ms, CPU 660 vs 654 s, lead and void identical): there the workers are not the bottleneck.

<details>
<summary><b>Method</b></summary>

Since 26.x, Minecraft decodes every palette entry of a saved chunk section (a block name, or a block with its properties, or a biome name) through DataFixerUpper codecs: an identifier is parsed and validated and several `DataResult`s are built per entry. Kindling reads the palettes straight from NBT (`net.kindlingmc.chunk.SectionPaletteDecoder`), resolves names through a cache and hands the result to the same `PalettedContainer.unpack` the codec uses. Whatever the codec would treat as an error (unknown or invalid names, unexpected tag types, a broken container) is left to the codec, so such chunks are logged and repaired exactly as in vanilla.

</details>

<details>
<summary><b>Caveats and open points</b></summary>

> [!NOTE]
> Writing the palettes without the codecs as well (branch `exp/palette-encoder`, byte for byte the codec's output) was measured and not taken: saving is only about 1.3 % of the CPU of a pregeneration run, the encoder cut that to 1.1 %, and chunks per run differed less than two runs of the same build did (28 421 / 28 410 against 26 368 / 28 396).

</details>

## Copies of unreadable chunks

> [!NOTE]
> 🛡️ **Damaged chunk data is copied to `<world>/kindling-quarantine/` before the usual handling continues; nothing else changes** · against the build before the patch · functional check, one run per case · undated

Patch `0328` copies the stored bytes of a chunk that cannot be decompressed, read as NBT, or upgraded and parsed to `<world>/kindling-quarantine/` before the existing handling continues.

Vanilla and Paper log the error and generate the chunk anew, and the next save overwrites what was stored. The copy is `<world>/kindling-quarantine/<dimension>/r.<x>.<z>/c.<x>.<z>-<n>.bin` next to the exception, and one `WARN` line names the path.

| Case | Result |
| --- | --- |
| Six bytes of the compressed stream overwritten | One `WARN` ("could not be read (java.util.zip.ZipException: invalid code lengths set); kept a copy of its stored bytes at ...c.20.20-1.bin") next to the usual Moonrise errors; the `.bin` (3 990 bytes) is identical to the stored record, a `.txt` holds the stack trace; the chunk was regenerated and saved as before. After a restart with the same damage put back, the copy is `-2`. The previous build logged the same Moonrise errors and kept nothing |
| Valid NBT whose `block_states` has no palette | Moonrise's "Failed to parse chunk data ... chunk data will be lost", then the same `WARN` and a `.bin` (3 461 bytes, equal to the stored record); regenerated; `-2` after a restart. The previous build: only the Moonrise error |
| An unknown block in the palette | Both builds log "Unknown registry key ... using default" for every section and load the chunk with default blocks. No exception, so **no copy**: this is not a trigger on 26.x |
| A chunk saved by a newer version (data version +100 000) | Paper stops the server (`System.exit(1)`) in `SerializableChunkData.parse` before the quarantine runs, on both builds; the stored bytes stay untouched. With `-DPaper.ignoreWorldDataVersion=true` the chunk loads without an error. So **a newer Minecraft version's chunk is not covered**, and not needed |
| An untouched chunk | No `WARN`, no error, no files. Start and stop times are the same as on the previous build |

<details>
<summary><b>Method</b></summary>

Functional check on the bench host with a copy of a small generated world, one run per case, the build with the patch against the one before it:

</details>

<details>
<summary><b>All numbers</b></summary>

The final regression of that build passed: the bases check (15 counters), the sleeping pairs (default and tuned rates) and the villager pair. After a chunk saved by a newer version Paper's exit hung on the chunk system's halt on both builds, so it is not a regression of this patch.

</details>

<details>
<summary><b>Caveats and open points</b></summary>

- Each chunk is copied once per run, and nothing is copied once the folder holds 256 MB.
- Only chunk data (not entities or POI) in the Anvil format is copied, and only when the read or parse throws an exception; chunks the region file reports as missing, for example with a truncated header, are not copied.
- A chunk saved by a newer Minecraft version stops the server before this runs (Paper's own guard), and a block that does not exist loads as the default block without an error, so neither is copied.

</details>

## Client-paced chunk sending

> [!CAUTION]
> ❌ **Dropped: a fast client gets its view no sooner (join 5 169 → 5 875 ms), a slow client joins about twice as slowly (6 325 → 12 881 ms)** · against the setting off · 3 runs each, bench host, pregenerated world · 2026-10-28

The setting was taken out again after this measurement, because it gave no gain ([ROADMAP](../ROADMAP.md#decided-against-while-building)); the results stay here as the reason.

`performance.client-paced-chunk-sending` sends the chunks a player gets in one tick as one vanilla batch, and the number per tick follows the client's answers (Paper's fixed rate still applies on top unless it is set to -1).

Pregenerated world (273 MB), where chunks load from disk and the sending shows:

| Clients | Variant | Join until the full view | Chunk packets in the flight | Batches | Flight tick time avg / p95 |
| --- | --- | ---: | ---: | ---: | ---: |
| Fast (32) | Off | 5 169 ±139 ms | 17 603 | 0 | 69.1 / 131 |
| | On | 5 875 ±1 106 ms (p = 0.41) | 17 550 | 3 680 | 68.2 / 128 |
| | On, send rate -1 | 5 406 ±677 ms (p = 0.63) | 18 018 | 1 830 | 69.7 / 135 |
| | On, send and load rate caps removed | 6 894 ±567 ms (**p = 0.032 \***, slower) | 18 051 | 1 813 | 74.5 / 151 (off with the same caps removed: 74.3 / 150) |
| Slow (2) | Off | 6 325 ±368 ms | 17 248 | 0 | 69.3 / 130 |
| | On | **12 881 ±1 144 ms** (p = 0.006 \*) | 16 599 (p = 0.060) | 8 563 | 71.1 / 138 (p = 0.56 / 0.30) |
| | On, send rate -1 | 13 152 ±675 ms (p = 0.001 \*) | 16 800 (p = 0.009 \*) | 8 660 | 72.2 / 141 |
| Never answers (0) | Off | 6 111 ±946 ms | 17 726 | 0 | 74.6 / 144 |
| | On | **11 016 ±850 ms** (p = 0.003 \*) | 17 943 | 0 | 75.7 / 149 |

<details>
<summary><b>Method</b></summary>

Bench host, `chunk-acks` makes the bench's fake players answer batches like a client that takes that many chunks per tick: 32 is a fast client, 2 a slow one, 0 one that never answers. 8 players, 3 GB heap, tail shortened to 30 s standing and 10 s idle; 3 runs per variant, interleaved.

The default mixed scenario generates its world fresh, so generation (join about 43 s) is the bottleneck and off against on is identical there (fast clients: join 42.7 ±0.5 against 44.0 ±0.5 s, p = 0.055, chunk packets and lead equal). The meaningful test ran on a world pregenerated from a previous run (273 MB), where chunks load from disk and the sending shows:

</details>

<details>
<summary><b>All numbers</b></summary>

Result: **on gives a fast client its view no sooner**; the join is bounded by loading chunks on the server, not by Paper's 75 chunks per second (removing the caps in the off variant changed the join not at all, 5 255 ±501 ms, and raised its tick times as far as the on variant: that is the caps, not the pacing). A slow client is throttled as designed (4 % fewer packets in the flight, many more batches), but its own join takes twice as long, and the main thread gets no measurable relief. A client that never answers (an old, modded or fake client) joins about 5 s later than off, more than the 3 s the code allows before the fixed rate takes over; the flight afterwards is unchanged. Lead, holes (0 %) and the chunk count at the end of the warm-up (3 784 packets in each of more than 120 runs) show no lost chunks; the bench has no counter for duplicates. Compared with the previous build the off variant is not slower (flight tick time 75.4 ±0.9 against 77.1 ±3.6 ms, p = 0.52). The setting stays off.

</details>

<details>
<summary><b>Caveats and open points</b></summary>

A real client, ViaVersion clients and packet-watching plugins were not tried.

</details>

---

[← Overview](../MEASUREMENTS.md)
