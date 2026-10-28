# 🧵 Async defaults

[← Overview](../MEASUREMENTS.md)

Leaf's async entity tracker, pathfinding, player data saving and mob spawning are the subject here. Since the vanilla-defaults change, only the tracker and the player data saving are on in a fresh Kindling configuration; async pathfinding and async mob spawning are opt-in. This page measures what the fixes that make them act as vanilla cost, and the figures below were taken with pathfinding switched on (the default at the time); nothing was measured with the new defaults.

**Contents**

- [Async pathfinding, tracker and saving after the fixes](#async-pathfinding-tracker-and-saving-after-the-fixes)

---

## Async pathfinding, tracker and saving after the fixes

> [!NOTE]
> 🛡️ **Villager parity: 5 and 3 shared beds on release 26.3-11, 0 with the fix; standing tick time 37.0 → 36.8 ms (p = 0.77), so no cost is measurable** · against release 26.3-11 (villagers) and the previous build (cost) · 1 run per side, then 3 runs each · 2026-10-28

Leaf's async entity tracker, async pathfinding, async player data saving and async mob spawning were on by default in Kindling when this was measured (pathfinding and mob spawning are now off in a fresh configuration). Plain Leaf lets them differ from vanilla in places; Kindling fixed those: a villager takes a bed or job site only if it really got it, partial paths and the brain's path are kept, mob costs are read on the main thread; a pending player save is never discarded, `save-all flush` and the stop wait for the writes, and a failed encode writes nothing; a failed async mob count no longer stops spawning (Leaf pull request #937); the tracker's sync flags are not lost.

| Build | `shared_beds`, on | `shared_beds`, off | `shared_job_sites` | `bed_claims`, `job_site_claims` |
| --- | ---: | ---: | ---: | ---: |
| Release 26.3-11, round 1 | **5** | 0 | 0 | 48, 48 |
| Release 26.3-11, round 2 | **3** | 0 | 0 | 48, 48 |
| Build with the fix | 0 | 0 | 0 | 48, 48 in both |

<details>
<summary><b>Method</b></summary>

Kindling's patches `0321` (async mob spawning stall, mirrors Leaf pull request #937), `0323` (async player data saving), `0324` and Paper `0071` (async pathfinding and tracker) make the async defaults act as vanilla where they did not.

**Villager claims.** `type=villagers` builds walled halls with twelve villagers, twelve beds and twelve composters each; vanilla takes a place in the tick it chooses it, so no place is held twice. One run per side, async pathfinding on against off, bench host:

</details>

<details>
<summary><b>All numbers</b></summary>

The fix held in the later builds as well (the Lithium build, the last build). Only beds showed the bug; shared job sites were 0 everywhere on the release, so that half of the bug is documented but not reproduced. The number of shared beds varies between runs.

**Cost of the fixes.** Mixed load, survival mode, 8 players, no overlay, the previous build against the async-path build, 3 runs each, interleaved:

| Figure | Previous | With the fix | p |
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
<summary><b>Caveats and open points</b></summary>

Their speed against plain Leaf is not measured on its own: the [comparison with plain Leaf](vs-leaf.md#final-round-the-build-of-2026-10-28-against-263-11-and-plain-leaf) also changes the chunk defaults, and plain Leaf was still generating while standing (an older two-run figure of −13 % standing does not meet the three-run rule).

**Not tested on a running server:** async player data saving. Its new write ordering, flush barrier and shutdown drain have unit tests (`SerialWrites`), but `save-all flush` followed by a look at the file times, and a stop on a slow disk, were not run. The tracker's flag race has no unit test (`Entity` cannot be loaded without registries). The bench's fake players are creative by default, so mobs never path to them; the villager hall is the only scenario that exercises async pathfinding hard.

</details>

---

[← Overview](../MEASUREMENTS.md)
