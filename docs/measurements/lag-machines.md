# Lag machines

[← Overview](../MEASUREMENTS.md)

What redstone, TNT and minecart lag machines cost, whether farms, item sorters and self-running clocks keep working under them, and what counters them.

## Farms and clocks under load

**Correct.** Under every lag machine, the self-running clocks, hopper chains, item sorters, crafters, composters and brewing stands did exactly as much work as on a calm server; only TNT stopped exploding, because of Spigot's TNT budget.
Kindling 26.3-24, `type=lag`, 8 players × 4 plots, 3 runs each.

The bench's `lag` scenario ([kindling-bench](../../kindling-bench/README.md#the-lag-scenario)) gives every plot three self-running clocks (an observer pair, a piston and observer clock, a hopper clock), the workshop of item sorters and farms, and a TNT chamber, and counts what each of them did. Up to one lag machine per plot runs next to them: an observer storm (observers driving redstone dust over lamps), a TNT cannon chamber or pits crammed with minecarts.

Spigot's `max-tnt-per-tick` (100 by default, in `spigot.yml`) lets only so many primed TNT tick per tick. TNT beyond the budget does not tick at all, so its fuse never runs down: it piles up instead of exploding. With every machine running, 192 instead of 707 TNT exploded in the counted chambers, and 9 831 entities were loaded instead of 1 607. With the budget off (`max-tnt-per-tick: 0`), all 707 exploded, at the same tick time (23.1 against 24.8 ms, not marked).

<details>
<summary>Method and all numbers</summary>

Bench host, `type=lag`, 8 players × 4 plots (32 plots, the lag machine in the fourth chunk of each plot), 60 s warm-up, 90 s measured, 3 runs each. The counters were identical in every run; `!` marks a counter that differed from the calm server.

| Figure | calm | observers | tnt | carts | all | all, TNT budget off |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Tick time avg | 6.7 | 35.0 | 17.6 | 22.3 | 24.8 | 23.1 |
| p95 | 11.3 | 140.2 | 26.9 | 27.9 | 63.5 | 61.9 |
| Main-thread allocation | 5.1 GB | 45.3 GB | 8.4 GB | 26.3 GB | 28.5 GB | 27.6 GB |
| CPU time | 221.6 s | 330.8 s | 275.6 s | 286.0 s | 298.8 s | 288.9 s |
| Entities at the end | 230 | – | 28 992 | 3 449 | 9 831 | 1 607 |
| TNT chamber explosions | 707 | 707 | 64 ! | 707 | 192 ! | 707 |

The other twelve counters were the same in every column: observer pair 19 200, piston and observer clock 14 400 and hopper clock 4 096 extensions; sorter 5 760 passed and 5 766 sorted items, 28 815 dropped items caught, 7 232 items in the double chest and in the chest minecart, 7 200 in the hopper minecart, 2 880 crafted items, 800 bone meal and 256 brews.

</details>

## Redstone wire without allocated positions

**Same** tick time; main-thread allocation 47.3 → 30.3 GB (−36 %) and GC runs 59 → 46 on the observer storm, with identical counters.
Against the same build without the change (`61a86d13`), 3 runs each.

The wire power calculation Kindling ported from Lithium created a new block position for every neighbour it looked at; in the observer storm that was 40 % of the main thread's allocation. Two reused positions now address the neighbours. The blocks read and their order are unchanged, and a differential test compares the result with vanilla's in 4 000 random surroundings. The time goes into the neighbour updates and the light updates of the lamps, which vanilla needs, so the tick time did not move.

<details>
<summary>Method and all numbers</summary>

`type=lag,lag-machines=observers`, 8 players × 4 plots, both jars built from the same tree, 3 runs each:

| Figure | Before | After | p |
| --- | ---: | ---: | ---: |
| Tick time avg | 35.1 ±1.3 | 34.4 ±0.2 | 0.50 |
| p95 | 141.2 ±5.8 | 137.5 ±0.9 | 0.40 |
| Main-thread allocation | 47 330 ±2 972 MB | **30 276 ±98 MB** | 0.013 \* |
| GC runs | 59.0 ±2.0 | **46.3 ±0.5** | 0.005 \* |
| GC pause | 1 019 ms | 977 ms | 0.30 |
| CPU time | 327.9 s | 331.4 s | 0.49 |

