# 🧬 Vanilla parity

[← Overview](../MEASUREMENTS.md)

Where Leaf or Gale changed vanilla behaviour without a switch, Kindling restores it. The fixes are checked against the vanilla code, by unit tests (`KindlingTestSuite`: 526 tests, 0 failures), by the parity scenarios and, for contraptions, by a [differential run against Mojang's jar](#vanilla-diff-against-mojangs-jar).

**Speed, measured as a whole** (the build with all restorations against 26.3-12, 3 runs each, 2026-10-29; [details](vs-leaf.md#final-round-the-vanilla-build-of-2026-10-29-against-263-12-and-plain-leaf)): the bases (11.5 against 11.5 ms) and the flight (27.2 against 28.1 ms) show no cost. Standing among 1 200 mobs the build is 8 % slower (36.6 → 39.7 ms), most likely because async pathfinding and async mob spawning are off now. The restored code itself costs at most about 1 ms there, which the bench cannot separate from noise. No restoration was measured on its own. Which Leaf patch each fix undoes: [UPSTREAM.md](../UPSTREAM.md#deviations-from-vanilla-that-kindling-restores).

**Contents**

- [Defaults a fresh configuration writes](#defaults-a-fresh-configuration-writes)
- [Hash, goals and entity order](#hash-goals-and-entity-order)
- [Smaller restorations](#smaller-restorations)
- [Entity movement, age and game events](#entity-movement-age-and-game-events)
- [Biome zoom](#biome-zoom)
- [Async pathfinding, when switched on](#async-pathfinding-when-switched-on)
- [Sleeping hoppers with a move event listener](#sleeping-hoppers-with-a-move-event-listener)
- [Parity scenarios](#parity-scenarios)
- [Vanilla diff against Mojang's jar](#vanilla-diff-against-mojangs-jar)
- [What still differs from vanilla](#what-still-differs-from-vanilla)

---

## Defaults a fresh configuration writes

> [!NOTE]
> 🔻 **Ten settings default to vanilla behaviour; standing tick time among 1 200 mobs 36.6 → 39.7 ms (+8 %), bases and flight unchanged** · against 26.3-12, whose defaults had async pathfinding and mob spawning on · 3 runs each · 2026-10-29
>
> 🛡️ Each value was compared with the vanilla code that reads it (read from the code, 2026-10-28)

Existing files keep their values, which is why `/kindling doctor` has a "vanilla parity" finding: it names each setting that runs at a non-vanilla value and the value to set.

| File and key | Before | Now | Why that is vanilla |
| --- | --- | --- | --- |
| Gale world: `small-optimizations.max-projectile-chunk-loads.per-tick` | 10 | -1 | A negative limit never stops a projectile; ender pearls no longer freeze at the edge of unloaded chunks |
| Gale world: `small-optimizations.max-projectile-chunk-loads.per-projectile.max` | 10 | -1 | same |
| Gale world: `small-optimizations.reduced-intervals.check-stuck-in-wall` | 10 | 1 | An interval of 1 checks `isInWall()` every tick |
| Gale world: `small-optimizations.reduced-intervals.villager-item-repickup` | 100 | -1 | -1 keeps vanilla's `setDefaultPickUpDelay()` (10 ticks) |
| Gale world: `gameplay-mechanics.entity-wake-up-duration-ratio-standard-deviation` | 0.2 | 0 | 0 gives Paper's fixed wake-up duration |
| Gale global: `small-optimizations.reduced-intervals.update-entity-line-of-sight` | 4 | 1 | An interval of 1 senses every tick |
| `leaf-global.yml`: `performance.skip-map-item-data-updates-if-map-does-not-have-craftmaprenderer` | `true` | `false` | Maps without the default renderer keep updating their colours |
| `leaf-global.yml`: `performance.optimize-player-movement` | `true` | `false` | `maybeBackOffFromEdge` runs again; the flag also gates a redundant view-distance shortcut, now off too |
| `leaf-global.yml`: `async.async-pathfinding.enabled` | `true` | `false` | Paths are computed on the main thread (see [below](#async-pathfinding-when-switched-on)) |
| `leaf-global.yml`: `async.async-mob-spawning.enabled` | `true` | `false` | Mob counts are taken on the main thread, never stale |

The async entity tracker, async player data saving and sleeping block entities stay on (with Kindling's fixes). The doctor no longer recommends async pathfinding. The earlier "the async fixes cost nothing" figures in [async.md](async.md) hold only with the options switched on.

**What the defaults cost** (mixed load, [details](vs-leaf.md#final-round-the-vanilla-build-of-2026-10-29-against-263-12-and-plain-leaf)): the new build among 1 200 mobs ticks 39.7 ms against 36.6 ms for 26.3-12 (p = 0.005 \*), 40.5 ms in a second series. With the overlay `async-previous-defaults` (async pathfinding and mob spawning on) it ticks 37.5 ms (p = 0.051 against the second series, misses the rule), so the async options are the likely main cause. Neither the flight (27.2 against 28.1 ms) nor the bases (11.5 against 11.5 ms) shows a cost. Which of the ten settings costs what was not separated; with the in-wall and line-of-sight checks every tick the rest is at most about 1 ms.

## Hash, goals and entity order

> [!NOTE]
> 🤝 **`Vec3i` hash, goal order and entity-section order are vanilla's again; no measurable cost in the bases (11.5 against 11.5 ms, p = 0.80) or the flight (27.2 against 28.1 ms, p = 0.29)** · against 26.3-12, measured together with the other restorations, not one by one · 3 runs each · 2026-10-29
>
> 🛡️ Compared with the vanilla code, unit tests; the contraptions diff finds no block difference against vanilla ([below](#vanilla-diff-against-mojangs-jar)), 26.3-12 had eight

- **`Vec3i.hashCode`** is `(y + z * 31) * 31 + x` again instead of Leaf's mixing hash (`BlockPos(1,0,0)` hashes to 1, `(0,1,0)` to 31, `(0,0,1)` to 961). Every `HashMap` and `HashSet` keyed by a position iterates in vanilla order, including the set that orders redstone dust's neighbour updates, which location-dependent contraptions rely on. Dense, structured position sets get vanilla's collisions back; that is what Leaf's hash avoided. Tests: `VanillaVec3iHashTest`.
- **`GoalSelector.availableGoals`** is Paper's `ObjectLinkedOpenHashSet` again: goals iterate in the order they were added, the first-added of equal priority wins a shared flag (a zombie's `LookAtPlayerGoal(8)` before `RandomLookAroundGoal(8)`), `canUse()` calls and their random draws happen in vanilla order, and adding the same goal twice keeps the first entry. Paper's original `tick()` is back; Leaf's cached `isInterruptable()` is not kept, because some goals change it during the tick. Tests: `GoalSelectorOrderTest` (6). Leaf's `BinaryGoalSet` and `Vec3iHasher` stay in place, unused.
- **Entities in a chunk section** keep their insertion order on removal (shift down with `System.arraycopy`, as Moonrise and vanilla; Sakura's O(1) `has()` stays). It decides which item a hopper pulls first when several lie on it. A removal is O(n) with one int-map write per shifted entity; no cost shows in the bench loads.

## Smaller restorations

> [!NOTE]
> 🛡️ **Six small deviations restored; not measured on their own, no cost in the bench loads** · compared with the vanilla and Paper code · only the shapeless-recipe helper has unit tests (`SoleMatchTest`, 8) · 2026-10-28

| Change | What differed | Now |
| --- | --- | --- |
| Lightning | Freshly loaded chunks got about half the strikes, and the thunder chance was drawn once per chunk | `thunderChance > 0 && random.nextInt(thunderChance) == 0` per chunk tick, only while thundering |
| Zombie villager cure | An offline starter still gave the reputation discount | The event runs only for an online `ServerPlayer` |
| `Paper.FailedSpawnAttempts` | Counted and written for every chunk | Counted and written only when Leaf's `throttle-mob-spawning` or Paper's `failed-attempts-threshold` is on (`FailedSpawnThrottle`); an old tag is dropped on the next save while both are off |
| Brownian walk (`InsideBrownianWalk`) | 26 values drawn from the mob's random per run | The shuffle uses `ThreadLocalRandom`; the mob's random stream is untouched. The sequence cannot equal vanilla's, whose source is unseeded |
| `World#getTileEntityCount` | The ticker count (`optimize-block-entities`, on by default) | Paper's count over the loaded chunks; the Leaf option no longer affects it |
| Shapeless recipes | First fit per item, so a datapack recipe `[#logs, oak_log]` rejected an oak and a birch log | The first-fit path only when no item can go to two different ingredients, otherwise vanilla's stacked-contents matching (`SoleMatch`); at most 9 ingredients, a few more `Ingredient.test` calls |

## Entity movement, age and game events

> [!NOTE]
> 🛡️ **Movement flags, entity age, block speed factor, `distanceTo`, attribute sums and game event dispatch match vanilla; not measured on their own, no cost in the bench loads** · compared with the vanilla code · `InsideBlocksOrderTest` (3), `GameEventListenerChangesTest` (3) · 2026-10-28

- **Inside-block checks** (`BlockGetter.forEachBlockIntersectedBetween`, used by `Entity.checkInsideBlocks` and the ghast) visit blocks as vanilla does: `betweenClosed` for a still entity, `betweenCornersInDirection` for the start and target box, one `LongOpenHashSet` for deduplication. It decides pressure plate and tripwire order. Leaf's single-axis shortcut is removed, so a falling entity allocates one set per call again (no cost shows in the bench loads).
- **Zero-movement skip** (Gale): `horizontalCollision` and `minorHorizontalCollision` are still reset; `verticalCollision`, `verticalCollisionBelow` and the ground state are reset only when `isLocalInstanceAuthoritative()`, so players and vehicles steered by a player keep them.
- **Entity age:** `getTicksLived()` and the saved `Spigot.ticksLived` count only ticked ticks, like Paper, unless Paper's `despawn-time` applies to the entity type.
- **Floating point:** the block speed factor guards are `x != 0 || z != 0` and `factor != 1.0F`; `Entity.distanceTo` is vanilla's float math unless `-DLeaf.enableFMA=true`; attribute modifiers are summed in vanilla's hash order (`Object2ObjectOpenHashMap`).
- **Game events:** a listener registered or removed while an event is delivered is deferred as in vanilla (it used to throw `ConcurrentModificationException`, which affected plugin handlers that place or break sculk sensors or spawn allays), and listeners in sections far above or below the build height get a real registry from a lazily created map.

## Biome zoom

> [!NOTE]
> 🛡️ **The zoom chooses exactly vanilla's cell** · checked against a copy of vanilla's algorithm over 4 × 10 000 000 random (seed, position) pairs plus the known counterexample · the test fails on Leaf's original `BiomeManager` · 2026-10-28
>
> 🤝 **`performance.biome-zoom-shortcut` on against off: flight tick time 27.2 → 27.7 ms (p = 0.49), standing 39.7 → 39.9 ms (p = 0.83), no memory gain claimed** · new build, mixed load, 3 runs each · 2026-10-29 · ⏸️ stays off by default

Leaf's pruned zoom (Carpet-Fixes port) summed the fiddled distance as x + y + z, vanilla as z + y + x; the rounding differed and picked another cell in about 2 of 200 million positions (zoom seed -8055847622726226809 at block -20104644, 265, -14313980: vanilla corner 6, Leaf corner 3, pinned in `BiomeZoomParityTest`). Now the final distance and the three pruning bounds add up in vanilla's order, so a bound is never larger than the distance vanilla computes and pruning never skips a candidate vanilla would pick. The cost is unchanged.

<details>
<summary><b>The opt-in shortcut</b></summary>

When the eight candidate cells lie in one chunk column (`(x & 3) != 3` and `(z & 3) != 3`, 9 of 16 positions) it looks the chunk up once. If the sections holding the two quart rows each store one biome (0-bit index storage; Paper uses a hash palette even there, so the detection reads the storage bits) and both give the same `Holder`, that biome is returned without a fiddle. Otherwise the exact zoom runs and reads the chosen cell from the chunk already found. Sources without the override (lambdas, biome sources, the async spawn-cost sampler) take the normal path. It touches no chunk vanilla would not touch; `ProtoChunk` before `BIOMES` status returns null so vanilla's exception still comes from the normal path. `UniformBiomeCellsTest` (6) runs the detection on real sections. The share of positions it catches is expected to be below the 71 % of neighbourhoods that are uniform in single-valued sections, because only in-chunk neighbourhoods are tried. The mixed load showed no tick time difference ([table](vs-leaf.md#final-round-the-vanilla-build-of-2026-10-29-against-263-12-and-plain-leaf)). Its peak RSS rows are marked (2 845 → 2 774 MB), but a shortcut that allocates nothing cannot plausibly lower resident memory and the heap after GC does not move (1 153 → 1 155 MB), so that is read as chance among about 40 rows. A world-generation profile on and off would show the effect on generation, which the mixed load does not exercise much; not run.

</details>

## Async pathfinding, when switched on

> [!NOTE]
> 🛡️ **Targets, trimming and completion follow vanilla; applies only with `async.async-pathfinding.enabled: true` (default `false`)** · compared with vanilla's `PathNavigation` · `AsyncPathCompletionTest` (4); villager parity run on the new build: no bed or job site claimed twice · with the options on, the standing tick time is 37.5 against 40.5 ms ([measured](vs-leaf.md#final-round-the-vanilla-build-of-2026-10-29-against-263-12-and-plain-leaf)) · 2026-10-29

- `createPath` sets `targetPos`, `reachRange` and the stuck timeout like vanilla for every path it returns, check paths included: at once for a processed path or one to a single position, otherwise when the result arrives (the next `leaf$preparePath`, `recomputePath` or `createPath`).
- Only a path given to `moveTo` is trimmed (a processed one inside `moveTo`, an unprocessed one once when it is processed); paths from `recomputePath` are not.
- Every async path completes: any `Throwable` gives an empty path and is rethrown to the worker's handler, and a path queued after the executor shut down is computed on the calling thread, so `AcquirePoi`, `SetClosestHomeAsWalkTarget` and `NearestBedSensor` always get a result.
- **Still different:** for several target positions the target arrives with the result; a mob follows a new path a tick or more late; `moveTo` with an unprocessed path returns true even if trimming later leaves no nodes.
- **Shutdown:** the final save waits without a time limit for every queued player data and `level.dat` write; the 60 s bound in `AsyncPlayerDataSaving.shutdown` covers only writes queued after it. A real bound would let `System.exit` cut off the newest saves, so it was not made.

## Sleeping hoppers with a move event listener

> [!NOTE]
> 🤝 **With a move event listener the bases tick 11.8 ms against 11.7 ms on 26.3-12 (p = 0.63): no measurable effect of the fix** · 🛡️ parity passed: 15 of 15 counters identical with sleeping on and off · applies when `performance.sleeping-block-entity` is on · unit tests (`StackListWakeTest` 9, `TemporaryCountChangeTest` 5, `WakesOnContentChangeTest` 2) · 3 runs each · 2026-10-29

With a plugin listening to `InventoryMoveItemEvent`, Paper's hopper sets a stack's count to the moved amount for the event and sets it back. That used to wake every hopper sleeping on the inventory. `ItemStack#setCountTemporarily` now tells the stack list, which keeps its caches correct but wakes nobody if the next change of that stack restores the old count; any other change wakes as before. Furnaces and brewing stands put themselves to sleep as content listeners, so a count or component change through the Bukkit mirror (`setAmount`, `setItemMeta`) wakes them on the same tick. Item flow is unchanged. The fewer wake/sleep cycles did not show in the tick time of the bases scenario with a listener (`move-event-listener`, overlays `parity-sleeping-listener-on` and `-off`): 11.8 ±0.2 ms on the new build, 11.7 ±0.1 ms on 26.3-12, 11.5 ms on the new build without a listener. A plugin sees fewer `InventoryMoveItemEvent` attempts from sleeping hoppers than on Paper. The bench key and overlays: [kindling-bench README](../../kindling-bench/README.md).

## Parity scenarios

> [!NOTE]
> 🛡️ **All four parity checks passed on the new build** · `parity/run.sh <jar> all` · 2026-10-29

- **Bases:** 15 of 15 counters identical to `bases-ci.json`.
- **Sleeping block entities, both pairs** (default and tuned hopper rates, on against off): 10 of 10 counters identical in each.
- **Bases with a move event listener:** 15 of 15 counters identical, sleeping on and off.
- **Villagers:** 4 of 4 checks, no bed or job site claimed twice.

## Vanilla diff against Mojang's jar

> [!NOTE]
> 🛡️ **Contraptions: the new build differs from vanilla in 0 blocks; 26.3-12 differed in 8, vanilla against vanilla in 0** · 72 cells, 400 stepped ticks, one seed and one console script per jar · 1 run each, first real runs of the tool · 2026-10-29
>
> 🧪 **Worldgen: not conclusive** (see below)

[`kindling-bench/vanilla-diff`](../../kindling-bench/vanilla-diff/README.md) starts Mojang's server jar and the jar under test one after the other on the same seed with the same console script, saves both worlds and compares them: block states, biomes, block entities, entities, scheduled ticks. In the contraptions mode, 72 redstone, hopper, piston, rail and sensor cells are built while the game is frozen, then stepped for 400 ticks; 110 full chunks were compared.

| Jar under test | Differences | Blocks | Block entities and containers | Entities |
| --- | ---: | ---: | --- | ---: |
| Vanilla (the tool's noise) | 80 in 23 chunks | **0** | 4 + 2: two furnaces, cooking and fuel timing | 74 |
| 26.3-12 | 165 in 49 chunks | **8** | 27 + 7: five chests, the same two furnaces; one scheduled tick | 122 |
| New build | 128 in 45 chunks | **0** | 4 + 2: the same two furnaces | 122 |

- **Vanilla against vanilla** is the noise floor: no block differs. The entity differences are dropped items and minecarts whose position or motion differs (unseeded random spread), and two furnaces differ in smelting timing. Their number varies from run to run, so entity differences are not evaluated.
- **26.3-12 against vanilla:** a redstone wire network is unpowered where vanilla has power 10 to 15, together with a powered repeater (8 blocks). Three chests hold the same items in another order and two hold different totals (hopper pile cells, so entity order; for example 7 cobblestone and 9 oak logs in vanilla, 5 cobblestone, 9 oak logs and 1 sand in the jar). One comparator tick exists only in vanilla.
- **New build against vanilla:** no block differs and no chest differs. What is left is the noise of the vanilla run. The restored `Vec3i` hash and entity order aim at exactly the differences 26.3-12 shows; the diff did not isolate which restoration fixed which cell.

**The worldgen mode is not conclusive.** Vanilla against vanilla already differs in 410 of 624 chunks: feature placement across chunk borders depends on how the worker threads are scheduled. The new build against vanilla differs by 164 000 to 183 000 entries in 508 to 513 chunks; the terrain is equal, the features differ, which is the same scheduling effect. Six biome cells differ and are not explained. A comparison with one worker thread on both sides is open ([roadmap](../ROADMAP.md)).

The comparer needed two fixes before the runs were meaningful: Minecraft 26 block palette entries (a different format) and wrapper compounds in mixed NBT lists. Both are committed with a unit test. Contraptions without a difference show that the new build acts as vanilla in these 72 cells, not in all cases.

## What still differs from vanilla

Listed in the [README](../../README.md#how-vanilla-is-kindling): opt-in async features and `cache-biome`, the rest of Gale's zero-movement skip, the sculk catalyst pre-filter, entity age with a despawn time, `-DLeaf.enableFMA`, fewer hopper events from sleeping hoppers and the `use-vanilla-hopper` wake. The [vanilla-diff test](../../kindling-bench/vanilla-diff/README.md) is built to find what this list misses; its first contraptions run found nothing beyond the list, its worldgen mode is not conclusive yet.
