# 🤝 Contributing to Kindling

Thanks for helping. Kindling is small on purpose: it is Leaf plus a few changes that each won a measurement. That shapes what gets merged.

## What fits

| Rule | Meaning |
| --- | --- |
| 🎮 **No gameplay changes by default** | Mob spawning, AI, redstone, world generation and everything else a player can notice behave as in vanilla (and Leaf). An optimisation that changes behaviour can only be an opt-in setting with an honest description. |
| 📏 **Measured, not guessed** | A performance change comes with `kindling-bench` numbers before and after (two runs each, see [kindling-bench/README.md](kindling-bench/README.md)). Only differences larger than the gap between the two runs count. |
| 🍃 **Leaf stays the base** | Fixes for problems plain Leaf has too belong in [Leaf](https://github.com/Winds-Studio/Leaf); Kindling picks them up with the next merge. |
| ✂️ **Small patches** | Code that does not need to live inside a Minecraft class goes into `leaf-server/src/main/java/net/kindlingmc/`; the patch only hooks it in. This keeps merging Leaf updates cheap. |

## Working on the code

Requirements: Java 25, Git (on Windows `git config --global core.longpaths true`).

```bash
./gradlew applyAllPatches        # once after cloning and after every Leaf merge
./gradlew createPaperclipJar
./gradlew :leaf-server:test --tests org.bukkit.support.suite.KindlingTestSuite   # Kindling's tests
```

The applied Minecraft sources in `leaf-server/src/minecraft/java` are a Git repository with one commit per patch. Change them, commit there, then `./gradlew :leaf-server:rebuildMinecraftFeaturePatches` writes the patch files back. Paper's [contributing guide](https://github.com/PaperMC/Paper/blob/main/CONTRIBUTING.md) explains this workflow in depth; [docs/UPSTREAM.md](docs/UPSTREAM.md) has the Kindling specifics.

> [!IMPORTANT]
> Mark every line you change in Leaf or Paper code with `// Kindling - <reason>` (or a `Kindling start` / `Kindling end` pair around a block), so the next Leaf merge shows at a glance what is ours.

Tests for Kindling's own code go into `leaf-server/src/test/java/net/kindlingmc/`. Paper's test task only runs `*TestSuite` classes; `KindlingTestSuite` picks up everything in `net.kindlingmc`.

## Pull requests

- One topic per pull request, with the measurement in the description.
- Commit messages in English, imperative mood (`feat: …`, `fix: …`, `docs: …`).
- The build workflow has to pass.
