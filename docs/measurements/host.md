# The host: waiting for a CPU

[← Overview](../MEASUREMENTS.md)

How long Kindling's threads wait for a CPU on a shared host, what worker priority, the pregenerator's throttle and worker demand do about it, and how the doctor reads it. Settings: [configuration reference](../reference/configuration.md).

## Waiting for a CPU on the VPS

**Indicative.** In 28 minutes the main thread of a production server ran 326 s and waited 92 s in the run queue (+28 % of its running time). One sample, not repeated.

The VPS has 8 vCPUs that the guest sees as 8 sockets with one core each (8 last-level caches), kernel 6.12. Linux ignores Java thread priorities there (HotSpot's default `ThreadPriorityPolicy=0`), so Paper's raised main thread has no edge over the chunk workers, which the Windows benchmarks did not show.

| Reading | Result |
| --- | --- |
| 28 minutes, main thread | ran 326 s, waited 92 s in the run queue (**+28 %**) |
| 28 minutes, Netty | ran 19 s, waited 101 s |
| One 60 s window, main thread | ran 4.68 s, waited 1.30 s |
| The same window, Netty | waited 11.8 ms per wake-up |
| The same window, machine | 33 % idle, steal 0.8 %: the competition is inside the guest |
| Experiment: five chunk workers at nice 10, 45 s alternating with nice 0, twice each | main-thread wait 1 140 and 1 032 ms at nice 0, 945 and 721 ms at nice 10 (**about −25 %**, noisy); Netty wait per wake-up 16.1 and 5.1 ms at nice 0, 15.9 and 29.0 ms at nice 10 (no improvement) |

<details>
<summary>Method and all numbers</summary>

**Method.** Measured on a production server while a pregeneration ran, with the kernel's per-thread scheduler statistics (`/proc/<pid>/task/<tid>/schedstat`). Read-only except for the experiment, and a single sample, not repeated. The experiment set the nice values by hand and reset all 93 threads to 0 afterwards (checked with `ps`). It led to [worker priority](#worker-priority), which the bench host and a second VPS check measured further.

**VPS check of the build with worker priority and the host monitor.** A test server (127.0.0.1, port 25997, `-Xms1G -Xmx1G`, no plugins, view distance 8, seed 1234, a fresh world per run) next to the production servers, with nobody online (0 established connections to the proxy, rechecked every 5 s); the live servers were only read, none logged "Can't keep up". The machine was 85 to 91 % idle, load 0.3 to 1.6, steal 0.05 to 0.09 %. `/pregen start world 2000`, 3 chunk workers, 181 s per run, restart in between, the `max-main-thread-wait` of that build (0.10) active, order off, on, off, on, 2 runs per mode:

| Run | Chunks in 186 s | Main-thread wait | Chunk worker CPU | Tick time (1 min avg / max) |
| --- | ---: | ---: | ---: | ---: |
| 1, nice 0 | 1 188 | 11.5 % | 45 s | 1.2 / 12.0 ms |
| 1, nice 10 | 1 842 | 12.7 % | 72 s | 0.9 / 28.6 ms |
| 2, nice 0 | 753 | 13.8 % | 27 s | 1.0 / 31.4 ms |
| 2, nice 10 | 3 017 | 10.9 % | 99 s | 1.6 / 27.7 ms |

Worker priority had **no measurable effect** on the main thread here: the wait was 11 to 14 % in both modes and the ticks were about 1 ms. Nice 10 showed more chunks (13.1 against 5.2 chunks/s on average) and more worker CPU, but with two runs per mode and a fourfold spread inside the nice-10 pair (9.9 and 16.2 chunks/s) that is not significant. The machine never ran short of CPUs, which is when a lower priority matters. Side finding: a nearly idle main thread (3 to 6 s of CPU in 181 s) already waited 11 to 14 % of its time at nice 0 and nice 10 alike, on the idle a production server too (5.2 % over 20 s, about 10 µs per scheduler slice). On this VM that is wake-up latency, not contention, and it made the [pregenerator's wait throttle](#pregenerator-wait-throttle) (limit 0.10) fire at baseline in 4 to 6 of 8 status lines per run, while the four runs made only 4.0 to 16.2 chunks/s.

The doctor's `LIVE` section agreed with `/proc` seen from outside on an idle test server (nice 0 and steal 0.0 against 0.05 %; main-thread wait 5.4, 3.6 and 4.6 % against 2.5 % over a different window of a nearly idle thread). Its warning text ("other threads or processes on this machine compete with it") was misleading on a VM like this one, where a lightly loaded thread shows 10 % wait without any competitor. The warning also needs at least 20 ms of waiting per second: the idle VPS thread waited about 3 ms per second, the loaded bench host 25 to 50 ms per second (mixed flight, worker priority on and off).

</details>

## Worker priority

**Faster.** Elytra flight tick time avg 22.5 → 21.2 ms and p95 43.9 → 37.9 ms (both marked), main-thread wait −59 %, new chunks per second and time over void unchanged. Against the same build with it off, 3 runs each, bench host.

`performance.worker-priority` lets the chunk and I/O worker threads lower their own priority when they start (nice 10 by default; on Windows and macOS the Java thread priority instead). It is on by default. HotSpot ignores Java thread priorities on Linux, so without it the chunk workers compete with the main thread on equal terms. A priority is never raised, and the setting applies to threads started after it is read, so restart the server after changing it.

Flight over a pregenerated world: p95 38.3 → 37.1 ms (marked), average unchanged. Bases unchanged. The [worker governor](#worker-governor), measured in the same series, stays off.

<details>
<summary>Method and all numbers</summary>

**Method.** The build with five chunk workers and the density pruning, the scenarios of the [campaign against Leaf](vs-leaf.md), worker priority off against on, 3 runs each, on the Linux (WSL2) bench host.

**Series: the build that introduced the setting** (nice 10 for the chunk and I/O workers; pregeneration throttle off). Linux (WSL2) bench host, 8 CPUs, the `-XX:ActiveProcessorCount=8` flags of `pregen.sh`, three runs per variant, interleaved with alternating order, 36 flight and pregeneration runs and 6 mixed runs, none failed. The main thread's wait is `main_wait_ms` (run-queue time from `schedstat`, see [the bench README](../../kindling-bench/README.md)).

| Scenario | Figure | Off | On | p |
| --- | --- | ---: | ---: | ---: |
| Elytra flight, 5 players at 1.7 blocks/tick | Main-thread wait | 5 430 ±460 ms | **2 673 ±345 ms** | 0.002 \* |
| | Tick time avg / p95 | 15.9 ±1.2 / 52.0 ±6.7 | 14.3 ±0.4 / 46.5 ±5.6 | 0.17 / 0.38 |
| | Time in the void, lead | 52.3 ±4.7 %, 1.4 | 49.1 ±5.3 %, 1.4 | 0.50 |
| | New chunks per second | 85.6 | 86.7 | 0.55 |
| Pregeneration alone, one player standing, 240 s | Main-thread wait | 2 867 ±250 ms | **1 732 ±61 ms** | 0.012 \* |
| | Tick time avg / p95 | 6.2 / 11.2 ±1.0 | 5.9 / 10.1 ±0.4 | 0.24 / 0.20 |
| | Chunks per second | 140.3 | 142.4 | 0.17 |
| Pregeneration while 5 players fly, 90 s | Main-thread wait | 4 380 ±314 ms | **2 307 ±126 ms** | 0.004 \* |
| | Tick time avg / p95 | 23.1 ±0.7 / 65.9 ±3.6 | 21.5 ±0.7 / 57.7 ±1.0 | 0.049 \* / 0.050 |
| | Time in the void, lead | 43.1 ±8.8 %, 3.0 | 42.2 ±2.5 %, 2.9 | 0.88 / 0.94 |
| Mixed load, 8 survival players | Main-thread wait (flight) | 4 512 ±48 ms | **2 322 ±187 ms** | 0.001 \* |
| | Flight tick time avg / p95 | 27.3 ±0.6 / 64.4 ±0.5 | **24.8 ±0.2 / 57.6 ±1.2** | 0.010 \* / 0.005 \* |
| | Standing tick time avg / p95 | 36.7 ±0.6 / 65.7 ±3.5 | 37.4 ±1.0 / 68.4 ±4.7 | 0.40 / 0.50 |
| | Time in the void, lead | 3.1 ±1.9 %, 3.5 | 6.3 ±0.8 %, 3.3 | 0.10 / 0.09 |

The main thread's wait roughly halves in every scenario, significantly. Tick times improve in the mixed flight and, at the edge of significance, with the pregenerator beside the flight; nothing gets significantly worse.

</details>

<details>
<summary>Caveats</summary>

- In the mixed load of this series the time in the void rose from 3.1 to 6.3 % (p = 0.10, not significant) with a slightly lower lead. More void means less to load, tick and send, so part of the better flight tick time can come from that.
- The standing ticks after the pregeneration runs (a 5 s window) and after the elytra flight (p95 28.5 against 38.6 ms, p = 0.08) are too short and noisy to read.
- The runs say that nice 10 does not cost pregeneration speed or lead here. They were made on the bench host; the [VPS check](#waiting-for-a-cpu-on-the-vps) saw no effect on a machine that was 85 to 90 % idle.

</details>

## Pregenerator wait throttle

**Slower.** With `performance.pregeneration.max-main-thread-wait` at 0.10, pregeneration alone makes 65 chunks/s instead of 140.3 (−55 %), though the tick times fall. Against the throttle off, 3 runs each, bench host. Opt-in; the default is 0 (off).

The setting makes the pregenerator throttle while the main thread waited for a CPU longer than that share of the last minute. At 0.10 it stops the pregenerator for the whole flight, halves the lead and cuts pregeneration alone by more than a third without worker priority. It stays off because a share of the last minute is a poor criterion on a VM: the main thread of an idle test server already sits above 10 % there ([VPS check](#waiting-for-a-cpu-on-the-vps)).

<details>
<summary>Method and all numbers</summary>

**Method.** The build that introduced the setting, the same bench host and runs as the series of [worker priority](#worker-priority), limit 0.10 against the limit off, 3 runs each.

| Scenario | Figure | Limit off | Limit 0.10 | With priority on, limit 0.10 |
| --- | --- | ---: | ---: | ---: |
| Pregeneration alone | Chunks per second | 140.3 | **65** (47, 76, 72; p = 0.013 \*, −55 %) | **113.7** (−20 % against priority on, p < 0.001 \*) |
| | Tick time p95 | 11.2 | 9.7 | 10.4 (none significant) |
| Pregeneration while 5 players fly | Tick time p95 | 65.9 | 48.1 (p = 0.004 \*) | 41.3 (against off, p = 0.001 \*) |
| | Lead avg / worst ray | 3.0 / 2.9 | **1.6 / 1.4** (p = 0.002 \*) | 1.6 (p = 0.001 \*) |
| | Time in the void | 43.1 ±8.8 % | 49.1 % (p = 0.36) | not reported |
| | Pregeneration progress | 12 547, 13 254, 13 067 | **2, 3, 3** | 7, 8, 5 |

</details>

<details>
<summary>Caveats</summary>

- A limit such as 0.20 was not tested.
- The throttle's switching on and off could not be observed, because the run does not log the progress over time.
- Judging the absolute wait per tick, or only a busy machine, is an open item on the [roadmap](../ROADMAP.md).

</details>

## Worker demand

**Faster.** Flight over a pregenerated world: tick time 35.6 → 33.3 ms, p95 54.3 → 50.5 ms, main-thread wait 709 → 311 ms, CPU time 513 → 477 s. Elytra, mixed flight and bases show no marked difference. Against the same build with it off (five fixed workers), 3 runs each. On by default.

**Slower than plain Leaf.** Against plain Leaf `66f99f8e` the pregenerated flight still ticks 5 % slower (31.7 → 33.3 ms), at the same CPU time (462 → 477 s, p = 0.085). The bases keep 13 % more CPU time (276 → 313 s). Same series, 3 runs each.

`performance.worker-demand` runs every configured chunk worker only while new terrain waits for them. A profile of the pregenerated flight with the worker count as the variable (3 runs each, against Leaf) had put both losses of the [campaign against Leaf](vs-leaf.md) on the five workers: with Paper's two the flight ticked like Leaf (33.3 against 32.6 ms, p = 0.15) and the bases used Leaf's CPU time (281.5 against 280.0 s), with five it was 36.1 ms and 334 s. Five workers on four cores generate new terrain 3.6 times as fast, but where little is generated they only crowd the main thread.

Every second the server reads how many generation steps waited for a worker on average (patch `0345` adds up the time each step of a chunk below its target status spends queued; by Little's law that sum over a second is the average queue length). Above 16 waiting steps every worker runs at once; after `quiet-seconds` (10) below 8 the count falls back to Paper's default for the machine, a quarter of the processors. Loading saved chunks does not count.

<details>
<summary>Method and all numbers</summary>

**The thresholds.** A diagnostic build logged the average every second (1 run each): over a pregenerated world the median was 0.4 waiting steps, the 90th percentile 2.4 and the maximum 15; while flying over new terrain it was 90 to 240 (130 of 144 samples above 16); the mixed flight switched between 0 and full backlog. A first version (all workers above 2, back after 10 s below 1) kept five workers for 101 of 131 seconds of the pregenerated flight and measured the same as five fixed workers (36.6 against 36.8 ms, 3 runs).

**The A/B.** The scenarios of the [campaign against Leaf](vs-leaf.md), worker priority on, `-Xms3G -Xmx3G`, 8 CPUs; a pregenerated world made once with five workers and copied in for every loaded run; 3 rounds in rotating order:

| Scenario | Row | Leaf `66f99f8e` | Five fixed workers | Worker demand |
| --- | --- | --- | --- | --- |
| Pregenerated flight | Tick time avg / p95 (ms) | **31.7 / 43.5** | 35.6 \* / 54.3 \* | 33.3 \* / 50.5 \* |
| | Main-thread wait (ms) | **149** | 709 \* | 311 \* |
| | CPU time (s) | **462** | 513 \* | 477 (p = 0.085) |
| | Chunk packets in the flight | 22 547 | 24 498 \* | 23 777 \* |
| Bases | Tick time avg (ms) | 13.4 | 12.0 \* | **11.5** \* |
| | CPU time (s) | **276** | 324 \* | 313 \* |
| Elytra | Tick time avg (ms) | | 20.3 | 20.4 (p = 0.83) |
| | New chunks per second | | 126.5 | 126.2 (p = 0.73) |
| | Time over void | | 15.6 % | 11.7 % (p = 0.23) |
| Mixed flight | Tick time avg (ms) | | 30.9 | 33.4 (p = 0.32) |
| | New chunks per second | | 129.2 | 137.8 (p = 0.19) |
| | CPU time (s) | | 1 283 | 1 266 (p = 0.24) |

\* marked against Leaf. Against five fixed workers, worker demand is marked in the pregenerated tick time, p95, main-thread wait, chunk packets and CPU time, and nowhere else.

</details>

<details>
<summary>Caveats</summary>

- The mixed flight ticked slower with worker demand in all three rounds (33.4 against 30.9 ms) while it generated 7 % more chunks per second; not significant, because the fixed-worker runs spread ±2.9 ms. Watch it in the next comparison.
- The bases' extra CPU time against Leaf did not move (313 against 324 s, p = 0.09). They generate only at the start, so the cause is likely the spawn area being generated with every worker; not established.
- The thresholds come from one host with five players at 1.7 blocks per tick. A host with more cores or more players may see a different queue over generated terrain; 16 is about three times what the pregenerated flight ever reached.
- No effect while `performance.worker-governor` is on; a Paper config reload sets the configured count again until the next sample.

</details>

## Worker governor

**Indicative.** With the governor on, the elytra flight tick time was 16.9 ms but the chunk rate fell by 11 %, so it stays off. One series next to the worker priority measurement, with the scenarios of the [campaign against Leaf](vs-leaf.md); the governor variant's own run count, its other figures and its p-values are not recorded.
Opt-in (`performance.worker-governor.enabled`, default `false`, Linux, needs `doctor.host-monitor`); never run on a live pool. The decision logic is covered by unit tests (`GovernorStateTest` 14, `HostPressureTest` 8, `ThreadChecksTest`, `ChunkWorkersTest`).

After every 5-second host sample the pressure on the main thread is **high** (it waited for a CPU above the doctor's contention threshold, or the container throttled at least 1 % of its periods), **low** (wait share at most 5 % or under 10 ms per second, throttling under 0.5 %, steal at most 5 %), **unknown** (no readings) or **moderate** (anything else; steal above 5 % only blocks steps up). Two high samples in a row remove one chunk worker; `quiet-seconds` (default 60) of low samples add one back; a worker added and removed again within one quiet period doubles the wait, up to 8 times. Bounds: a quarter of the available processors (at least 1, never above the ceiling) up to the configured or measured default. It calls `adjustThreadCount` of the Moonrise pool, which halts the newest workers after their current task (no chunk task is lost) and starts new ones through the same thread factory, so worker priority applies to them. A Paper config reload sets the configured count again until the next sample. The doctor's LIVE section shows the current count, its bounds and the latest reading.

Expected, not measured: less main-thread wait on a crowded shared host, in exchange for slower chunk generation. Open: whether 2 samples down and 60 s up are good defaults, a `/paper reload` while it holds a lower count, and the effect on pregeneration and on flights beyond the one series above. Decisions use single 5-second samples, not the doctor's one-minute window.

## The doctor's live readings

**Correct.** The doctor's readings agree with `/proc` read from outside within about half a percentage point. Against the same counters read from outside, 5 windows of 60 s, bench host.

On Linux `/kindling doctor` reads the main thread's run-queue wait, the chunk workers' wait and the host's steal from `/proc` in a background thread.

| Window | Main-thread wait, doctor / outside | Chunk-worker wait | Steal |
| --- | ---: | ---: | ---: |
| Idle, a | 0.0 % / 0.00 % | not shown | 0.0 % / 0.00 % |
| Idle, b | 0.1 % / 0.16 % | not shown | 0.0 % / 0.00 % |
| Pregeneration, a | 12 % / 12.02 % | 0.1 % / 0.11 % | 0.0 % / 0.00 % |
| Pregeneration, b | 13 % / 12.87 % | 0.2 % / 0.16 % | 0.0 % / 0.00 % |
| Pregeneration, c (the cancel command was malformed, so the run kept going) | 4.8 % / 5.11 % | 0.1 % / 0.06 % | 0.0 % / 0.00 % |

<details>
<summary>Method and all numbers</summary>

**Method.** Check on the bench host, server idle and then pregenerating (`/pregen start world 3000`, about 135 to 143 chunks/s), each window 60 s, the doctor run right after the window against the same counters read from outside (`schedstat` per thread, the first line of `/proc/stat`).

The readings agree within about half a percentage point; the machine was 99 % idle in the idle windows and 32 to 34 % idle with the pregenerator (62 % user, 6 % system). At 12 to 13 % the doctor gave its warning about contention; at 4.8 % it said contention is not what slows the server down. The doctor prints no machine idle share (only the main thread, the workers, steal and the container limit). On the VPS the same readings matched too ([VPS check](#waiting-for-a-cpu-on-the-vps)), with the caveat that a nearly idle thread on a VM shows 10 % wait without competition.

Other lines checked in a second short run (world on a 1 GiB tmpfs, an older `leaf-global.yml` with async pathfinding and sleeping block entities off, then `host-monitor: false`): disk space was an `INFO` at 936.7 GiB free and a `WARN` at 832 MiB of 1.0 GiB (also logged at start-up); the older file produced the hint that Kindling's defaults apply only to a fresh file and named both options; with `host-monitor: false` the `LIVE` section is absent; the pregenerator paused itself for lack of disk and `/pregen status` showed the `disk:` line.

</details>

<details>
<summary>Caveats</summary>

- The windows differ slightly (the doctor's last 60 s against a window read by hand), so this is a check for agreement, not a precision test.
- The advice for a direct class path start (AOT cache) was not exercised, because the server started with `java -jar`.

</details>

---

[← Overview](../MEASUREMENTS.md)
