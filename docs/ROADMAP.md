# Kindling roadmap

What Kindling works on next. Kindling has one maintainer, so an item stays on this list only if its value clearly outweighs the upkeep it adds on every Leaf merge and Minecraft update (scored 1–10 in an independent review on 2026-10-27; 7.5 or more stays). [MEASUREMENTS.md](MEASUREMENTS.md) records what was measured; each finished item moves there.

## Rules for every item

1. **Vanilla-identical by default** ([AGENTS.md](../AGENTS.md), ground rule 1).
2. **Measured before it becomes a default:** `kindling-bench` before and after, two runs each, difference larger than the run-to-run gap.
3. **No new patch where a setting, a plugin or upstream does the job.** Every Kindling patch is rebased on every Leaf merge.
4. **Bugs go upstream, features stay.** A fix for something that is broken in plain Leaf too goes to Leaf as a pull request (like Leaf#970). Kindling's own features stay in Kindling.

## Next

Nothing open; new items need the 7.5 bar of the review.

## Done (2026-10-27)

| Item | Score | Result |
| --- | --- | --- |
| Pregenerator resume fix | 9.5 | State written atomically and durably; a damaged file is kept as `.corrupt` and only the affected worlds start over |
| Leaf's synchronous options one by one | 8.5 | Measured; none is both faster and the same behaviour, so no new default ([MEASUREMENTS](MEASUREMENTS.md#leafs-options-one-by-one)) |
| `/kindling doctor` and start-up check | 8.0 | Command plus start-up warnings, setting `performance.doctor.startup-check` |
| Fast fliers on modest hardware | — | Paper's automatic limit of concurrent generations per player capped at 32: time in the void on the 8-thread bench host 38.5 % → 0.9 % ([MEASUREMENTS](MEASUREMENTS.md#concurrent-generations-per-player)) |
| Parity CI | 7.8 | `parity.yml` and the weekly Leaf merge run the bases scenario and compare 15 counters with `kindling-bench/parity/bases-ci.json`; identical on Windows and Linux |

## Reviewed and dropped

Recorded so they are not proposed again without new facts. Scores from the 2026-10-27 review; DivineMC checked at `219de55` (`ver/26.3`).

**Already in Leaf:** Lithium equipment tracking (Leaf 0229), sheep (0054), in-wall/suffocation checks (0018), BFS slope search for fluids (0181), parallel world ticking, the multithreaded tracker, Secure Seed, DAB, Anti-Xray, `B_LINEAR` region files.

**Already in vanilla 26.3:** a density function compiler of its own (`levelgen/densityfunction/DensityFunctionCompiler`: rewrite rules, cache reuse, buffered samplers). This is why C2ME's compiler and noise optimisations no longer apply, and why DivineMC dropped them for 26.3.

**Dead code on a Paper server:** C2ME NBT cache limit (Paper's chunk system bypasses `IOWorker`'s write queue; the patch also has a `Math.min(1, …)` bug), ModernFix compact bit storage (only on the client's packet read path).

**Not vanilla-identical, or not worth the upkeep:**

| Item | Score | Reason |
| --- | --- | --- |
| C2ME aquifer (DivineMC 0047) | 4.5 | aquifers are 7 % of chunk worker time, so a realistic gain is below the bench's noise; rewrites the core `Aquifer` method; not bit-exact (literal `1.2` vs vanilla's `Mth.map` result) |
| C2ME world gen math (0009), End biome cache (0054) | 1.0 / 2.5 | one hoisted `Math.min`; the End cache ignores Y |
| StructureLayoutOptimizer (0028) | 4.0 | large, jigsaw assembly does not show in the pregen profile; mainly helps modpacks |
| Native math (0066) | 1.5 | not seed-identical (DivineMC's own warning), native binaries in CI |
| Optimise entities, fluids, raids, Moonrise (0013, 0027, 0025, 0058) | 1.5–3.5 | change ordering or timing, or collide with Leaf's own patches |
| Command block parse cache, async `/locate`, chunk lookups (0033, 0010, 0022) | 2.0 | stale command source; `/locate` returns 0; negligible gain |
| Player profile result cache (0034) | 0.5 | caches Mojang's `hasJoinedServer` by name only, so a cached name logs in without authentication |
| Portal search prefetch, chunk worker presets (Paper 0019) | 4.5 / 2.0 | no measured hitch; the presets give about 2 workers on a 2 GB heap, already measured as too few |
| Async chunk sending (0032) | 3.0 | skipped whenever Anti-Xray modifies the chunk; packet building is not in the measured profile |
| Parallel sensors (0067), parallel entity tracker (0038) | 2.5 / 2.0 | sensors see the world before other mobs move; the tracker duplicates Leaf's |
| Regionized chunk ticking (0045 + Paper 0020) | 1.0 | 20 core classes plus a 33 KB scheduler, redstone and block events in parallel; not maintainable by one person |
| Behaviour options: entity culling, lag compensation, update-suppressor catch, XP clumping, 1-gt delays, MC-73719, spawn tracking range, spectator packet, leaf decay | 1.0–5.5 | plugin-type features or already covered by Spigot settings |
| Lag spike recorder, `/kindling hotspots`, metrics endpoint | 6.5 / 6.0 / 5.0 | spark and exporter plugins already cover them; hotspots needs hooks in hot, heavily patched tick loops |
| AOT from the jar, profiles, join prefetch | 6.0 / 5.0 / 4.0 | a documented start command covers AOT; profiles write keys across six config systems; join prefetch is unmeasured and collides with Leaf |
| Sakura explosions | 2.0 | no TNT-heavy server to measure or justify it |
| Slime world format | — | very large port, own world format; networks that need it use AdvancedSlimePaper |