A JFR profile of the observer storm put 77 % of the main thread into observer ticks and the neighbour updates they cause, and 11 % of all CPU into Starlight's light propagation.

</details>

## Paper's redstone implementations

**Faster**, opt-in and not vanilla's update order. On the observer storm, Paper's `ALTERNATE_CURRENT` ticked in 11.0 instead of 34.4 ms (p95 137.5 → 26.6 ms, CPU time −21 %), `EIGENCRAFT` in 15.7 ms; every counter stayed the same.
Against the same build with Paper's default `VANILLA`, 3 runs each.

Paper's `misc.redstone-implementation` (in `config/paper-world-defaults.yml` or per world) replaces vanilla's redstone dust with an algorithm that updates each wire once. Both alternatives change the order in which redstone updates reach other blocks, so contraptions that depend on vanilla's order, such as some update detectors and zero-tick builds, can behave differently; the clocks, sorters and farms of the bench all kept working. Kindling keeps `VANILLA` as the default. [Counter lag machines](../guides/lag-machines.md) says how to switch, and [`/kindling doctor`](../reference/commands.md#kindling-doctor) names the setting and this measurement.

<details>
<summary>Method and all numbers</summary>

`type=lag,lag-machines=observers`, 8 players × 4 plots, the build with the wire change, the setting from an overlay `config/paper-world-defaults.yml` (checked in the run folder), 3 runs each:

| Figure | VANILLA | ALTERNATE_CURRENT | EIGENCRAFT |
| --- | ---: | ---: | ---: |
| Tick time avg | 34.4 ±0.2 | **11.0 ±0.8** \* | **15.7 ±0.1** \* |
| p95 | 137.5 ±0.9 | **26.6 ±3.2** \* | **42.2 ±0.3** \* |
| p99 | 142.4 ±0.5 | **34.6 ±5.7** \* | **45.9 ±1.9** \* |
| Main-thread allocation | 30 276 MB | **9 487 MB** \* | **15 029 MB** \* |
| GC pause | 977 ms | **663 ms** \* | **858 ms** \* |
| CPU time | 331.4 s | **262.7 s** \* | **284.0 s** \* |

All 13 counters, including the three self-running clocks, were identical in all nine runs.

</details>

## Minecart lag machines

**Faster**, opt-in. With `minecart-push-limit` on (4 pushes), the minecart pits ticked in 19.0 instead of 22.1 ms (−14 %) and the main thread allocated 22 % less; every counter stayed the same, the minecarts of the workshop included.
Against the same build with the setting off, 3 runs each.

The minecart pits (25 carts each, without rails) cost 57 % of the main thread in a JFR profile: about a quarter goes into finding the entities each cart pushes or collides with, the rest into moving the carts and the blocks they touch. A faster lookup that keeps vanilla's result did not work out: the order of the pushes changes the carts' speeds, so a lookup in another order would change gameplay. The opt-in [`minecart-push-limit`](../reference/configuration.md#minecart-push-limit) lets each cart push at most a few other carts per tick instead, which changes how fast a crowd spreads out; Kindling keeps it off by default.

<details>
<summary>Method and all numbers</summary>

`type=lag,lag-machines=carts`, 8 players × 4 plots (32 plots with four pits each), build `feat/lag-counters`, the setting from an overlay `config/leaf-global.yml` (checked in the run folder), 3 runs each:

| Figure | Off | On, 4 pushes | p |
| --- | ---: | ---: | ---: |
| Tick time avg | 22.1 ±0.5 | **19.0 ±0.2** | 0.006 \* |
| p95 | 27.5 ±1.6 | 23.6 ±0.2 | 0.056 |
| p99 | 34.1 ±3.2 | 29.7 ±2.4 | 0.15 |
| Main-thread allocation | 24 910 ±611 MB | **19 494 ±706 MB** | 0.001 \* |
| GC pause | 759 ms | 787 ms | 0.24 |
| CPU time | 270.5 s | 267.9 s | 0.42 |
| Entities at the end | 3 446 | 3 443 | 0.66 |

All 13 counters were identical in all six runs, including the 7 232 items of the chest minecart and the 7 200 of the hopper minecart.

</details>
