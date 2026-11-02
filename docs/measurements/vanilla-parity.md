# Vanilla parity

[← Overview](../MEASUREMENTS.md)

Where Leaf or Gale changed vanilla behaviour without a switch, Kindling restores it. This page holds the evidence: the unit tests (`KindlingTestSuite`: 554 tests, 0 failures), the parity scenarios, a differential run against Mojang's jar and what the restorations cost. "The restorations build" below is `kindling-deep-1.jar`, the build with all vanilla restorations and the defaults of release 26.3-13. What still differs from vanilla, and which Leaf patch each restoration undoes: [vanilla-differences.md](../reference/vanilla-differences.md).

**Contents**

- [Vanilla defaults of a fresh configuration](#vanilla-defaults-of-a-fresh-configuration)
- [Restorations measured together](#restorations-measured-together)
- [Biome zoom](#biome-zoom)
- [Async pathfinding, when switched on](#async-pathfinding-when-switched-on)
- [Async fixes for pathfinding, tracker and saving](#async-fixes-for-pathfinding-tracker-and-saving)
- [Sleeping hoppers with a move event listener](#sleeping-hoppers-with-a-move-event-listener)
- [Parity scenarios](#parity-scenarios)
- [Vanilla diff against Mojang's jar](#vanilla-diff-against-mojangs-jar)
- [Entity UUIDs from world generation](#entity-uuids-from-world-generation)

---

## Vanilla defaults of a fresh configuration

**Slower.** Ten settings default to vanilla behaviour. Standing tick time among 1 200 mobs 36.6 → 39.7 ms (+8 %); the bases (11.5 against 11.5 ms) and the flight (27.2 against 28.1 ms) show no cost. Against release 26.3-12, whose defaults had async pathfinding and mob spawning on, 3 runs each.

**Correct.** Each value was compared with the vanilla code that reads it.

Existing files keep their values, which is why `/kindling doctor` has a "vanilla parity" finding: it names each setting that runs at a non-vanilla value and the value to set.

| File and key | Release 26.3-12 | Kindling default | Why that is vanilla |
| --- | --- | --- | --- |
| Gale world: `small-optimizations.max-projectile-chunk-loads.per-tick` | 10 | -1 | A negative limit never stops a projectile; ender pearls do not freeze at the edge of unloaded chunks |
| Gale world: `small-optimizations.max-projectile-chunk-loads.per-projectile.max` | 10 | -1 | same |
| Gale world: `small-optimizations.reduced-intervals.check-stuck-in-wall` | 10 | 1 | An interval of 1 checks `isInWall()` every tick |
| Gale world: `small-optimizations.reduced-intervals.villager-item-repickup` | 100 | -1 | -1 keeps vanilla's `setDefaultPickUpDelay()` (10 ticks) |
| Gale world: `gameplay-mechanics.entity-wake-up-duration-ratio-standard-deviation` | 0.2 | 0 | 0 gives Paper's fixed wake-up duration |
| Gale global: `small-optimizations.reduced-intervals.update-entity-line-of-sight` | 4 | 1 | An interval of 1 senses every tick |
| `leaf-global.yml`: `performance.skip-map-item-data-updates-if-map-does-not-have-craftmaprenderer` | `true` | `false` | Maps without the default renderer keep updating their colours |
| `leaf-global.yml`: `performance.optimize-player-movement` | `true` | `false` | `maybeBackOffFromEdge` runs again; the flag also gates a redundant view-distance shortcut, which is off with it |
| `leaf-global.yml`: `async.async-pathfinding.enabled` | `true` | `false` | Paths are computed on the main thread (see [Async pathfinding, when switched on](#async-pathfinding-when-switched-on)) |
| `leaf-global.yml`: `async.async-mob-spawning.enabled` | `true` | `false` | Mob counts are taken on the main thread, never stale |

The async entity tracker, async player data saving and sleeping block entities stay on, with Kindling's fixes. The doctor does not recommend async pathfinding. The figures in [Async fixes for pathfinding, tracker and saving](#async-fixes-for-pathfinding-tracker-and-saving) hold only with the options switched on.

<details>
<summary>Method and all numbers</summary>

The restorations build against release 26.3-12, each with its own defaults, interleaved, 3 runs each, bench host. Mixed load (8 players fly outward for 90 s, then stand among 1 200 mobs); `*` = marked by `compare.py` against the restorations build:

| Figure | Restorations build | 26.3-12 |
| --- | ---: | ---: |
| Flight tick time avg | 27.2 ±0.8 | 28.1 ±1.0 (p = 0.29) |
| Flight tick time p95 | 63.4 ±0.7 | 65.6 ±0.6 \* (p = 0.016) |
| Standing tick time avg | 39.7 ±0.5 | 36.6 ±0.7 \* (p = 0.005) |
| Standing tick time p95 | 64.9 ±3.2 | 63.2 ±2.5 |
| Time in the void | 1.5 ±1.1 % | 1.3 ±1.2 % |
| New chunks per second | 99.0 | 98.5 |
| Join until the full view | 40.7 s | 40.7 s |
| CPU time | 1 433 ±13 s | 1 438 ±13 s |

Survival bases (8 players among 32 plots):

| Figure | Restorations build | 26.3-12 |
| --- | ---: | ---: |
| Tick time avg | 11.5 ±0.1 | 11.5 ±0.3 (p = 0.80) |
| p95 / p99 | 16.2 / 21.9 | 16.6 / 24.1 |
| CPU time | 368 ±7 s | 365 ±5 s |

All 15 counters were identical. The flight p95 is the one marked row of the flight and the bases, out of about 35 compared. Among 1 200 mobs the restorations build ticks 39.7 ms against 36.6 ms for release 26.3-12 (p = 0.005 \*), 40.5 ms in a second series. With the overlay `async-previous-defaults` (async pathfinding and mob spawning on) it ticks 37.5 ms (p = 0.051 against the second series, misses the rule), so the async options are the likely main cause. Which of the ten settings costs what was not separated. With the in-wall and line-of-sight checks every tick, the rest of the restored code costs at most about 1 ms there, which the bench cannot separate from noise. No restoration was measured on its own.

</details>

## Restorations measured together

**Same** in average tick time (bases 11.5 against 11.5 ms, p = 0.80; flight 27.2 against 28.1 ms, p = 0.29); one marked row: flight p95 63.4 against 65.6 ms (one of about 35 compared). Against release 26.3-12, measured together with the other restorations and not one by one, 3 runs each; [the numbers](#vanilla-defaults-of-a-fresh-configuration).

**Correct.** Checked against the vanilla code and by unit tests. The contraptions diff finds no block difference against vanilla ([Vanilla diff against Mojang's jar](#vanilla-diff-against-mojangs-jar)); release 26.3-12 had eight. The smaller restorations have no cost in the bench loads and were not measured on their own.

Hash, goal order and entity order:

- **`Vec3i.hashCode`** is `(y + z * 31) * 31 + x` again instead of Leaf's mixing hash. Every `HashMap` and `HashSet` keyed by a position iterates in vanilla order, which decides redstone dust's neighbour update order. Dense, structured position sets get vanilla's collisions back; that is what Leaf's hash avoided. Test: `VanillaVec3iHashTest`.
- **`GoalSelector.availableGoals`** is Paper's `ObjectLinkedOpenHashSet` again: goals iterate in the order they were added, and `canUse()` calls and their random draws happen in vanilla order. Leaf's cached `isInterruptable()` is not kept, because some goals change it during the tick. Test: `GoalSelectorOrderTest` (6).
- **Entities in a chunk section** keep their insertion order on removal, which decides which item a hopper pulls first. A removal is O(n); no cost shows in the bench loads.

Smaller restorations:

- **Lightning:** freshly loaded chunks got about half the strikes; the thunder chance is drawn per chunk tick again, only while thundering.
- **Zombie villager cure:** an offline starter does not give the reputation discount.
- **`Paper.FailedSpawnAttempts`:** counted and written only when Leaf's `throttle-mob-spawning` or Paper's `failed-attempts-threshold` is on.
- **Brownian walk:** the shuffle uses `ThreadLocalRandom`, so the mob's random stream is untouched. The sequence cannot equal vanilla's, whose source is unseeded.
- **`World#getTileEntityCount`:** Paper's count over the loaded chunks; the Leaf option `optimize-block-entities` does not affect it.
- **Shapeless recipes:** vanilla's stacked-contents matching where an item can fit two ingredients (a datapack recipe `[#logs, oak_log]` rejected an oak and a birch log without it). Test: `SoleMatchTest` (8), the only one of the six with unit tests.

Entity movement, age and game events:

- **Inside-block checks** visit blocks as vanilla does, which decides pressure plate and tripwire order. Leaf's single-axis shortcut is removed, so a falling entity allocates one set per call again (no cost in the bench loads). Test: `InsideBlocksOrderTest` (3).
- **Zero-movement skip** (Gale): the vertical collision flags and the ground state are reset only when `isLocalInstanceAuthoritative()`, so players and vehicles steered by a player keep them.
- **Entity age:** `getTicksLived()` and the saved `Spigot.ticksLived` count only ticked ticks, like Paper, unless Paper's `despawn-time` applies to the entity type.
- **Floating point:** the block speed factor guards, `Entity.distanceTo` (vanilla's float math unless `-DLeaf.enableFMA=true`) and the order in which attribute modifiers are summed match vanilla.
- **Game events:** a listener registered or removed while an event is delivered is deferred as in vanilla (otherwise it throws `ConcurrentModificationException`, which affects plugin handlers that place or break sculk sensors or spawn allays). Test: `GameEventListenerChangesTest` (3).

## Biome zoom

**Correct.** The zoom chooses exactly vanilla's cell. Checked against a copy of vanilla's algorithm over 4 × 10 000 000 random (seed, position) pairs plus the known counterexample; the test fails on Leaf's original `BiomeManager`.

**Same.** The opt-in shortcut `performance.biome-zoom-shortcut` on against off: flight tick time 27.2 → 27.7 ms (p = 0.49), standing 39.7 → 39.9 ms (p = 0.83), no memory gain claimed. Restorations build, mixed load, 3 runs each. It stays off by default.

Leaf's pruned zoom (Carpet-Fixes port) summed the fiddled distance as x + y + z, vanilla as z + y + x; the rounding differed and picked another cell in about 2 of 200 million positions (zoom seed -8055847622726226809 at block -20104644, 265, -14313980: vanilla corner 6, Leaf corner 3, pinned in `BiomeZoomParityTest`). The final distance and the three pruning bounds add up in vanilla's order, so a bound is never larger than the distance vanilla computes and pruning never skips a candidate vanilla would pick. The cost is unchanged.

<details>
<summary>The opt-in shortcut</summary>

When the eight candidate cells lie in one chunk column (`(x & 3) != 3` and `(z & 3) != 3`, 9 of 16 positions) it looks the chunk up once. If the sections holding the two quart rows each store one biome (0-bit index storage; Paper uses a hash palette even there, so the detection reads the storage bits) and both give the same `Holder`, that biome is returned without a fiddle. Otherwise the exact zoom runs and reads the chosen cell from the chunk already found. Sources without the override (lambdas, biome sources, the async spawn-cost sampler) take the normal path. It touches no chunk vanilla would not touch; `ProtoChunk` before `BIOMES` status returns null so vanilla's exception still comes from the normal path. `UniformBiomeCellsTest` (6) runs the detection on real sections. The share of positions it catches is expected to be below the 71 % of neighbourhoods that are uniform in single-valued sections, because only in-chunk neighbourhoods are tried. The mixed load showed no tick time difference (table below; shortcut on against off, restorations build, mixed load, 3 runs each). Its peak RSS rows are marked (2 845 → 2 774 MB), but a shortcut that allocates nothing cannot plausibly lower resident memory and the heap after GC does not move (1 153 → 1 155 MB), so that is read as chance among about 40 rows. A world-generation profile on and off would show the effect on generation, which the mixed load does not exercise much; not run.

| Figure | Off | On | p |
| --- | ---: | ---: | ---: |
| Flight tick time avg / p95 | 27.2 ±0.8 / 63.4 ±0.7 | 27.7 ±0.8 / 64.8 ±3.0 | 0.49 / 0.55 |
| Standing tick time avg | 39.7 ±0.5 | 39.9 ±0.7 | 0.83 |
| Time in the void | 1.5 ±1.1 % | 4.4 ±2.0 % | 0.13 |
| CPU time | 1 433 ±13 s | 1 428 ±1 s | 0.61 |
| Peak RSS | 2 845 ±15 MB | 2 774 ±23 MB \* | 0.018 |

</details>

## Async pathfinding, when switched on

**Correct.** Targets, trimming and completion follow vanilla's `PathNavigation`. This applies only with `async.async-pathfinding.enabled: true` (default `false`). Test: `AsyncPathCompletionTest` (4). A villager parity run on the restorations build: no bed or job site claimed twice.

With the options on, the standing tick time is 37.5 against 40.5 ms ([measured](vs-leaf.md#async-pathfinding-and-mob-spawning-opt-in)).

- `createPath` sets `targetPos`, `reachRange` and the stuck timeout like vanilla for every path it returns, check paths included: at once for a processed path or one to a single position, otherwise when the result arrives (the next `leaf$preparePath`, `recomputePath` or `createPath`).
- Only a path given to `moveTo` is trimmed (a processed one inside `moveTo`, an unprocessed one once when it is processed); paths from `recomputePath` are not.
- Every async path completes: any `Throwable` gives an empty path and is rethrown to the worker's handler, and a path queued after the executor shut down is computed on the calling thread, so `AcquirePoi`, `SetClosestHomeAsWalkTarget` and `NearestBedSensor` always get a result.
- **Still different:** for several target positions the target arrives with the result; a mob follows a new path a tick or more late; `moveTo` with an unprocessed path returns true even if trimming later leaves no nodes.
- **Shutdown:** the final save waits without a time limit for every queued player data and `level.dat` write; the 60 s bound in `AsyncPlayerDataSaving.shutdown` covers only writes queued after it. A real bound would let `System.exit` cut off the newest saves, so it was not made.

## Async fixes for pathfinding, tracker and saving

**Correct.** Shared beds in the villager hall: 5 and 3 on release 26.3-11, 0 with the fix. One run per side, async pathfinding on.

**Same.** Standing tick time 37.0 → 36.8 ms (p = 0.77) against the build before the fix, 3 runs each, mixed load with async pathfinding on. No cost is measurable.

Plain Leaf lets its async features differ from vanilla in places. Kindling fixes those: a villager takes a bed or job site only if it really got it, partial paths and the brain's path are kept, mob costs are read on the main thread; a pending player save is never discarded, `save-all flush` and the stop wait for the writes, and a failed encode writes nothing; a failed async mob count does not stop spawning (Leaf pull request #937); the tracker's sync flags are not lost. In a fresh configuration only the tracker and the player data saving are on; async pathfinding and async mob spawning are opt-in.

<details>
<summary>Method and all numbers</summary>

Kindling's patches `0321` (async mob spawning stall, mirrors Leaf pull request #937), `0323` (async player data saving), `0324` and Paper `0071` (async pathfinding and tracker) make the async features act as vanilla where they did not.

**Villager claims.** `type=villagers` builds walled halls with twelve villagers, twelve beds and twelve composters each; vanilla takes a place in the tick it chooses it, so no place is held twice. One run per side, async pathfinding on against off, bench host:

| Build | `shared_beds`, on | `shared_beds`, off | `shared_job_sites` | `bed_claims`, `job_site_claims` |
| --- | ---: | ---: | ---: | ---: |
| Release 26.3-11, round 1 | **5** | 0 | 0 | 48, 48 |
| Release 26.3-11, round 2 | **3** | 0 | 0 | 48, 48 |
| Build with the fix | 0 | 0 | 0 | 48, 48 in both |

The fix held in the Lithium build and the last build as well. Only beds showed the bug; shared job sites were 0 everywhere on the release, so that half of the bug is documented but not reproduced. The number of shared beds varies between runs.

**Cost of the fixes.** Mixed load, survival mode, 8 players, no overlay, the build before the fix against the build with the fix, 3 runs each, interleaved:

| Figure | Before the fix | With the fix | p |
| --- | ---: | ---: | ---: |
| Standing tick time avg | 37.0 ±0.7 | 36.8 ±0.5 | 0.77 |
| Standing p95 | 64.0 ±2.8 | 65.3 ±4.8 | 0.71 |
| Flight tick time avg | 26.6 ±1.0 | 26.7 ±0.6 | 0.92 |
| Flight p95 | 62.1 ±3.1 | 61.3 ±0.9 | 0.71 |
| Standing GC pause | 888 ±486 ms | 1 066 ±442 ms | 0.69 |
| Standing main-thread wait | 586 ±229 ms | 516 ±308 ms | 0.77 |
| CPU time | 1 442 ±6 s | 1 449 ±20 s | 0.62 |
| Peak RSS | 2 799 ±55 MB | 2 844 ±68 MB | 0.45 |

Nothing is significant; with three runs and a half range of 0.5 to 1 ms, an effect below about 1 ms of standing tick time cannot be excluded. The extra loop over the path types per search and the tracker's flag read are therefore not shown to be free, only below the noise of this test. The comparison also covers the async-saving change that was in the same build.

</details>

<details>
<summary>Caveats and open points</summary>

The speed of the async features against plain Leaf is not measured on its own; the [comparison with plain Leaf](vs-leaf.md) also changes the chunk defaults.

**Not tested on a running server:** async player data saving. Its new write ordering, flush barrier and shutdown drain have unit tests (`SerialWrites`), but `save-all flush` followed by a look at the file times, and a stop on a slow disk, were not run. The tracker's flag race has no unit test (`Entity` cannot be loaded without registries). The bench's fake players are creative by default, so mobs never path to them; the villager hall is the only scenario that exercises async pathfinding hard.

</details>

## Sleeping hoppers with a move event listener

**Same.** With a move event listener the bases tick 11.8 ms against 11.7 ms on release 26.3-12 (p = 0.63): no measurable effect of the fix. 3 runs each.

**Correct.** 15 of 15 counters identical with sleeping on and off. Unit tests: `StackListWakeTest` (9), `TemporaryCountChangeTest` (5), `WakesOnContentChangeTest` (2). Applies when `performance.sleeping-block-entity` is on.

With a plugin listening to `InventoryMoveItemEvent`, Paper's hopper sets a stack's count to the moved amount for the event and sets it back. Without the change that wakes every hopper sleeping on the inventory. `ItemStack#setCountTemporarily` tells the stack list, which keeps its caches correct but wakes nobody if the next change of that stack restores the old count; any other change wakes as before. Furnaces and brewing stands put themselves to sleep as content listeners, so a count or component change through the Bukkit mirror (`setAmount`, `setItemMeta`) wakes them on the same tick. Item flow is unchanged. A plugin sees fewer `InventoryMoveItemEvent` attempts from sleeping hoppers than on Paper.

<details>
<summary>Method and all numbers</summary>

Bases scenario with a listener (`move-event-listener`, overlays `parity-sleeping-listener-on` and `-off`): 11.8 ±0.2 ms on the restorations build, 11.7 ±0.1 ms on release 26.3-12, 11.5 ms on the restorations build without a listener. The fewer wake/sleep cycles did not show in the tick time. The bench key and overlays: [kindling-bench README](../../kindling-bench/README.md).

</details>

## Parity scenarios

**Correct.** All four parity checks passed on the restorations build (`parity/run.sh <jar> all`).

- **Bases:** 15 of 15 counters identical to `bases-ci.json`.
- **Sleeping block entities, both pairs** (default and tuned hopper rates, on against off): 10 of 10 counters identical in each.
- **Bases with a move event listener:** 15 of 15 counters identical, sleeping on and off.
- **Villagers:** 4 of 4 checks, no bed or job site claimed twice.

## Vanilla diff against Mojang's jar

**Correct.** Contraptions: the restorations build differs from vanilla in 0 blocks; release 26.3-12 differed in 8, vanilla against vanilla in 0. 72 cells, 400 stepped ticks, one seed and one console script per jar, 1 run each (the first real runs of the tool).

**Indicative.** Worldgen: not conclusive (see below).

[`kindling-bench/vanilla-diff`](../../kindling-bench/vanilla-diff/README.md) starts Mojang's server jar and the jar under test one after the other on the same seed with the same console script, saves both worlds and compares them: block states, biomes, block entities, entities, scheduled ticks. In the contraptions mode, 72 redstone, hopper, piston, rail and sensor cells are built while the game is frozen, then stepped for 400 ticks; 110 full chunks were compared. Contraptions without a difference show that the restorations build acts as vanilla in these 72 cells, not in all cases.

<details>
<summary>Method and all numbers</summary>

| Jar under test | Differences | Blocks | Block entities and containers | Entities |
| --- | ---: | ---: | --- | ---: |
| Vanilla (the tool's noise) | 80 in 23 chunks | **0** | 4 + 2: two furnaces, cooking and fuel timing | 74 |
| Release 26.3-12 | 165 in 49 chunks | **8** | 27 + 7: five chests, the same two furnaces; one scheduled tick | 122 |
| Restorations build | 128 in 45 chunks | **0** | 4 + 2: the same two furnaces | 122 |

- **Vanilla against vanilla** is the noise floor: no block differs. The entity differences are dropped items and minecarts whose position or motion differs (unseeded random spread), and two furnaces differ in smelting timing. Their number varies from run to run, so entity differences are not evaluated.
- **Release 26.3-12 against vanilla:** a redstone wire network is unpowered where vanilla has power 10 to 15, together with a powered repeater (8 blocks). Three chests hold the same items in another order and two hold different totals (hopper pile cells, so entity order; for example 7 cobblestone and 9 oak logs in vanilla, 5 cobblestone, 9 oak logs and 1 sand in the jar). One comparator tick exists only in vanilla.
- **Restorations build against vanilla:** no block differs and no chest differs. What is left is the noise of the vanilla run. The restored `Vec3i` hash and entity order aim at exactly the differences release 26.3-12 shows; the diff did not isolate which restoration fixed which cell.

**The worldgen mode is not conclusive.** Vanilla against vanilla already differs in 410 of 624 chunks: feature placement across chunk borders depends on how the worker threads are scheduled. The restorations build against vanilla differs by 164 000 to 183 000 entries in 508 to 513 chunks; the terrain is equal, the features differ, which is the same scheduling effect. Six biome cells differ and are not explained. A comparison with one worker thread on both sides is open ([roadmap](../ROADMAP.md)).

The comparer needed two fixes before the runs were meaningful: Minecraft 26 block palette entries (a different format) and wrapper compounds in mixed NBT lists. Both are committed with a unit test.

</details>

## Entity UUIDs from world generation

**Correct.** No more mobs refused for a duplicate UUID: 4 warnings in 2 of 4 pregeneration runs before, 0 in 4 runs after (about 278 000 chunks). Against the build before the fix, both with compiled material rules.

Purpur's `settings.entity.shared-random` (on by default, in plain Leaf too) gives every entity one shared random, a `ThreadUnsafeRandom`, and every entity draws its UUID from it. World generation spawns mobs on several chunk workers at once, so two workers can read the same state and give two mobs the same UUID. When the chunks load, the chunk system refuses the second mob (`Entity uuid already exists ... can't add`), so it is missing from the world. In vanilla every entity has a random of its own and this cannot happen. Kindling gives an entity that is created off the tick threads a random of its own (`net.kindlingmc.entity.EntityRandoms`, patch `0342`); on the tick threads Purpur's shared random stays as it was.

<details>
<summary>Method and all numbers</summary>

The warning showed up during the A/B of [compiled material rules](chunks.md), which generates about 9 % more chunks per second: the runs with the compiler on logged 0, 3, 0 and 1 duplicates, the runs with it off 0, 0, 0 and 0 (each a 240 s pregeneration of about 63 000 to 69 000 chunks). Each pair was two different mob types next to each other with the same UUID and a tick count of 0, freshly generated. The fixed build (label `uuid-fix`, compiled material rules on by default) logged 0 in each of 4 runs of the same scenario (70 145, 69 529, 69 376 and 69 376 chunks). Unit tests: an entity created on a `TickThread` gets the shared random, one created elsewhere gets a new one.

</details>

<details>
<summary>Caveats and open points</summary>

- Four runs per side are few for a rare race; the cause follows from the code (an unsynchronised shared random read by several chunk workers), the runs only show that the symptom is gone.
- Mobs generated before the fix that were refused are not restored.

</details>
