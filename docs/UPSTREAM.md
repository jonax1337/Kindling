# 🔁 Following Leaf

Kindling is a GitHub fork of [Leaf](https://github.com/Winds-Studio/Leaf): Leaf's `ver/26.3` branch plus a few commits on top.

> [!NOTE]
> A weekly workflow ([`.github/workflows/leaf-upstream.yml`](../.github/workflows/leaf-upstream.yml), Mondays 06:00 UTC, also startable by hand) merges Leaf's new commits into a branch `auto/leaf-<commit>`, applies the patches, builds the jar, runs Kindling's tests and the [parity check](../kindling-bench/README.md#parity-check-ci) (the `bases` counters must equal `kindling-bench/parity/bases-ci.json`). It reports in one issue labelled `upstream`: a link to open the pull request when the merge applied, built and passed the tests, together with the parity result (a failed parity check does not stop the branch; the end of its log lists the counters that differ), otherwise the step that failed (a merge conflict, a Leaf patch with a number Kindling uses, the build or the tests) with the end of its log. Once Kindling contains Leaf's branch again, it closes that issue.
>
> The pull request is still merged by hand after a `kindling-bench` run.

## Layout

| Path | What Kindling changes there |
| --- | --- |
| `leaf-server/minecraft-patches/features/0316-…` to `0329-…` | Kindling's own Minecraft patches (directional chunk loading, pregenerator, palette decoder, sleeping hoppers that keep asking plugins, chunk generation cap, async mob spawning fix, sleeping block entities that act as on Paper, safe async player data saving, async pathfinding and tracker fixes, natural spawning that skips needless spawn cost biome lookups, the Lithium redstone sensor and chunk tick port, pregenerator lifecycle hooks, optional client-paced chunk sending, copies of unreadable chunks), appended after Leaf's; their subject starts with `Kindling:` |
| `leaf-server/minecraft-patches/features/0066-5-second-TPS-average.patch` | The lagging check computes only the 5-second average. The same change as Leaf pull request [#970](https://github.com/Winds-Studio/Leaf/pull/970), without a `Kindling` marker, so the patch matches Leaf's once Leaf has merged it |
| `leaf-server/minecraft-patches/features/0321-Kindling-Fix-async-mob-spawning-stall.patch` | A failed async mob count no longer leaves the world without natural spawning until restart. Mirrors Leaf pull request [#937](https://github.com/Winds-Studio/Leaf/pull/937) (stall fix only, ported to the current code) and is dropped once Leaf has merged it |
| `leaf-server/minecraft-patches/features/0324-Kindling-Fix-async-pathfinding-and-tracker-behaviour.patch`, `leaf-server/paper-patches/features/0071-…` and `org/dreeam/leaf/async/path/` | Async pathfinding acts as vanilla: a villager claims a POI only if it really took it, partial paths are kept, the brain's `PATH` follows the navigation, frogs and axolotls no longer share node evaluators, the Paper pathfinder API returns computed paths, and mob costs and hooks (`onPathfindingDone`, `maxUpStep`) are read or run on the main thread. The async tracker takes `needsSync` and `syncVelocity` atomically. Contains the correctness part of Leaf pull request [#943](https://github.com/Winds-Studio/Leaf/pull/943); once Leaf has merged it, the overlapping part is dropped |
| `leaf-server/paper-patches/features/0069-…` to `0071-…` | Kindling's own Paper patches (default number of chunk workers, `/version` message, processed paths from the Paper pathfinder API), appended after Leaf's |
| `leaf-server/src/main/java/net/kindlingmc/` | Kindling's own server code: `pregen` (pregenerator), `chunk` (palette decoder), `version` (`/version` text and the update check against Kindling's GitHub releases), `path` (mob path costs captured for async pathfinding), `tracker` (the tracker's sync flags), `spawn` (the one-entry position memo for natural spawning) |
| `leaf-server/src/test/java/net/kindlingmc/` | Kindling's tests, run through `org.bukkit.support.suite.KindlingTestSuite` |
| `leaf-server/src/main/java/org/dreeam/leaf/` | Leaf's own code; Kindling changes defaults and adds config modules there, every line marked `// Kindling - …` |
| `leaf-server/build.gradle.kts.patch` | The name in the jar manifest (`Brand-Name`, `Implementation-Title`) |
| `settings.gradle.kts` | The `kindling-bench` module |
| `kindling-bench/` | The load test |
| `.github/workflows/` | `build.yml` (every push to `ver/26.3` that changes more than docs and the bench becomes a pre-release `26.3-<build>`), `leaf-upstream.yml` (above), `parity.yml` (the parity check on every change to patches, Kindling code or the bench) |

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
