# AGENTS.md

Rules for coding agents working on Kindling. What Kindling is and does: [README.md](README.md). Why each change exists: [docs/MEASUREMENTS.md](docs/MEASUREMENTS.md).

> [!NOTE]
> This file is Kindling's own. Leaf ships its own `AGENTS.md`; when a Leaf merge conflicts here, keep this version (the upstream workflow does that by itself).

## Map

| Path | What it is | Edit? |
| --- | --- | --- |
| `leaf-server/src/main/java/net/kindlingmc/` | Kindling's own server code (`pregen`, `chunk`, `version`) | ✅ first choice for new code |
| `leaf-server/src/test/java/net/kindlingmc/` | Kindling's tests (run by `KindlingTestSuite`) | ✅ |
| `leaf-server/src/minecraft/java/` | Applied Minecraft sources; a Git repository with one commit per patch | ✅ through the patch workflow below |
| `paper-server/`, `paper-api/` | Applied Paper sources | ✅ through the patch workflow below |
| `leaf-server/minecraft-patches/features/0316-…`+, `leaf-server/paper-patches/features/0069-…`+ | Kindling's patches (subject `Kindling:`) | only by rebuilding them, never by hand |
| other `*-patches/` | Leaf's patches | ❌ read only |
| `leaf-server/src/main/java/org/dreeam/leaf/` | Leaf's own code | small changes only, each line marked `// Kindling - …` |
| `leaf-archived-patches/` | history, not the current baseline | ❌ read only |
| `kindling-bench/` | the load test ([README](kindling-bench/README.md)) | ✅ |
| `docs/`, `*.md` | documentation, English | ✅ keep it true to the code |
| `website/` | the public site (Vite, React, TypeScript, published by `website.yml`); [README](website/README.md) | ✅ |

## Ground rules

1. **No gameplay changes by default.** Vanilla (and Leaf) behaviour stays: mobs, spawning, AI, redstone, world generation, the full world size, no world border. A behaviour-changing optimisation can only be an opt-in setting with an honest description.
2. **Measured, not guessed.** A performance change needs `kindling-bench` numbers before and after, two runs each. Only differences larger than the gap between two runs of the same build count. Say which numbers are measured and which are expectations.
3. **Leaf stays the base.** Fixes for problems plain Leaf has too belong upstream in Leaf. Keep Leaf's config keys, brand id `winds-studio:leaf` and plugin compatibility.
4. **Small patches.** Logic lives in `net.kindlingmc`; a patch only hooks it into a Minecraft or Paper class. Mark every changed upstream line with `// Kindling - <reason>` or a `// Kindling start - …` / `// Kindling end - …` pair.
5. **Never write world data differently from vanilla.** Code that reads saved data must fall back to the vanilla path for anything unusual; code that writes it is out of scope unless the owner asks.

## Changing a patch

1. Edit the applied source and commit there (in `leaf-server/src/minecraft/java` or `paper-server`), as `git commit --fixup` onto the matching `Kindling:` commit or as a new commit for a new patch.
2. Rebuild: `./gradlew :leaf-server:rebuildMinecraftFeaturePatches :leaf-server:rebuildPaperServerFeaturePatches`.
3. Check the rebuilt patches: the author of the inner commit becomes the public `From:` line, so look at every `From:` line in the changed patch files before committing them. The owner tells you which identity to commit with; never publish an address that has not been published before.
4. Commit the changed `.patch` files in this repository.

Leaf-owned patches are never edited. The exception is a change that is meant for Leaf itself (as with the 5-second TPS patch, Leaf pull request #970): then the patch must match the Leaf pull request exactly.

Details, including renumbering after a Leaf merge: [docs/UPSTREAM.md](docs/UPSTREAM.md).

## Building, testing, measuring

```bash
./gradlew applyAllPatches                     # after cloning and after every Leaf merge
./gradlew createPaperclipJar                  # leaf-server/build/libs/leaf-paperclip-26.3.local-SNAPSHOT.jar
./gradlew :leaf-server:test --tests org.bukkit.support.suite.KindlingTestSuite
```

- Paper's test task only runs `*TestSuite` classes; `KindlingTestSuite` picks up everything in `net.kindlingmc`. Run it after every change to Kindling code.
- `applyAllPatches` resets the applied sources to the patches: commit or rebuild your work first.
- Benchmarks load every core for minutes and leave a full server per run under `kindling-bench/runs/`. Run them only when asked, one at a time, nothing heavy in parallel, and delete the run folders once the results are read.
- Never start a server on a port or address other than the bench's (`127.0.0.1:25999`).

Do not claim that a change compiles, passes tests or is faster unless you ran the check and saw the result.

## Hot paths

Leaf and Kindling code runs per tick, per chunk or per block. On a hot path:

- avoid allocation, boxing, copying and temporary collections, including repeated construction of positions, keys and coordinates;
- prefer explicit loops where the surrounding code uses them; no streams or capturing lambdas there;
- think about sparse, typical and dense worlds, and about how much memory stays retained;
- keep early exits and established fast paths;
- never change observable vanilla or Paper behaviour for performance alone.

## Threading and lifecycle

Do not assume code is safe off the main thread. Before moving work to another thread or weakening a thread check, look at mutable state, tick-thread assumptions, chunk and entity lifecycle (load, unload, removal, shutdown), publication between threads, and what plugins and the Paper API expect. Without a clear safety argument and the owner's request, don't.

## Compatibility

Unless the owner asks for a behaviour change, keep vanilla and Paper API behaviour, plugin compatibility, serialized and persistent formats, world loading and upgrading, config defaults and keys, and API contracts. For API changes, look at both the Paper API source and its server implementation.

## Reviewing

Order of importance: behavioural correctness, vanilla and Paper compatibility, thread safety and lifecycle, hot-path cost, memory retention, API compatibility, diff clarity. Separate confirmed defects from possible risks and optional optimisations.

For each defect or risk, put the attribution directly below its title, with a short reason:

`**Attribution: <label>** — <evidence>`

| Label | Meaning |
| --- | --- |
| `Kindling` | introduced or worsened by a Kindling change |
| `Leaf` | introduced or worsened by Leaf |
| `Upstream` | inherited from Paper or vanilla (compare with `paperCommit` in `gradle.properties`, not with the latest Paper) |
| `Unsupported usage` | caused by usage outside the API contract; name the contract |
| `Unknown` | not established; say which evidence is missing |

Judge severity by impact, independently of where the defect comes from.

## Git

- Look at `git status` before editing and keep unrelated changes untouched; never assume existing changes are yours.
- Stage only your own files, by path. Commit messages in English, imperative mood (`feat: …`, `fix: …`, `perf: …`, `docs: …`).
- No `git push`, `--force`, `reset --hard`, `clean`, `rebase` or history rewriting unless the owner asks for exactly that. Every push to `ver/26.3` that changes more than docs and the bench publishes a release.

## Code graph

When a `.codegraph/` index exists (not checked in), use it for cross-file questions (call paths, overrides, ownership) and treat its answers as hints: verify against the applied source before changing behaviour. Do not rebuild the index unless asked.

## Completion report

At the end of a task, report which files changed, what behaviour changed, the threading, compatibility and performance considerations, what you verified yourself (with the command), and what still needs a manual check.
