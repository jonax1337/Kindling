# 🔁 Following Leaf

Kindling is a GitHub fork of [Leaf](https://github.com/Winds-Studio/Leaf): Leaf's `ver/26.3` branch plus a few commits on top.

> [!NOTE]
> A weekly workflow ([`.github/workflows/leaf-upstream.yml`](../.github/workflows/leaf-upstream.yml), Mondays 06:00 UTC, also startable by hand) merges Leaf's new commits into a branch `auto/leaf-<commit>`, applies the patches, builds the jar, runs Kindling's tests and the [parity check](../kindling-bench/README.md#parity-check-ci) (the `bases` counters must equal `kindling-bench/parity/bases-ci.json`). It reports in one issue labelled `upstream`: a link to open the pull request when the merge applied, built and passed the tests, together with the parity result (a failed parity check does not stop the branch; the end of its log lists the counters that differ), otherwise the step that failed (a merge conflict, a Leaf patch with a number Kindling uses, the build or the tests) with the end of its log. Once Kindling contains Leaf's branch again, it closes that issue.
>
> The pull request is still merged by hand after a `kindling-bench` run.

## Layout

| Path | What Kindling changes there |
| --- | --- |
| `leaf-server/minecraft-patches/features/0316-…` to `0328-…` | Kindling's own Minecraft patches (directional chunk loading, pregenerator, palette decoder, sleeping hoppers that keep asking plugins, chunk generation cap, async mob spawning fix, sleeping block entities that act as on Paper, safe async player data saving, async pathfinding and tracker fixes, natural spawning that skips needless spawn cost biome lookups, the Lithium redstone sensor and chunk tick port, pregenerator lifecycle hooks, copies of unreadable chunks), appended after Leaf's; their subject starts with `Kindling:` |
| `leaf-server/minecraft-patches/features/0066-5-second-TPS-average.patch` | The lagging check computes only the 5-second average. The same change as Leaf pull request [#970](https://github.com/Winds-Studio/Leaf/pull/970), without a `Kindling` marker, so the patch matches Leaf's once Leaf has merged it |
| `leaf-server/minecraft-patches/features/0321-Kindling-Fix-async-mob-spawning-stall.patch` | A failed async mob count no longer leaves the world without natural spawning until restart. Mirrors Leaf pull request [#937](https://github.com/Winds-Studio/Leaf/pull/937) (stall fix only, ported to the current code) and is dropped once Leaf has merged it |
| `leaf-server/minecraft-patches/features/0324-Kindling-Fix-async-pathfinding-and-tracker-behaviour.patch`, `leaf-server/paper-patches/features/0071-…` and `org/dreeam/leaf/async/path/` | Async pathfinding acts as vanilla: a villager claims a POI only if it really took it, partial paths are kept, the brain's `PATH` follows the navigation, frogs and axolotls no longer share node evaluators, the Paper pathfinder API returns computed paths, and mob costs and hooks (`onPathfindingDone`, `maxUpStep`) are read or run on the main thread. The async tracker takes `needsSync` and `syncVelocity` atomically. Contains the correctness part of Leaf pull request [#943](https://github.com/Winds-Studio/Leaf/pull/943); once Leaf has merged it, the overlapping part is dropped |
| `leaf-server/paper-patches/features/0069-…` to `0073-…` | Kindling's own Paper patches (default number of chunk workers and its formula in `ChunkWorkers`, `/version` message, processed paths from the Paper pathfinder API, optional lower OS priority for the chunk workers), appended after Leaf's |
| `leaf-server/src/main/java/net/kindlingmc/` | Kindling's own server code: `pregen` (pregenerator), `chunk` (palette decoder, chunk quarantine, worker count, look-ahead direction), `version` (`/version` text and the update check against Kindling's GitHub releases), `path` (mob path costs captured for async pathfinding), `tracker` (the tracker's sync flags), `spawn` (the one-entry position memo for natural spawning), `sleeping` (hopper rules), `save` (ordered player data writes), `ai` (sensors a mob does not use), `world` (freezable water), `doctor` (`/kindling doctor`, the host monitor), `memory` (idle memory return), `os` (worker priority) |
| `leaf-server/src/test/java/net/kindlingmc/` | Kindling's tests, run through `org.bukkit.support.suite.KindlingTestSuite` |
| `leaf-server/src/main/java/org/dreeam/leaf/` | Leaf's own code; Kindling changes defaults and adds config modules there, every line marked `// Kindling - …` |
| `leaf-server/build.gradle.kts.patch` | The name in the jar manifest (`Brand-Name`, `Implementation-Title`) |
| `settings.gradle.kts` | The `kindling-bench` module |
| `kindling-bench/` | The load test |
| `.github/workflows/` | `build.yml` (every push to `ver/26.3` that changes more than docs and the bench becomes a pre-release `26.3-<build>`), `leaf-upstream.yml` (above), `parity.yml` (the parity check on every change to patches, Kindling code or the bench) |

## Fixes Leaf could take

Kindling switches Leaf's async features and sleeping block entities on by default, which made their differences from Paper and vanilla matter. The fixes below are for bugs that plain Leaf has as well, whenever the feature is switched on, and are candidates for pull requests to Leaf. **The owner decided on 2026-10-28 to send none for now**; this list says what exists, so a pull request can be cut from the patch later. Each patch is a separate commit with a focused topic, so it can be lifted out. The evidence is in [MEASUREMENTS.md](MEASUREMENTS.md): the parity scenarios of `kindling-bench` reproduced several of the bugs on release 26.3-11.

| Leaf bug (feature, on in Kindling by default) | Kindling's fix | Leaf references | Evidence |
| --- | --- | --- | --- |
| **Sleeping block entities.** A plugin's `InventoryMoveItemEvent#setItem` duplicates items (the Lithium path shrinks a copy of the stack); hoppers keep a stale inventory cache after a block is set without block updates (`updateHopperOnUpdateSuppression` is never called); Spigot's `hopper-amount` and `ticks-per.hopper-transfer` are ignored; a brewing stand or furnace that a plugin held back never wakes; `skipNextSleepCheckAfterCooldown` is never cleared; Purpur's `use-lava-from-underneath` is ignored; a `HopperInventorySearchEvent` that returns a compound container for the same two halves counts as a plugin holding the hopper back; Paper's `cooldown-when-full` is skipped | Patch `0322`: Lithium's insert and extract only when Paper would fire no `InventoryMoveItemEvent` and `hopper-amount` is 1, otherwise Paper's own transfer; the cache invalidation hook in `Level#setBlock`; cooldowns from `ticks-per.hopper-transfer`; wake-ups for held-back stands and furnaces; hoppers stay awake with `ticks-per.hopper-check` above 1. The logic lives in `net.kindlingmc.sleeping.SleepingHopperRules` | Leaf issue #950 and pull request #952 (tuned hopper rates; Kindling's fix is different and was not derived from it) | Release 26.3-11: 652 duplicated items, 906 against 2 751 items on a tuned chain, chests placed without physics never filled, stand never served; fixed build identical to Paper in all counters |
| **Async pathfinding.** `AcquirePoi` sets a villager's bed or job site memory without checking that `PoiManager#take` succeeded, so two villagers can hold the same place; partial paths are lost; the brain's `PATH` memory can differ from the navigation's path; frogs and axolotls share node evaluators; the mob's path costs, `onPathfindingDone` and `maxUpStep` are read or run on the pathfinding thread | Patch `0324` and Paper `0071`: claims only for a POI that was really taken, partial paths kept, the brain's `PATH` follows the navigation, evaluators pooled per generator, the mob's costs captured on the main thread (`net.kindlingmc.path.CapturedPathCosts`), the Paper pathfinder API returns computed paths | Pull requests #943 and #921 (open); Kindling carries the correctness part of #943 and the captured costs, which go further | Release 26.3-11: 5 and 3 shared beds with async pathfinding on, 0 off; fixed build 0 and 0 |
| **Async player data saving.** A save that is not done after 10 s is cancelled and the old file is read back, so a player can rejoin with an old inventory next to duplicated items; `save-all flush` does not wait for the writes; the bookkeeping map is changed without a lock and one job can drop another's entry; an encode error writes a truncated `.dat`; writes queued during shutdown can be discarded | Patch `0323`: nothing is cancelled, writes run in order on one thread and are tracked in `net.kindlingmc.save.SerialWrites`, a barrier for flushes, a drain before the storage closes (60 s), an encode error writes nothing | None known | Unit tests only (no server test yet) |
| **Async mob spawning.** If the asynchronous count job throws, the world stops spawning until restart (`spawnCountPending` is never reset) | Patch `0321`: the failed job clears its state, and the next tick submits a new count. Ported from the pull request to the current code | Pull request #937 (open); the patch is dropped once Leaf merges it | Not measurable (it needs an exception) |
| **Async entity tracker.** The tracker thread clears `needsSync` and `syncVelocity` while the main thread can set them, so a flag set during a send is lost (a knockback packet arrives a tick late) | Part of patch `0324`: a read first, an atomic `getAndSet` only when the flag is set (`net.kindlingmc.tracker.TrackerFlags`) | None known | Review only |
| **Purpur's lagging check** asks for all TPS averages every tick | Patch `0066`, the 5-second average only | Pull request #970 (already open) | See [MEASUREMENTS](MEASUREMENTS.md#the-lagging-check-of-the-tick-loop) |

Things in Kindling that are not Leaf bugs and stay here: the pregenerator, directional loading, the worker defaults, the palette decoder, the doctor, the opt-in settings.

## Merging a Leaf update

```bash
git remote add upstream https://github.com/Winds-Studio/Leaf.git   # once
git fetch upstream
git merge upstream/ver/26.3
./gradlew applyAllPatches
```

Conflicts can only appear where Leaf changed a line Kindling marks with `Kindling`, where Leaf added patches with the numbers Kindling uses, or in `AGENTS.md`, which Kindling replaces with its own (keep ours: `git checkout --ours AGENTS.md`; the workflow does that by itself).

> [!TIP]
> In the second case renumber Kindling's patches so they stay last: apply Leaf's patches, re-apply Kindling's commits on top in `leaf-server/src/minecraft/java` (the applied sources are a Git repository with one commit per patch), then run `./gradlew :leaf-server:rebuildMinecraftFeaturePatches`.

Then test, build and measure before releasing:

```bash
./gradlew :leaf-server:test --tests org.bukkit.support.suite.KindlingTestSuite
./gradlew createPaperclipJar
./gradlew :kindling-bench:jar --no-configuration-cache
bash kindling-bench/matrix.sh leaf-server/build/libs/leaf-paperclip-26.3.local-SNAPSHOT.jar <prefix> fork
bash kindling-bench/elytra.sh <kindling.jar> <leaf.jar> <prefix>
```

Compare with the numbers in [MEASUREMENTS.md](MEASUREMENTS.md). If Leaf changed something Kindling relies on (the chunk loader for directional loading, the chunk task scheduler for the pregenerator), say so in the release notes.

## Changing a Kindling patch

1. Edit the applied source in `leaf-server/src/minecraft/java` (Minecraft classes) or `paper-server` (Paper classes) and commit there (`git commit --fixup` onto the matching `Kindling:` commit, or a new commit for a new patch).
2. `./gradlew :leaf-server:rebuildMinecraftFeaturePatches :leaf-server:rebuildPaperServerFeaturePatches` turns the commits back into patch files.
3. Commit the changed `.patch` files in this repository.

> [!CAUTION]
> The commit author in the applied sources becomes the `From:` line of the public patch file. Commit there with the identity you want published, and check the rebuilt patches before committing them.

Code that does not touch Minecraft classes belongs in `leaf-server/src/main/java/net/kindlingmc/` instead of a patch; a patch should only hook it in.
