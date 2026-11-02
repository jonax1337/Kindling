# Following Leaf

Kindling is a GitHub fork of [Leaf](https://github.com/Winds-Studio/Leaf): Leaf's `ver/26.3` branch plus Kindling's commits on top. This page is for contributors: the layout, how a Leaf update comes in, how a patch is changed, and which Kindling fixes Leaf could take.

## Layout

| Path | Contents |
| --- | --- |
| `leaf-server/src/main/java/net/kindlingmc/` | Kindling's own server code. Packages: `pregen`, `chunk` (palette decoder, quarantine, worker count, worker demand, worker governor, look-ahead), `worldgen` (compiled material rules and density), `doctor` (with the host monitor), `stress`, `memory`, `os` (worker priority), `sleeping`, `spawn`, `path`, `tracker`, `save`, `entity`, `ai`, `world`, `biome`, `recipe`, `version`. |
| `leaf-server/src/test/java/net/kindlingmc/` | Kindling's tests, run through `org.bukkit.support.suite.KindlingTestSuite`. |
| `leaf-server/minecraft-patches/features/0316-…` to `0345-…` | Kindling's Minecraft patches, subject `Kindling:`, listed below. |
| `leaf-server/paper-patches/features/0069-…` to `0075-…` | Kindling's Paper patches, listed below. |
| `leaf-server/minecraft-patches/features/0066-5-second-TPS-average.patch` | A Leaf patch changed to match Leaf pull request [#970](https://github.com/Winds-Studio/Leaf/pull/970) exactly, without a `Kindling` marker. |
| `leaf-server/src/main/java/org/dreeam/leaf/` | Leaf's own code. Kindling changes defaults and adds config modules here; every line is marked `// Kindling - …`. |
| `leaf-server/build.gradle.kts.patch` | The jar manifest name (`Brand-Name`, `Implementation-Title`). |
| `kindling-bench/` | The load test ([README](../kindling-bench/README.md)). |
| `.github/workflows/` | `build.yml` (a push to `ver/26.3` that changes more than docs and the bench becomes a pre-release `26.3-<build>`), `leaf-upstream.yml` (below), `parity.yml` (the parity check on every change to patches, Kindling code or the bench). |

### Kindling's patches

| Patch | Topic |
| --- | --- |
| `0316` | Directional chunk loading |
| `0317`, `0327`, `0329` | Pregenerator, its lifecycle hooks, the full-disk re-check while the server sleeps |
| `0318` | Chunk section palettes without DataFixerUpper |
| `0319`, `0322`, `0339` | Sleeping block entities that act as Paper does for plugins |
| `0320` | Cap on concurrent chunk generations per player |
| `0321` | Async mob spawning stall (mirrors Leaf [#937](https://github.com/Winds-Studio/Leaf/pull/937)) |
| `0323` | Safe async player data saving |
| `0324`, `0338`, Paper `0071` | Async pathfinding and tracker behaviour (contains the correctness part of Leaf [#943](https://github.com/Winds-Studio/Leaf/pull/943)) |
| `0325`, `0343` | Cheaper natural spawning |
| `0326` | Lithium redstone sensor and chunk tick ports |
| `0328`, `0340` | Copies of unreadable chunks |
| `0330` to `0337` | Vanilla restorations ([list](reference/vanilla-differences.md#restored-leaf-changes)) |
| `0341` | Compiled material rules |
| `0342` | A random of its own for entities created off the tick threads |
| `0344` | Compiled density |
| `0345` | Wait counter for worker demand |
| Paper `0069`, `0072` | Default number of chunk workers (`ChunkWorkers`) |
| Paper `0070` | `/version` message |
| Paper `0073` | Lower OS priority for chunk workers |
| Paper `0074` | Block entity count of `World#getTileEntityCount` |
| Paper `0075` | Lazy index map of the per-section ticking list |

## Merging a Leaf update

A weekly workflow ([`leaf-upstream.yml`](../.github/workflows/leaf-upstream.yml), Mondays 06:00 UTC, also startable by hand) does the first steps:

1. Merges Leaf's new commits into a branch `auto/leaf-<commit>`.
2. Applies the patches, builds the jar, runs Kindling's tests and the [parity check](../kindling-bench/README.md#parity-check-ci) (the `bases` counters must equal `kindling-bench/parity/bases-ci.json`).
3. Reports in one issue labelled `upstream`: a link to open the pull request when the merge applied, built and passed the tests, together with the parity result, or otherwise the step that failed (a merge conflict, a Leaf patch with a number Kindling uses, the build or the tests) with the end of its log. A failed parity check does not stop the branch; the end of its log lists the counters that differ. The issue closes once Kindling contains Leaf's branch again.

The pull request is merged by hand after a `kindling-bench` run. By hand, the same merge is:

```bash
git remote add upstream https://github.com/Winds-Studio/Leaf.git   # once
git fetch upstream
git merge upstream/ver/26.3
./gradlew applyAllPatches
```

Conflicts appear only where Leaf changed a line marked `Kindling`, where Leaf added patches with numbers Kindling uses, or in `AGENTS.md`, which Kindling replaces with its own (`git checkout --ours AGENTS.md`; the workflow does this).

When Leaf takes a number Kindling uses, renumber Kindling's patches so they stay last: apply Leaf's patches, re-apply Kindling's commits on top in `leaf-server/src/minecraft/java`, then run `./gradlew :leaf-server:rebuildMinecraftFeaturePatches`.

Then test, build and measure:

```bash
./gradlew :leaf-server:test --tests org.bukkit.support.suite.KindlingTestSuite
./gradlew createPaperclipJar
./gradlew :kindling-bench:jar --no-configuration-cache
bash kindling-bench/matrix.sh leaf-server/build/libs/leaf-paperclip-26.3.local-SNAPSHOT.jar <prefix> fork
bash kindling-bench/elytra.sh <kindling.jar> <leaf.jar> <prefix>
```

Compare with [MEASUREMENTS.md](MEASUREMENTS.md). If Leaf changed code Kindling relies on (the chunk loader for directional loading, the chunk task scheduler for the pregenerator), say so in the release notes.

## Changing a Kindling patch

1. Edit the applied source in `leaf-server/src/minecraft/java` (Minecraft classes) or `paper-server` (Paper classes) and commit there: `git commit --fixup` onto the matching `Kindling:` commit, or a new commit for a new patch.
2. Turn the commits back into patch files:

   ```bash
   ./gradlew :leaf-server:rebuildMinecraftFeaturePatches :leaf-server:rebuildPaperServerFeaturePatches
   ```

3. Commit the changed `.patch` files in this repository.

> [!CAUTION]
> The commit author in the applied sources becomes the public `From:` line of the patch file. Commit there with the identity you want published, and check the rebuilt patches before committing them.

Code that does not need to live inside a Minecraft or Paper class belongs in `net.kindlingmc`; the patch only hooks it in.

## Fixes Leaf could take

Kindling turns Leaf's async features and sleeping block entities on by default, so their differences from Paper and vanilla matter. These fixes are for bugs plain Leaf has as well whenever the feature is on. None has been sent to Leaf yet; each patch is a separate commit, so a pull request can be cut from it.

| Leaf feature | Bug | Kindling's fix | Leaf references | Evidence |
| --- | --- | --- | --- | --- |
| Sleeping block entities | A plugin's `InventoryMoveItemEvent#setItem` duplicates items; hoppers keep a stale inventory cache after a block is set without updates; Spigot's `hopper-amount` and `ticks-per.hopper-transfer`, Purpur's `use-lava-from-underneath` and Paper's `cooldown-when-full` are ignored; a stand or furnace that a plugin held back never wakes. | Patch `0322`: Lithium's transfer only where Paper would fire no event and `hopper-amount` is 1, otherwise Paper's own; the cache hook in `Level#setBlock`; Paper's cooldowns; wake-ups. Logic in `net.kindlingmc.sleeping.SleepingHopperRules`. | Issue #950, pull request #952 (different fix) | Release 26.3-11: 652 duplicated items; the fixed build equals Paper in every counter. |
| Async pathfinding | `AcquirePoi` sets a villager's bed or job site without checking that `PoiManager#take` succeeded, so two villagers can hold one place; partial paths are lost; the brain's `PATH` can differ from the navigation; frogs and axolotls share node evaluators; mob path costs and hooks run on the pathfinding thread. | Patch `0324` and Paper `0071`; costs captured on the main thread in `net.kindlingmc.path.CapturedPathCosts`. | Pull requests #943 and #921 (open) | Release 26.3-11: 5 and 3 shared beds with it on; fixed build 0. |
| Async player data saving | A save not done after 10 s is cancelled and the old file read back (old inventory next to duplicated items); `save-all flush` does not wait; the bookkeeping map is changed without a lock; an encode error writes a truncated `.dat`; writes queued during shutdown can be lost. | Patch `0323`: ordered writes on one thread (`net.kindlingmc.save.SerialWrites`), a flush barrier, a shutdown that waits for every write, nothing written on an encode error. | None known | Unit tests only. |
| Async mob spawning | A count job that throws stops spawning in that world until restart. | Patch `0321`: the failed job clears its state. | Pull request #937 (open) | Not measurable (needs an exception). |
| Async entity tracker | The tracker thread clears `needsSync` and `syncVelocity` while the main thread can set them, so a knockback can arrive a tick late. | Part of patch `0324`: atomic `getAndSet` only when set (`net.kindlingmc.tracker.TrackerFlags`). | None known | Review only. |
| Purpur's shared entity random | Chunk workers spawn mobs from one random at once, so two generated mobs can get the same UUID and the second is refused. | Patch `0342` (`net.kindlingmc.entity.EntityRandoms`). | None known | Pregeneration: 4 refused mobs in 2 of 4 runs before, 0 in 4 after. |
| Purpur's lagging check | Asks for every TPS average each tick. | Patch `0066`: the 5-second average only. | Pull request #970 (open) | [ticking.md](measurements/ticking.md#the-lagging-check-of-the-tick-loop) |

The unconditional changes to vanilla that Kindling restores are listed in [Vanilla differences](reference/vanilla-differences.md#restored-leaf-changes). Leaf's `cache-biome` option (off by default, not used by Kindling) has its own bug, listed there too; a candidate for a report to Leaf.

Kindling's own features stay in Kindling: the pregenerator, directional loading, the worker defaults, the palette decoder, the doctor and the opt-in settings.
