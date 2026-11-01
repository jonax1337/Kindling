# 🔭 Preview chunks

[← Overview](../MEASUREMENTS.md)

Far view for vanilla clients: beyond the view distance the server sends previews of chunks, computed from the world seed or read from the save, and a real chunk replaces its preview when the player comes close. What it costs, what it saves, how faithful the previews are, and what was tried and dropped.

**Contents**

- [Previews to 32 chunks](#previews-to-32-chunks)
- [Previews yield to new terrain](#previews-yield-to-new-terrain)
- [One unhurried worker for standing players](#one-unhurried-worker-for-standing-players)
- [Travel view distance](#travel-view-distance)
- [View distance 8 with previews](#view-distance-8-with-previews)
- [How faithful seed previews are](#how-faithful-seed-previews-are)
- [Surface features in previews](#surface-features-in-previews)
- [Tree crowns from neighbouring previews](#tree-crowns-from-neighbouring-previews)
- [Cost of one preview](#cost-of-one-preview)

---

## Previews to 32 chunks

> [!NOTE]
> 🏆 **Vanilla clients see 32 chunks at the tick time of a view distance of 10** · steady tick time 39.4 ms with previews to 32 against 39.9 ms at a plain view distance of 10 (not different) and 82.4 ms at a real view distance of 32; CPU time −52 % and heap −69 % against the real 32, +19 % CPU against the plain 10 · `exp/preview-chunks` at `ef871b8b`, bench host, 3 runs each · 2026-11-01

`network.preview-chunks` (off by default). The client is told a chunk radius of `radius` (32); the server keeps loading, ticking and sending real chunks only within its view distance and fills the ring beyond with previews:

- **Seed previews** are computed from the world seed: vanilla's noise and surface rules over a band around the surface, stone below; with `trees`, vanilla's own surface features (lakes, rocks, icebergs, ice spikes, desert wells, trees, grass and flowers); snow and ice as vanilla freezes them; sky light estimated from above. Nothing is generated as a real chunk or saved.
- **Saved previews** (`saved-chunks`, on): a chunk that is already saved is previewed as saved (its trees, structures and builds; no chests, signs, banners or item frames), read at the lowest I/O priority through the chunk system's own read path.
- **Real chunks a player leaves** stay on the client as their own preview while within `radius` (`keep-real-chunks`).
- **Gaps** (`fill-gaps`): chunks within the view distance that the server has not loaded yet get a preview until the real one arrives, while the player moves, except the two chunks around the player.

| Arm (view distance, previews) | Steady tick time | Flight tick time avg / p95 | Join: full view | CPU time (run) | Heap after GC |
| --- | ---: | ---: | ---: | ---: | ---: |
| A: 10, off | 39.9 ms | 35.2 / 61.4 ms | 34.1 s | 1 244 s | 1 119 MB |
| **B3: 10, previews to 32** | **39.4 ms** | **31.2 / 50.6 ms** | **33.2 s** | **1 481 s** | **1 146 MB** |
| C: 32, off | 82.4 ms | 60.8 ms (avg) | 87.2 s | 3 086 s | 3 724 MB |

<details>
<summary><b>Method</b></summary>

`kindling-bench`, default scenario: 8 fake players, warm-up 30 s, flight 90 s at 1 block per tick (an elytra in level flight), settle 15 s, steady 90 s, idle 60 s; the players request a render distance of 32 (`client-view-distance=32`), heap 6 GB, `-XX:+UseCompactObjectHeaders`, simulation distance 8. Arms A and C ran with the same jar and previews off, B3 with the overlay `preview-chunks-on`. Bench host (i7-7700, 4 cores / 8 threads, WSL), runs in sequence. Significance as defined in [MEASUREMENTS](../MEASUREMENTS.md).

</details>

<details>
<summary><b>All numbers</b></summary>

B3 against A: steady tick time 39.9 → 39.4 ms and the real-chunk lead (avg 6.0 → 5.4 chunks, share of samples with a lead of 2 or less 1.4 → 2.2 %) not significantly different; flight tick time p95 61.4 → 50.6 ms *; CPU time of the run 1 244 → 1 481 s (+19 %) *, of the steady phase 91 → 201 s *, of the flight +7 % *; heap after GC +27 MB *.

B3 against C: CPU time 3 086 → 1 481 s (−52 %) *, heap 3 724 → 1 146 MB (−69 %) *, GC pauses 16.0 → 3.1 s *, steady tick time 82.4 → 39.4 ms *, flight tick time 60.8 → 31.2 ms *, join 87 → 33 s *.

The first version without the two changes below (B, build `3748e9f9`) cost more: against A +43 % CPU, steady tick time 39.9 → 46.7 ms, lead 6.0 → 3.7 chunks (31 % of samples at 2 or less); against C still −42 % CPU.

</details>

<details>
<summary><b>Caveats</b></summary>

- The previews are not the real chunks; see [how faithful seed previews are](#how-faithful-seed-previews-are). Structures (villages, temples) are not in seed previews.
- The cost is CPU time on background threads (`threads`, 2, plus one shared by standing players); a host without spare cores pays it in tick time.
- The fake players discard packets, so encoding and the bandwidth of previews are not in the numbers (a seed preview's packet is about 6 times smaller than a saved chunk's, below).
- Overworld-like worlds only.

</details>

---

## Previews yield to new terrain

> [!NOTE]
> 🏆 **Run CPU time −7 %, real-chunk lead 3.7 → 4.6 chunks** · while new terrain waits for the chunk workers, seed previews take turns on one worker · against the build before (B), 3 runs each · `27fcea59` · 2026-11-01

While the chunk workers have a backlog (more than 8 generation steps waiting on average), only one seed preview at a time is computed; saved and cached previews are not held back.

<details>
<summary><b>All numbers</b></summary>

B2 against B: lead avg 3.7 → 4.6 *, samples with a lead of 2 or less 31 → 10.6 % *, new chunks per second 102 → 116 *, flight CPU −9 % *, run CPU 1 784 → 1 664 s *, join full view 44.5 → 38.3 s *; previews sent in flight 17.9 k → 8.6 k; steady phase unchanged (306 s CPU). The preview time per seed preview includes the wait for the turn (25 ms on average).

</details>

---

## One unhurried worker for standing players

> [!NOTE]
> 🏆 **Steady tick time 50.2 → 39.4 ms, steady CPU −34 %** · players standing still (also right after joining or a teleport) share one background thread for their previews · against the build before (B2), 3 runs each · `ef871b8b` · 2026-11-01

Moving players keep the `threads` workers; standing players queue on one shared thread, at most 32 previews each. Their rings fill more slowly, the more of them stand the slower.

<details>
<summary><b>All numbers</b></summary>

B3 against B2: steady tick time 50.2 → 39.4 ms *, steady CPU 306 → 201 s *, run CPU 1 664 → 1 481 s *, join full view 38.3 → 33.2 s *, warm-up 45.0 → 39.8 s *, flight tick time p95 58.1 → 50.6 ms *, flight CPU 635 → 622 s *, samples with a lead of 2 or less 10.6 → 2.2 % *, GC pauses 3 587 → 3 139 ms *; lead avg 4.6 → 5.4 (p = 0.082) and new chunks per second 116 → 132.5 (p = 0.065) not marked; previews sent in flight unchanged (8 574 against 8 354). At the end of the run 4 to 12 % fewer seed previews had been computed after the flight than with B2: the work is spread out, not skipped.

</details>

---

## Travel view distance

> [!NOTE]
> ⏸️ **Flight tick time 31.2 → 24.5 ms (−21 %), CPU time not lower** · `travel-view-distance: 5` against the same build without it (B3), view distance 10, 3 runs each · `e25d7f29` · 2026-11-01

`network.preview-chunks.travel-view-distance` (0, off) and `travel-speed` (15 blocks a second): a player moving faster than that for two seconds in a row gets at most that view and simulation distance until it has been slower for five seconds; previews show the rest. It changes what the server does around such a player (chunks between the lowered and the usual distance are not loaded, do not tick and spawn nothing, and plugins see the lowered distance), so it stays off by default.

<details>
<summary><b>All numbers</b></summary>

B4 against B3: flight tick time avg 31.2 → 24.5 ms *, p95 50.6 → 44.5 ms *, main-thread work in flight 53.4 → 42.0 s *, flight CPU 622 → 604 s (−3 %) *, flight allocation −12 % *; run CPU 1 481 → 1 545 s (p = 0.070), new chunks per second 132.5 → 115.6 (p = 0.071) and the steady phase not marked; real-chunk lead in the weakest direction 5.0 → 3.7 chunks *; join full view 33.2 → 38.2 s * (the players stand while joining, where the setting does not act; not explained). Against A (view distance 10, previews off) the flight ticks in 24.5 instead of 35.2 ms *.

The chunk workers are saturated during the flight in every arm, so the CPU time follows the worker count, not the view distance: the setting relieves the main thread, not the CPU.

</details>

---

## View distance 8 with previews

> [!NOTE]
> ❌ **No CPU saving, more flights at the edge of the real chunks** · view distance 8 with previews to 32 against view distance 10 with previews (B3), 3 runs each · 2026-11-01

Join full view 33.2 → 30.0 s * and heap after GC 1 146 → 995 MB *, but samples with a real-chunk lead of 2 or less 2.2 → 13.9 % * and the CPU time unchanged (1 481 → 1 473 s). Not recommended.

---

## How faithful seed previews are

> [!NOTE]
> 📊 **The top block matches in 87.9 % of the columns, the surface height in 88.9 %** · seed previews against the real, fully generated chunk · 400 random chunks (102 400 columns), bench host, 1 run (the comparison is deterministic: two runs of the same build differed by 0.02 points) · 2026-11-01

The bench scenario `FIDELITY` (`--scenario "type=fidelity"`, [README](../../kindling-bench/README.md)) loads 400 chunks drawn with a fixed seed from a square of ±1 500 chunks to full status, builds each one's seed preview the way the server sends it and compares the two column by column.

| Top block (the block at `WORLD_SURFACE`) | All columns | Interior (3+ blocks from the chunk edge) | Edge |
| --- | ---: | ---: | ---: |
| equal | 87.9 % | 89.8 % | 86.6 % |
| tree only in the real chunk | 4.3 % | 3.1 % | 5.1 % |
| tree only in the preview | 3.0 % | 3.3 % | 2.8 % |
| plant (grass, flowers) | 3.0 % | 2.3 % | 3.5 % |
| structure or other | 1.5 % | 1.3 % | 1.7 % |
| snow or ice | 0.2 % | 0.2 % | 0.2 % |
| water | 0.1 % | 0.1 % | 0.1 % |

Surface height (`MOTION_BLOCKING`): equal 88.9 %, off by 1–2 blocks 2.2 %, by 3–8 6.3 %, by more 2.6 % (mostly tree crowns). Sky light: the block above the surface equal in 95.2 % of the columns; in the band from 8 below to 4 above it, 3.1 % of the blocks differ by 2 or more.

<details>
<summary><b>Caveats</b></summary>

- Trees are the main difference. About a third of the missing trees are crowns that reach over the chunk border from a neighbour (5.1 % at the edge against 3.1 % inside); inside the chunk trees stand in other places about as often as they are missing, because vanilla decorates a chunk onto what earlier neighbours already placed there, which a preview built alone cannot know.
- The light readiness check did not pass for any chunk, so every chunk was compared after the 600-tick limit (30 s after loading); the light is very likely final, but that is not confirmed.
- How far two real generations of the same chunk differ in another generation order (the floor a preview can reach) is not measured yet: the first attempt ran out of memory in the twin world.

</details>

---

## Surface features in previews

> [!NOTE]
> 🏆 **Wrong snow and ice 0.47 → 0.18 % of the columns, top block equal 87.6 → 87.9 %** · lakes, rocks, icebergs, ice spikes and desert wells in seed previews, against previews with vegetation only · same 400 chunks, 1 run each · 2026-11-01

Seed previews run vanilla's `LAKES`, `LOCAL_MODIFICATIONS` and `SURFACE_STRUCTURES` steps before the vegetation, with vanilla's seeds per feature; underground features of these steps (lava lakes, geodes) run too and are hidden in the stone below the preview's band. Surface height equal 88.7 → 88.9 %. The extra cost per preview is not measured yet.

---

## Tree crowns from neighbouring previews

> [!NOTE]
> ❌ **Worse: top block equal 87.6 → 86.6 %** · placing what neighbouring previews' features wrote into the chunk before its own features, against previews without it · same 400 chunks, neighbours nearer to the origin built first · 2026-11-01

The idea was to give each preview the tree crowns and grass its already built neighbours spill over the border, as vanilla's earlier neighbours do. Missing trees at the edge fell only from 5.1 to 4.9 %, while trees only in the preview inside the chunk rose from 3.3 to 4.2 %, the surface height matched in 86.3 instead of 88.7 % of the columns and the light above the surface in 93.7 instead of 95.2 %: the neighbours' border trees are themselves uncertain, so their spill spreads errors and blocks trees that the real chunk has. Dropped.

---

## Cost of one preview

> [!NOTE]
> 📊 **A seed preview's terrain costs 2.6 to 2.8 times less work than the terrain of a full chunk; its packet is about 6 times smaller than a saved chunk's** · harness without a server, 300 chunks, 3 forks of 30 rounds, bench host · 2026-11-01

`kindling-bench/preview-chunks/` ([README](../../kindling-bench/preview-chunks/README.md)) measures the preview band (vanilla `doFill` and `buildSurface` over a band around the surface, stone below) against vanilla's BIOMES and TERRAIN work over all 384 blocks of height: 17.2 → 6.3 ms per chunk on one thread without biome folding, 10.3 → 3.9 ms with the chunk's own biomes. Inside the band the density is bit-identical to the full chunk (19.1 million points), and of 90.7 million blocks two differ (an overhang above the band). zlib packet median: 4 521 B for a saved full chunk against 761 B for a preview. The surface features and the light come on top; under load a seed preview took 13.6 ms on the preview workers in the flight bench.
