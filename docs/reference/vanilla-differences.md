# Vanilla differences

Kindling aims at vanilla behaviour. This page lists where Leaf (or Gale, Purpur and Sakura through Leaf) changes vanilla unconditionally and Kindling restores it, the defaults it sets back to vanilla, and what still differs. The evidence for each item: [Vanilla parity measurements](../measurements/vanilla-parity.md).

Everything not listed here is Leaf, unchanged.

This rests on reading code, unit tests, the parity scenarios and, for redstone and hopper contraptions, the [vanilla diff](../../kindling-bench/vanilla-diff/README.md) against Mojang's jar: 0 differing blocks in 72 contraption cells. World generation is not compared yet; the diff's worldgen mode is not conclusive.

## Still different from vanilla

Opt-in, different by design:

- **Async pathfinding** (`async.async-pathfinding.enabled`): a mob follows a new path a tick or more late. With several target positions the navigation target is set when the result arrives.
- **Async mob spawning** (`async.async-mob-spawning.enabled`): spawn counts can be stale.
- **Leaf's `performance.cache-biome`**: keys its cache by the quart cell while the biome zoom depends on the position inside it, so a block can get another block's biome. It also writes two plain arrays without synchronisation.
- **`-DLeaf.enableFMA=true`** (off by default): changes the rounding of `Entity.distanceTo`.

Always on, not yet restored:

- **Gale's zero-movement skip** (Leaf patch `0051`): skips side effects of a zero-length move that vanilla runs: the record for inside-block checks, the fluid update in `LivingEntity.checkFallDamage` when not in water, and the empty movement emission. Only the vertical collision flags and the ground state are restored.
- **Sculk catalysts** (Leaf patch `0093`): drop game events through Leaf's `listensToEvent` pre-filter when their block entity was removed but the listener is still registered.
- **Entity age with a despawn time:** where Paper's `entities.spawning.despawn-time` is set for a type, Leaf counts ticks spent in weak-loaded chunks too, so `getTicksLived()` and the saved age are larger than on Paper. A thrown projectile also checks despawn before its physics there. Without that setting, the age counts as on Paper.
- **Sleeping hoppers** skip the `InventoryMoveItemEvent` attempts Paper fires on every check against a full destination, so plugins see fewer events. With `gameplay.use-vanilla-hopper` a pull still wakes them.
- **`performance.optimize-block-entities`** no longer affects `World#getTileEntityCount`.

## Restored Leaf changes

Kindling restores vanilla or Paper behaviour in its own patches on top. Leaf's patch files stay untouched.

| Leaf patch | What it changes | What Kindling restores | Kindling patch |
| --- | --- | --- | --- |
| `0260` Optimize Vec3i hashing | A mixing hash replaces `Vec3i.hashCode`, so every `HashSet<BlockPos>` iterates in another order, including the set that orders redstone dust neighbour updates. | Vanilla's `(y + z * 31) * 31 + x`. | `0330` |
| `0236` Optimize goal selector | Goals no longer run in insertion order, equal priorities start in another order, duplicates are kept, `isInterruptable()` is cached once per tick. | Paper's `ObjectLinkedOpenHashSet` and tick. | `0331` |
| `0012` Optimize random calls in chunk ticking (Gale) | New chunks get about half the lightning strikes; the thunder chance is drawn once per chunk. | One `nextInt(thunderChance)` per chunk tick. | `0332` |
| `0215` Use UUID for cure reputation | An offline starter of a zombie villager cure still gets the discount. | The reputation event only for an online `ServerPlayer`. | `0332` |
| `0182` Paper PR Throttle failed spawn attempts | `Paper.FailedSpawnAttempts` is counted and saved for every chunk. | Counted only when Leaf's `throttle-mob-spawning` or Paper's `failed-attempts-threshold` is on. | `0332` |
| `0180` Rewrite InsideBrownianWalk | The shuffle draws 26 values from the mob's random. | A source of its own, as vanilla's `Collections.shuffle`. | `0332` |
| `0063` (Paper) Optimize block entities count | `World#getTileEntityCount` returns the ticker count. | Paper's count over the loaded chunks. | Paper `0074` |
| `0008` Simpler ShapelessRecipe comparison (Gale) | The first fit of an item wins, so a recipe with `[#logs, oak_log]` rejects an oak and a birch log. | The first fit only where no item can go to two ingredients, otherwise vanilla's matching. | `0333` |
| `0195` Sakura BasicEntityList | The last entity moves into a freed slot, which changes the order of a chunk section's entities (which item a hopper pulls first). | Order-keeping removal. | `0334` |
| `0194` Sakura check inside blocks | Blocks are visited bottom-up and deduplicated against boxes, which can skip blocks and change the order. | Vanilla's iteration in the direction of travel. | `0334` |
| `0051` Skip entity move if movement is zero (Gale) | Clears the vertical collision flags and ground state of a still player or player-steered vehicle. | Kept when `isLocalInstanceAuthoritative()`. The other side effects: see above. | `0335` |
| `0070` Skip negligible planar movement multiplication | Velocities of 1e-6 or less, and speed factors within 1e-6 of 1, are not scaled. | Skips only multiplications that cannot change the result. | `0335` |
| `0153` Optimize Entity distanceToSqr | `Entity.distanceTo` is computed in double. | Vanilla's float and `Mth.sqrt` unless `-DLeaf.enableFMA=true`. | `0335` |
| `0149` Optimize attribute | Modifiers are summed in another map order, which changes floating-point sums. | Vanilla's summing order. | `0335` |
| `0259` Rewrite entity despawn time | `getTicksLived()` and the saved age count ticks in which the entity was not ticked. | Ticked ticks only, unless Paper's `despawn-time` is set for the type. | `0335` |
| `0093` Petal reduce work done by game event system | Registering a listener during an event throws `ConcurrentModificationException`; listeners far above or below the build height are dropped. | Vanilla's deferred changes and a map for far sections. | `0336` |
| `0087` Carpet Fixes optimized getBiome | Sums the distance in another order, so in about 2 of 200 million positions another cell wins. | Vanilla's summing order; the pruning stays. | `0337` |

Purpur's shared entity random (`settings.entity.shared-random`) could give two mobs from world generation the same UUID. Kindling patch `0342` gives an entity created off the tick threads a random of its own, as in vanilla.

## Defaults set back to vanilla

A fresh configuration writes these values. An existing file keeps its own; `/kindling doctor` lists every value that differs ("vanilla parity"). Leaf's `async.async-pathfinding.enabled` is already `false` in Leaf; the doctor still lists it when an older Kindling file has it on.

| File and key | Leaf or Gale default | Kindling default |
| --- | --- | --- |
| Leaf `async.async-mob-spawning.enabled` | `true` | `false` |
| Leaf `performance.optimize-player-movement` | `true` | `false` |
| Leaf `performance.skip-map-item-data-updates-if-map-does-not-have-craftmaprenderer` | `true` | `false` |
| Gale world `small-optimizations.max-projectile-chunk-loads.per-tick` | `10` | `-1` |
| Gale world `small-optimizations.max-projectile-chunk-loads.per-projectile.max` | `10` | `-1` |
| Gale world `small-optimizations.reduced-intervals.check-stuck-in-wall` | `10` | `1` |
| Gale world `small-optimizations.reduced-intervals.villager-item-repickup` | `100` | `-1` |
| Gale world `gameplay-mechanics.entity-wake-up-duration-ratio-standard-deviation` | `0.2` | `0` |
| Gale global `small-optimizations.reduced-intervals.update-entity-line-of-sight` | `4` | `1` |
