# Commands

Kindling adds `/pregen` and `/kindling` (with `doctor` and `stress`) and changes `/version`. Every permission is granted to operators by default.

| Command | Permission |
| --- | --- |
| [`/pregen`](#pregen) | `kindling.command.pregen` |
| [`/kindling doctor`](#kindling-doctor) | `kindling.command.doctor` |
| [`/kindling stress`](#kindling-stress) | `kindling.command.stress` |
| [`/version`](#version) | Paper's |

## pregen

```
/pregen start <world> <radius> [<x> <z>]
/pregen pause <world>
/pregen resume <world>
/pregen cancel <world>
/pregen status
```

| Subcommand | Effect |
| --- | --- |
| `start` | Generates every chunk within `<radius>` blocks. Centres on the world spawn unless both `<x> <z>` are given. Rounds the radius down to whole chunks, cuts it to the world border and says what it started. |
| `pause` | Stops the run and keeps its progress. |
| `resume` | Continues a paused run, also after a restart. |
| `cancel` | Stops the run and discards its progress. |
| `status` | The default without arguments. Shows each run's progress, why it is slow or waiting, and the free disk space against what the rest of the run needs. |

`status` names the reason a run waits:

| Message | Cause |
| --- | --- |
| `throttled: 43 ms average > 40 ms` | The average tick is above `max-mspt`. |
| `throttled: the main thread waited for a CPU 18 % of the last minute > 10 %` | The wait is above `max-main-thread-wait`. |
| `paused` | Paused by `/pregen pause`, or by an error in the run. |
| `paused: the disk would run out` | Finishing would leave less than `min-free-disk-gb`. |

Behaviour:

- A run that throws pauses itself and stays resumable. The server keeps running.
- A world that is unloaded and loaded again resumes its run.
- The state file is written on a thread of its own. At a clean stop it is written with the exact position; a write still running at the stop is waited for up to 10 seconds.
- If an empty server stopped for lack of disk space, the free space is checked every 30 seconds and the server wakes when there is room again.
- `/pregen start` before the first tick (for example from a plugin's `onEnable`) keeps the progress of the other worlds.
- A saved radius above 3 750 000 chunks is dropped with a "state is damaged" warning.

Settings: [`pregeneration`](configuration.md#pregeneration).

## kindling doctor

```
/kindling doctor
```

Reads the machine and the server settings and lists what costs performance or memory, grouped into `WARN`, `INFO` and `OK`. Each finding with something to change names its fix and says whether the advice rests on a Kindling measurement or is general advice. Values the system cannot read (for example the Linux-only values on Windows) are skipped, not guessed. With `doctor.startup-check`, each `WARN` is also logged on the first tick; hints and `OK` lines only show in the command.

| Check | Finding |
| --- | --- |
| Processors and container CPU limit | Too few for the configured load. |
| `-Xmx` | Heap plus about 600 MB must fit into the memory. Advises 3 GB for the default distances. |
| Garbage collector, `-XX:+UseCompactObjectHeaders`, AOT cache | Missing recommended flags. |
| Chunk worker threads | Count against the processors. |
| `MALLOC_ARENA_MAX` or `GLIBC_TUNABLES`, `-XX:TrimNativeHeapInterval`, transparent huge pages | Native memory settings. |
| View and simulation distance | Against the processors. |
| Network compression behind a proxy | A warning when the proxy runs on this machine or its place is unknown, a hint when it runs elsewhere. |
| Free disk space of the worlds | A warning below 5 GiB or 10 %. |
| Kindling's recommended options in `leaf-global.yml` | An older file that keeps other values. |
| Vanilla parity | Ten Leaf and Gale settings an older file may still run at non-vanilla values. One `INFO` names each deviating value and the vanilla one. |

### Live section (Linux)

Answers "is the lag my server or my host?" in plain sentences over the last minute, with the last 15 minutes in brackets:

| Reading | Warning |
| --- | --- |
| How long the main thread waited for a CPU | above 10 % of the time it had work and at least 20 ms per second |
| How long the chunk workers waited for a CPU | |
| CPU time the host took from this virtual machine (steal) | above 5 % |
| Whether a container CPU limit throttled the server | from 1 % of the periods |
| The nice value of a chunk worker, and whether [`worker-priority`](configuration.md#worker-priority) took effect | |
| The [worker governor's](configuration.md#worker-governor) state | |

The readings come from `/proc` and the cgroup files, sampled every 5 seconds by `doctor.host-monitor`. The section needs one minute of samples. On a virtual machine a nearly idle main thread shows 10 to 14 % wait from wake-up latency, but only about 3 ms per second; the doctor names that as wake-up latency instead of warning.

Settings: [`doctor`](configuration.md#doctor).

## kindling stress

```
/kindling stress players <0-100> [stand|fly]
/kindling stress mobs <0-5000>
/kindling stress bases <0-16>
/kindling stress status
/kindling stress stop
```

| Subcommand | Load |
| --- | --- |
| `players` | Fake players spread around the centre. `fly` sends them back and forth over 1 024 blocks, which generates and loads chunks. |
| `mobs` | Farm animals, villagers, spiders and skeletons around the centre. |
| `bases` | Plots of hopper rings, idle hoppers, furnace rows and repeater lines with pistons, observers and lamps. |
| `status` | Shows the current load. |
| `stop` | Ends the test. |

Each order sets a number: `mobs 0` removes the mobs, `players 20 fly` makes all 20 fly.

- **The test world.** The first order creates a fresh world `kindling_stress` and takes you to its centre; creating it takes the main thread a few seconds. The loads ramp up over a few seconds while their chunks load. The world is deleted when the test ends; a world left over by a server stop is deleted before the next test. It stays night without weather, unless Paper's `time.affects-all-worlds` is on: then the test leaves the shared time alone and monsters burn by day. `keep_inventory` is on.
- **The end.** `stop`, 15 minutes, or 20 seconds above 250 ms per tick (both in real time) end the test. The fake players leave, the mobs go, everyone returns to where they were.
- **The boss bar** shows tick time and TPS to every player in the test world.
- **The way back.** Your position before the test is kept in your player data (`kindling:stress_origin`) until you are back, so a logout or a server stop during the test still sends you back on your next join. A player the test cannot send back (dead, riding, held by a plugin) keeps the world loaded; the next test unloads it once it is empty.
- **Fake players** join through the same path as clients and send movement like clients. Only encoding and compressing their packets is skipped. They are not saved (no player data, stats or advancements, except advancements a `/minecraft:reload` during a test writes for every online player), but vanilla notes their names (`kindling_0`, …) in `usercache.json`. While they are online the player limit is raised by their number.
- **Plugins see them.** They join and leave with chat messages, fire join and quit events and show in the tab list, so plugins that store data per player store some for them. A fake player that a plugin moves out of the test world leaves.
- It does not run with Leaf's parallel world ticking.
- It is not a benchmark. To compare builds, use [`kindling-bench`](../../kindling-bench/README.md).

## version

`/version` shows the Kindling build, its commit and date, and checks Kindling's GitHub releases for a newer build. The brand id stays `winds-studio:leaf`, so plugins that check for Leaf keep working.
