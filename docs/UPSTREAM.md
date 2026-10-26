# Following Leaf

Kindling is Leaf's `ver/26.3` branch plus a few commits on top. A weekly workflow (`.github/workflows/leaf-upstream.yml`, also startable by hand) merges Leaf's new commits into a branch `auto/leaf-<commit>`, applies the patches, builds the jar and runs Kindling's tests. It reports in one issue labelled `upstream`: a link to open the pull request when all of that passed, otherwise the step that failed (a merge conflict, a Leaf patch with a number Kindling uses, the build or the tests) with the end of its log. The pull request is still merged by hand after a `kindling-bench` run.

## Layout

| Path | What Kindling changes there |
| --- | --- |
| `leaf-server/minecraft-patches/features/0316-…` to `0319-…` | Kindling's own Minecraft patches, appended after Leaf's; their subject starts with `Kindling:` |
| `leaf-server/paper-patches/features/0069-…` to `0070-…` | Kindling's own Paper patches (default number of chunk workers, `/version` message), appended after Leaf's |
| `leaf-server/src/main/java/net/kindlingmc/` | Kindling's own server code (`pregen`: pregenerator, `chunk`: palette decoder, `version`: `/version` text and the update check against Kindling's GitHub releases); tests in `leaf-server/src/test/java/net/kindlingmc/` |
| `leaf-server/src/main/java/org/dreeam/leaf/` | Leaf's own code; Kindling changes defaults and adds config modules there, every line marked `// Kindling - …` |
| `leaf-server/build.gradle.kts.patch` | the name in the jar manifest (`Brand-Name`, `Implementation-Title`) |
| `settings.gradle.kts` | the `kindling-bench` module |
| `kindling-bench/` | the load test |

## Merging a Leaf update

```bash
git remote add upstream https://github.com/Winds-Studio/Leaf.git   # once
git fetch upstream
git merge upstream/ver/26.3
./gradlew applyAllPatches
```

Conflicts can only appear where Leaf changed a line Kindling marks with `Kindling`, or where Leaf added patches with the numbers Kindling uses. In the second case renumber Kindling's patches so they stay last: apply Leaf's patches, re-apply Kindling's commits on top in `leaf-server/src/minecraft/java` (the applied sources are a Git repository with one commit per patch), then run `./gradlew :leaf-server:rebuildMinecraftFeaturePatches`.

Then build and measure before releasing:

```bash
./gradlew createPaperclipJar
./gradlew :kindling-bench:jar --no-configuration-cache
bash kindling-bench/matrix.sh leaf-server/build/libs/leaf-paperclip-26.3.local-SNAPSHOT.jar <prefix> fork
bash kindling-bench/elytra.sh <kindling.jar> <leaf.jar> <prefix>
```

Compare with the numbers in [MEASUREMENTS.md](MEASUREMENTS.md). If Leaf changed something Kindling relies on (the chunk loader for directional loading, the chunk task scheduler for the pregenerator), say so in the release notes.

## Changing a Kindling patch

1. Edit the applied source in `leaf-server/src/minecraft/java` (Minecraft classes) or `paper-server` (Paper classes) and commit there (`git commit --fixup` onto the matching `Kindling:` commit, or a new commit for a new patch). The commit author becomes the `From:` line of the public patch file, so commit with the identity you want published.
2. `./gradlew :leaf-server:rebuildMinecraftFeaturePatches :leaf-server:rebuildPaperServerFeaturePatches` turns the commits back into patch files.
3. Commit the changed `.patch` files in this repository.

Code that does not touch Minecraft classes belongs in `leaf-server/src/main/java/net/kindlingmc/` instead of a patch; a patch should only hook it in.
