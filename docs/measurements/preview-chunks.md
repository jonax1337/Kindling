# Preview chunks

[← Overview](../MEASUREMENTS.md)

Far view for vanilla clients: beyond the view distance the server sends previews of chunks, computed from the world seed or read from the save, and a real chunk replaces its preview when the player comes close. What it costs, what it saves, how faithful the previews are, and what was tried and dropped. The settings are in [configuration](../reference/configuration.md#networkpreview-chunks).

## Previews to 32 chunks

**Trade-off.** Vanilla clients see 32 chunks at the tick time of a view distance of 10 (steady 39.9 → 39.4 ms, not marked) for 19 % more CPU time. Against a real view distance of 32: CPU time −52 %, heap −69 %, steady tick time 82.4 → 39.4 ms, join 87 → 33 s.
Against the same jar at view distance 10 without previews and at a real view distance of 32, 3 runs each.

The client is told a chunk radius of `radius` (32). The server keeps loading, ticking and sending real chunks only within its view distance and fills the ring beyond with previews:

- **Seed previews** are computed from the world seed: vanilla's noise and surface rules over a band around the surface, stone below. With `trees`, vanilla's own surface features run on them (lakes, rocks, icebergs, ice spikes, desert wells, trees, grass and flowers). Snow and ice are frozen as vanilla freezes them, and the sky light is estimated from above. Nothing is generated as a real chunk or saved.
- **Saved previews** (`saved-chunks`, on): a chunk that is already saved is previewed as saved (its trees, structures and builds; no chests, signs, banners or item frames), read at the lowest I/O priority through the chunk system's own read path.
- **Real chunks a player leaves** stay on the client as their own preview while within `radius` (`keep-real-chunks`).
- **Gaps** (`fill-gaps`): chunks within the view distance that the server has not loaded yet get a preview until the real one arrives, while the player moves, except the two chunks around the player.

<details>
<summary>Method and all numbers</summary>

`kindling-bench`, default scenario: 8 fake players, warm-up 30 s, flight 90 s at 1 block per tick (an elytra in level flight), settle 15 s, steady 90 s, idle 60 s. The players request a render distance of 32 (`client-view-distance=32`), heap 6 GB, `-XX:+UseCompactObjectHeaders`, simulation distance 8. Arms A and C ran with the same jar and previews off, B3 with the overlay `preview-chunks-on`, build `ef871b8b`. Bench host, runs in sequence.

| Arm (view distance, previews) | Steady tick time | Flight tick time avg / p95 | Join: full view | CPU time (run) | Heap after GC |
| --- | ---: | ---: | ---: | ---: | ---: |
| A: 10, off | 39.9 ms | 35.2 / 61.4 ms | 34.1 s | 1 244 s | 1 119 MB |
| **B3: 10, previews to 32** | **39.4 ms** | **31.2 / 50.6 ms** | **33.2 s** | **1 481 s** | **1 146 MB** |
| C: 32, off | 82.4 ms | 60.8 ms (avg) | 87.2 s | 3 086 s | 3 724 MB |

B3 against A: steady tick time 39.9 → 39.4 ms and the real-chunk lead (avg 6.0 → 5.4 chunks, share of samples with a lead of 2 or less 1.4 → 2.2 %) not marked; flight tick time p95 61.4 → 50.6 ms *; CPU time of the run 1 244 → 1 481 s (+19 %) *, of the steady phase 91 → 201 s *, of the flight +7 % *; heap after GC +27 MB *.

B3 against C: CPU time 3 086 → 1 481 s (−52 %) *, heap 3 724 → 1 146 MB (−69 %) *, GC pauses 16.0 → 3.1 s *, steady tick time 82.4 → 39.4 ms *, flight tick time 60.8 → 31.2 ms *, join 87 → 33 s *.

The first version without the two changes below (B, build `3748e9f9`) cost more: against A +43 % CPU, steady tick time 39.9 → 46.7 ms, lead 6.0 → 3.7 chunks (31 % of samples at 2 or less); against C still −42 % CPU.

</details>

<details>
<summary>Caveats</summary>

- The previews are not the real chunks; see [how faithful seed previews are](#how-faithful-seed-previews-are). Structures (villages, temples) are not in seed previews.
- The cost is CPU time on background threads (`threads`, 2, plus one shared by standing players). A host without spare cores pays it in tick time.
- The fake players discard packets, so encoding and the bandwidth of previews are not in the numbers. A seed preview's packet is about 6 times smaller than a saved chunk's ([below](#cost-of-one-preview)).
- Overworld-like worlds only.

</details>

## Previews in the comparison with other forks

**Slower.** CPU time +13 % with 5 elytras, +31 % with 8 players exploring and +23 to +50 % without new terrain; new chunks per second 153 → 133 with the elytras and 168 → 140 exploring; join until the full view 20.1 → 24.5 s and 27.6 → 33.1 s. Tick time in the bases and among villagers the same.
Previews to 32 chunks against the same jar with previews off, 3 runs each, in the [comparison with Paper, Purpur and Leaf](vs-leaf.md#paper-purpur-leaf-and-kindling).

The fake players ask for a render distance of 32, so every player gets its ring of previews, also in the bases and villager halls where nobody moves. That ring costs worker time but no tick time there. While players explore, the previews share the cores with the chunk workers and slow the real chunks a little; the time over void of the elytras rises from 4.3 to 9.5 % (not marked, p = 0.13).

<details>
<summary>Method and all numbers</summary>

The jar and the host of the [comparison](vs-leaf.md#paper-purpur-leaf-and-kindling), with the overlay `preview-chunks-on` against previews off, both at view distance 10, run in the same rounds. `*` = marked by `compare.py`; times in milliseconds unless a unit is given.

| Scenario | Figure | Previews off | Previews to 32 |
| --- | --- | ---: | ---: |
| Elytra | New chunks per second | 153.2 | 132.5 \* |
| | Time over void | 4.3 % | 9.5 % |
| | Flight tick time avg / p95 | 22.0 / 37.0 | 20.5 \* / 37.2 |
| | Join until the full view | 20.1 s | 24.5 s \* |
| | CPU time | 877 s | 990 s \* |
| Mixed | New chunks per second | 167.7 | 139.9 \* |
| | Standing tick time | 34.6 | 37.0 \* |
| | Join until the full view | 27.6 s | 33.1 s \* |
| | CPU time | 1 110 s | 1 449 s \* |
| | Heap after GC | 1 125 MB | 1 153 MB \* |
| Loaded | Flight tick time avg | 25.0 | 26.3 \* |
| | CPU time | 284 s | 390 s \* |
| Bases | Tick time avg / p95 | 11.0 / 17.4 | 10.9 / 15.8 |
| | CPU time | 271 s | 408 s \* |
| | Heap after GC | 465 MB | 496 MB \* |
| Villagers | Tick time avg / p95 | 8.6 / 10.7 | 8.4 / 10.2 |
| | CPU time | 194 s | 239 s \* |
| | Heap after GC | 385 MB | 414 MB \* |

Of the seed previews' worker time, the surface features take 2.0 to 4.2 % per scenario (1.8 to 5.0 % per run; `LAKES`, `LOCAL_MODIFICATIONS`, `SURFACE_STRUCTURES`) and the vegetation 5 to 10 %; the rest is terrain, surface rules and light.

</details>

## Previews yield to new terrain

**Faster.** Run CPU time 1 784 → 1 664 s (−7 %), real-chunk lead 3.7 → 4.6 chunks.
Against the build before (B, `3748e9f9`), 3 runs each; this change is `27fcea59`.

While the chunk workers have a backlog (more than 8 generation steps waiting on average), only one seed preview at a time is computed. Saved and cached previews are not held back.

<details>
<summary>Method and all numbers</summary>

B2 against B: lead avg 3.7 → 4.6 *, samples with a lead of 2 or less 31 → 10.6 % *, new chunks per second 102 → 116 *, flight CPU −9 % *, run CPU 1 784 → 1 664 s *, join full view 44.5 → 38.3 s *; previews sent in flight 17.9 k → 8.6 k; steady phase unchanged (306 s CPU). The preview time per seed preview includes the wait for the turn (25 ms on average).

</details>

## One unhurried worker for standing players

**Faster.** Steady tick time 50.2 → 39.4 ms, steady CPU time 306 → 201 s (−34 %).
Against the build before (B2, `27fcea59`), 3 runs each; this change is `ef871b8b`.

Moving players keep the `threads` workers. Players standing still, also right after joining or a teleport, queue on one shared thread, at most 32 previews each. Their rings fill more slowly, the more of them stand the slower.

<details>
<summary>Method and all numbers</summary>

B3 against B2: steady tick time 50.2 → 39.4 ms *, steady CPU 306 → 201 s *, run CPU 1 664 → 1 481 s *, join full view 38.3 → 33.2 s *, warm-up 45.0 → 39.8 s *, flight tick time p95 58.1 → 50.6 ms *, flight CPU 635 → 622 s *, samples with a lead of 2 or less 10.6 → 2.2 % *, GC pauses 3 587 → 3 139 ms *; lead avg 4.6 → 5.4 (p = 0.082) and new chunks per second 116 → 132.5 (p = 0.065) not marked; previews sent in flight unchanged (8 574 against 8 354). At the end of the run 4 to 12 % fewer seed previews had been computed after the flight than with B2: the work is spread out, not skipped.

</details>

## Travel view distance

**Trade-off**, opt-in. Flight tick time 31.2 → 24.5 ms (−21 %), but the real-chunk lead in the weakest direction 5.0 → 3.7 chunks, and the CPU time is not lower.
`travel-view-distance: 5` against the same build without it (B3), view distance 10, 3 runs each; this change is `e25d7f29`.

A player moving faster than `travel-speed` (15 blocks a second) for two seconds in a row gets at most `travel-view-distance` as its view and simulation distance until it has been slower for five seconds; previews show the rest. It changes what the server does around such a player: chunks between the lowered and the usual distance are not loaded, do not tick and spawn nothing, and plugins see the lowered distance. So it stays off by default.

<details>
<summary>Method and all numbers</summary>

B4 against B3: flight tick time avg 31.2 → 24.5 ms *, p95 50.6 → 44.5 ms *, main-thread work in flight 53.4 → 42.0 s *, flight CPU 622 → 604 s (−3 %) *, flight allocation −12 % *; run CPU 1 481 → 1 545 s (p = 0.070), new chunks per second 132.5 → 115.6 (p = 0.071) and the steady phase not marked; real-chunk lead in the weakest direction 5.0 → 3.7 chunks *; join full view 33.2 → 38.2 s * (the players stand while joining, where the setting does not act; not explained). Against A (view distance 10, previews off) the flight ticks in 24.5 instead of 35.2 ms *.

The chunk workers are saturated during the flight in every arm, so the CPU time follows the worker count, not the view distance: the setting relieves the main thread, not the CPU.

</details>

## View distance 8 with previews

**Dropped.** No CPU saving, and more samples at the edge of the real chunks.
View distance 8 with previews to 32 against view distance 10 with previews (B3), 3 runs each.

Join full view 33.2 → 30.0 s * and heap after GC 1 146 → 995 MB *, but samples with a real-chunk lead of 2 or less 2.2 → 13.9 % * and the CPU time unchanged (1 481 → 1 473 s). Not recommended.

## How faithful seed previews are

**Correct.** The top block matches in 87.8 % of the columns and the surface height in 88.8 %, as often as two real generations of the chunk match each other.
Seed previews against the real, fully generated chunk, 400 random chunks (102 400 columns), 1 run (the comparison is deterministic: two runs of the same build differed by 0.02 points).

The bench scenario `fidelity` ([README](../../kindling-bench/README.md#the-fidelity-scenario)) loads 400 chunks drawn with a fixed seed from a square of ±1 500 chunks, at least 8 chunks apart, to full status. It waits until their light is settled (it was for all 400), builds each one's seed preview the way the server sends it and compares the two column by column. How close two real generations come is in [the next section](#how-far-two-real-generations-differ).

<details>
<summary>Method and all numbers</summary>

| Top block (the block at `WORLD_SURFACE`) | All columns | Interior (3+ blocks from the chunk edge) | Edge |
| --- | ---: | ---: | ---: |
| equal | 87.8 % | 89.7 % | 86.6 % |
| tree only in the real chunk | 4.4 % | 3.1 % | 5.2 % |
| tree only in the preview | 3.0 % | 3.3 % | 2.8 % |
| plant (grass, flowers) | 3.0 % | 2.3 % | 3.4 % |
| structure or other | 1.5 % | 1.3 % | 1.7 % |
| snow or ice | 0.2 % | 0.2 % | 0.2 % |
| water | 0.2 % | 0.2 % | 0.1 % |

Surface height (`MOTION_BLOCKING`): equal 88.8 %, off by 1–2 blocks 2.2 %, by 3–8 6.3 %, by more 2.6 % (mostly tree crowns). Sky light: the block above the surface equal in 95.2 % of the columns; in the band from 8 below to 4 above it, 3.1 % of the blocks differ by 2 or more.

</details>

<details>
<summary>Caveats</summary>

- Trees are the main difference. About a third of the missing trees are crowns that reach over the chunk border from a neighbour (5.2 % at the edge against 3.1 % inside). Inside the chunk trees stand in other places about as often as they are missing, because vanilla decorates a chunk onto what earlier neighbours already placed there, which a preview built alone cannot know.
- Until the bench capped the chunks waiting to be compared at 16, the light readiness check never passed and every chunk was compared after the 600-tick limit. That run gave 87.9 % and the same light figures, so the figures of the two sections after the next one, measured that way, compare with these.

</details>

## How far two real generations differ

**Correct.** Two real generations of the same chunk, decorated in opposite orders, match in 87.8 % of the top blocks, as often as the seed preview matches it (87.8 %). In the same order they match in 99.4 %.
The chunk decorated before all its neighbours (main world) against the same chunk decorated after all of them (a twin world of the same seed), the same 400 chunks, 1 run each.

Vanilla decorates a chunk onto what its neighbours' decoration already placed (the `FEATURES` step needs its neighbours only at `TERRAIN`), so the order in which a server happens to generate chunks changes them. The bench mode `fidelity-against=twin` ([README](../../kindling-bench/README.md#the-fidelity-scenario)) measures how much: it generates every sampled chunk a second time in a twin world, in the opposite order, and compares the two with the same column logic as a preview.

The preview is as close to the real chunk as the real chunk is to itself in the other order: closer in the interior, in height and in light, and further at the edge, where it lacks the crowns its neighbours spill in.

<details>
<summary>Method and all numbers</summary>

| | Preview against real | Real, opposite order | Real, same order (control) |
| --- | ---: | ---: | ---: |
| Top block equal, all columns | 87.8 % | 87.8 % | 99.4 % |
| Top block equal, interior | 89.7 % | 87.4 % | 99.9 % |
| Top block equal, edge | 86.6 % | 88.1 % | 99.1 % |
| Tree only on one side (missing + extra) | 7.4 % | 8.6 % | 0.3 % |
| Surface height equal | 88.8 % | 86.3 % | 99.5 % |
| Sky light above the surface equal | 95.2 % | 93.6 % | 99.8 % |

</details>

<details>
<summary>Caveats</summary>

- The opposite order is the extreme. The result bounds what the order alone changes; it is not the difference between two servers that generate in their usual, mixed order, which is likely smaller.
- The control still differs in 0.6 % of the top blocks, almost all at the edge: only the sampled chunk's own place in the order is fixed, not the order among its neighbours.
- That the interior differs more than the edge in the opposite order is not explained by spill alone. A likely cause is that the placements of a feature in a chunk share one random sequence, so a tree blocked at the border shifts every later tree of that feature in the chunk. Not checked.

</details>

## Surface features in previews

**Correct.** Wrong snow and ice 0.47 → 0.18 % of the columns, top block equal 87.6 → 87.9 %, surface height equal 88.7 → 88.9 %.
Lakes, rocks, icebergs, ice spikes and desert wells in seed previews, against previews with vegetation only, the same 400 chunks, 1 run each.

Seed previews run vanilla's `LAKES`, `LOCAL_MODIFICATIONS` and `SURFACE_STRUCTURES` steps before the vegetation, with vanilla's seeds per feature. Underground features of these steps (lava lakes, geodes) run too and are hidden in the stone below the preview's band. They take 2.0 to 4.2 % of the seed previews' worker time ([measured](#previews-in-the-comparison-with-other-forks)).

## Tree crowns from neighbouring previews

**Dropped.** Top block equal 87.6 → 86.6 %.
Placing what neighbouring previews' features wrote into the chunk before its own features, against previews without it, the same 400 chunks, neighbours nearer to the origin built first.

The idea was to give each preview the tree crowns and grass its already built neighbours spill over the border, as vanilla's earlier neighbours do. Missing trees at the edge fell only from 5.1 to 4.9 %, while trees only in the preview inside the chunk rose from 3.3 to 4.2 %, the surface height matched in 86.3 instead of 88.7 % of the columns and the light above the surface in 93.7 instead of 95.2 %. The neighbours' border trees are themselves uncertain, so their spill spreads errors and blocks trees that the real chunk has.

## Cost of one preview

**Faster.** A seed preview's terrain costs 2.6 to 2.8 times less work than the terrain of a full chunk; its packet is about 6 times smaller than a saved chunk's.
A harness without a server against vanilla's terrain work for the same chunks, 300 chunks, 3 forks of 30 rounds.

`kindling-bench/preview-chunks/` ([README](../../kindling-bench/preview-chunks/README.md)) measures the preview band (vanilla `doFill` and `buildSurface` over a band around the surface, stone below) against vanilla's BIOMES and TERRAIN work over all 384 blocks of height: 17.2 → 6.3 ms per chunk on one thread without biome folding, 10.3 → 3.9 ms with the chunk's own biomes. Inside the band the density is bit-identical to the full chunk (19.1 million points), and of 90.7 million blocks two differ (an overhang above the band). zlib packet median: 4 521 B for a saved full chunk against 761 B for a preview. The surface features and the light come on top; under load a seed preview took 13.6 ms on the preview workers in the flight bench.
