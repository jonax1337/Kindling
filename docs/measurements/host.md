# 🖥️ The host: waiting for a CPU

[← Overview](../MEASUREMENTS.md)

How long Kindling's threads wait for a CPU on a shared host, what worker priority and the pregenerator's throttle do about it, and how the doctor reads it.

**Contents**

- [Waiting for a CPU on the VPS](#waiting-for-a-cpu-on-the-vps)
- [Worker priority and the main thread's wait for a CPU](#worker-priority-and-the-main-threads-wait-for-a-cpu)
- [The doctor's live readings](#the-doctors-live-readings)

---

## Waiting for a CPU on the VPS

> [!NOTE]
> 🧪 **In 28 minutes the main thread ran 326 s and waited 92 s in the run queue (+28 %) on the live smp-1** · against its own running time · one sample, not repeated · 2026-10-27

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
<summary><b>Method</b></summary>

Measured on the live smp-1 while a pregeneration ran, with the kernel's per-thread scheduler statistics (`/proc/<pid>/task/<tid>/schedstat`), 2026-10-27; read-only except for the experiment below, and a single sample, not repeated:

The experiment set the nice values by hand and reset all 93 threads to 0 afterwards (checked with `ps`). It led to [`worker-priority`](#worker-priority-and-the-main-threads-wait-for-a-cpu), which the bench host and a second VPS check measured further.

</details>

<details>
<summary><b>All numbers</b></summary>

**VPS check of the build with worker priority and the host monitor (2026-10-28).** A test server (127.0.0.1, port 25997, `-Xms1G -Xmx1G`, no plugins, view distance 8, seed 1234, a fresh world per run) next to the production servers, with nobody online (0 established connections to the proxy, rechecked every 5 s); the live servers were only read, none logged "Can't keep up". The machine was 85 to 91 % idle, load 0.3 to 1.6, steal 0.05 to 0.09 %. `/pregen start world 2000`, 3 chunk workers, 181 s per run, restart in between, the default `max-main-thread-wait` (then 0.10) active, order off, on, off, on:

| Run | Chunks in 186 s | Main-thread wait | Chunk worker CPU | Tick time (1 min avg / max) |
| --- | ---: | ---: | ---: | ---: |
| 1, nice 0 | 1 188 | 11.5 % | 45 s | 1.2 / 12.0 ms |
| 1, nice 10 | 1 842 | 12.7 % | 72 s | 0.9 / 28.6 ms |
| 2, nice 0 | 753 | 13.8 % | 27 s | 1.0 / 31.4 ms |
| 2, nice 10 | 3 017 | 10.9 % | 99 s | 1.6 / 27.7 ms |

Worker priority had **no measurable effect** on the main thread here: the wait was 11 to 14 % in both modes and the ticks were about 1 ms. Nice 10 showed more chunks (13.1 against 5.2 chunks/s on average) and more worker CPU, but with two runs per mode and a fourfold spread inside the nice-10 pair (9.9 and 16.2 chunks/s) that is not significant. The machine never ran short of CPUs, which is when a lower priority matters. Side finding: a nearly idle main thread (3 to 6 s of CPU in 181 s) already waited 11 to 14 % of its time at nice 0 and nice 10 alike, on the idle live smp-1 too (5.2 % over 20 s, about 10 µs per scheduler slice). On this VM that is wake-up latency, not contention, and it made the pregenerator's wait throttle (limit 0.10) fire at baseline in 4 to 6 of 8 status lines per run, while the four runs made only 4.0 to 16.2 chunks/s. That is why the throttle is off by default.

The doctor's `LIVE` section agreed with `/proc` seen from outside on an idle test server (nice 0 and steal 0.0 against 0.05 %; main-thread wait 5.4, 3.6 and 4.6 % against 2.5 % over a different window of a nearly idle thread). Its warning text ("other threads or processes on this machine compete with it") was misleading on a VM like this one, where a lightly loaded thread shows 10 % wait without any competitor. The warning now also needs at least 20 ms of waiting per second: the idle VPS thread waited about 3 ms per second, the loaded bench host 25 to 50 ms per second (mixed flight, worker priority on and off).

</details>

## Worker priority and the main thread's wait for a CPU

> [!NOTE]
> ⏸️ **Main-thread wait roughly halves (elytra flight 5 430 → 2 673 ms, p = 0.002); the pregenerator's wait throttle at 0.10 cuts pregeneration alone by 55 %** · both settings off by default · against the settings off · 3 runs each · 2026-10-28

`performance.worker-priority` lets the chunk and I/O worker threads lower their own priority when they start (nice 10 by default; on Windows and macOS the Java thread priority instead), and `performance.pregeneration.max-main-thread-wait` makes the pregenerator throttle while the main thread waited for a CPU longer than that share of the last minute. Both are off by default. HotSpot ignores Java thread priorities on Linux, so there the chunk workers compete with the main thread on equal terms. A priority is never raised, and the setting applies to threads started after it is read, so restart the server after changing it.

**Worker priority off against on** (nice 10 for the chunk and I/O workers; pregeneration throttle off):

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

<details>
<summary><b>Method</b></summary>

Two opt-in settings come out of the finding that on Linux the chunk workers compete with the main thread on equal terms ([VPS facts](#waiting-for-a-cpu-on-the-vps)): `performance.worker-priority` (chunk and I/O worker threads lower their own priority, nice 10) and `performance.pregeneration.max-main-thread-wait` (the pregenerator throttles while the main thread waited for a CPU longer than that share of the last minute). Both are off by default; this is what the bench host showed.

Method: Linux (WSL2) bench host, 8 CPUs, `-XX:ActiveProcessorCount=8` flags of `pregen.sh`, the build that introduced the settings, three runs per variant, interleaved with alternating order, 36 flight and pregeneration runs and 6 mixed runs, none failed. The main thread's wait is `main_wait_ms` (run-queue time from `schedstat`, see [the bench README](../../kindling-bench/README.md#more-figures-per-run)).

</details>

<details>
<summary><b>All numbers</b></summary>

The main thread's wait roughly halves in every scenario, significantly. Tick times improve in the mixed flight and, at the edge of significance, with the pregenerator beside the flight; nothing gets significantly worse.

**Pregenerator throttle by the main thread's wait** (limit 0.10):

| Scenario | Figure | Limit off | Limit 0.10 | With priority on, limit 0.10 |
| --- | --- | ---: | ---: | ---: |
| Pregeneration alone | Chunks per second | 140.3 | **65** (47, 76, 72; p = 0.013 \*, −55 %) | **113.7** (−20 % against priority on, p < 0.001 \*) |
| | Tick time p95 | 11.2 | 9.7 | 10.4 (none significant) |
| Pregeneration while 5 players fly | Tick time p95 | 65.9 | 48.1 (p = 0.004 \*) | 41.3 (against off, p = 0.001 \*) |
| | Lead avg / worst ray | 3.0 / 2.9 | **1.6 / 1.4** (p = 0.002 \*) | 1.6 (p = 0.001 \*) |
| | Time in the void | 43.1 ±8.8 % | 49.1 % (p = 0.36) | not reported |
| | Pregeneration progress | 12 547, 13 254, 13 067 | **2, 3, 3** | 7, 8, 5 |

The throttle lowers the tick times, but at 0.10 it stops the pregenerator for the whole flight, halves the lead and cuts pregeneration alone by more than a third without priority. That is why the default is 0 (off).

</details>

<details>
<summary><b>Caveats and open points</b></summary>

Two cautions: in the mixed load the time in the void rose from 3.1 to 6.3 % (p = 0.10, not significant) with a slightly lower lead, and more void means less to load, tick and send, so part of the better flight tick time can come from that; and the standing ticks after the pregeneration runs (a 5 s window) and after the elytra flight (p95 28.5 against 38.6 ms, p = 0.08) are too short and noisy to read. The runs say that nice 10 does not cost pregeneration speed or lead here; they were made on the bench host, and the [VPS check](#waiting-for-a-cpu-on-the-vps) saw no effect on a machine that was 85 to 90 % idle. The default stays off until a run under real load on the VPS confirms it.

A limit such as 0.20 was not tested; the throttle's switching on and off could not be observed because the run does not log the progress over time. On the VPS the main thread of an idle test server already sat above 10 % ([see above](#waiting-for-a-cpu-on-the-vps)), so a share of the last minute is a poor criterion there; judging the absolute wait per tick, or only a busy machine, is an open item on the [roadmap](../ROADMAP.md).

</details>

## The doctor's live readings

> [!NOTE]
> 🛡️ **The doctor's readings agree with `/proc` read from outside within about half a percentage point** · against the same counters read from outside · 5 windows of 60 s, bench host · undated

On Linux `/kindling doctor` reads the main thread's run-queue wait, the chunk workers' wait and the host's steal from `/proc` in a background thread.

| Window | Main-thread wait, doctor / outside | Chunk-worker wait | Steal |
| --- | ---: | ---: | ---: |
| Idle, a | 0.0 % / 0.00 % | not shown | 0.0 % / 0.00 % |
| Idle, b | 0.1 % / 0.16 % | not shown | 0.0 % / 0.00 % |
| Pregeneration, a | 12 % / 12.02 % | 0.1 % / 0.11 % | 0.0 % / 0.00 % |
| Pregeneration, b | 13 % / 12.87 % | 0.2 % / 0.16 % | 0.0 % / 0.00 % |
| Pregeneration, c (the cancel command was malformed, so the run kept going) | 4.8 % / 5.11 % | 0.1 % / 0.06 % | 0.0 % / 0.00 % |

<details>
<summary><b>Method</b></summary>

Check on the bench host, server idle and then pregenerating (`/pregen start world 3000`, about 135 to 143 chunks/s), each window 60 s, the doctor run right after the window against the same counters read from outside (`schedstat` per thread, the first line of `/proc/stat`):

</details>

<details>
<summary><b>All numbers</b></summary>

The readings agree within about half a percentage point; the machine was 99 % idle in the idle windows and 32 to 34 % idle with the pregenerator (62 % user, 6 % system). At 12 to 13 % the doctor gave its warning about contention; at 4.8 % it said contention is not what slows the server down. The doctor prints no machine idle share (only the main thread, the workers, steal and the container limit). The windows differ slightly (the doctor's last 60 s against a window I read by hand), so this is a check for agreement, not a precision test. On the VPS the same readings matched too ([above](#waiting-for-a-cpu-on-the-vps)), with the caveat that a nearly idle thread on a VM shows 10 % wait without competition.

Other lines checked in a second short run (world on a 1 GiB tmpfs, an older `leaf-global.yml` with async pathfinding and sleeping block entities off, then `host-monitor: false`): disk space was an `INFO` at 936.7 GiB free and a `WARN` at 832 MiB of 1.0 GiB (also logged at start-up); the older file produced the hint that Kindling's defaults apply only to a fresh file and named both options; with `host-monitor: false` the `LIVE` section is absent; the pregenerator paused itself for lack of disk and `/pregen status` showed the `disk:` line.

</details>

<details>
<summary><b>Caveats and open points</b></summary>

The advice for a direct class path start (AOT cache) was not exercised, because the server started with `java -jar`.

</details>

---

[← Overview](../MEASUREMENTS.md)
