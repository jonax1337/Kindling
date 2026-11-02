# Find the cause of lag

## 1. Check the settings and the machine

```
/kindling doctor
```

Fix every `WARN` first; each one names its fix. `INFO` lines are hints.

## 2. Check whether the host is the problem (Linux)

Let the server run for a minute under its usual load, then run `/kindling doctor` again and read the live section:

| The doctor says | Meaning | What to do |
| --- | --- | --- |
| The main thread waited for a CPU, with a warning | Other threads or processes on the machine compete with the main thread. | Lower `chunk-system.worker-threads`, move other services off the machine, or turn on the [worker governor](../reference/configuration.md#worker-governor). |
| Steal above 5 % | The host takes CPU time from your virtual machine. | Ask the provider, or move to dedicated cores. |
| A container CPU limit throttled the server | The container's CPU quota is too small. | Raise the limit. |
| Wake-up latency, no warning | Normal on a virtual machine. | Nothing. |

## 3. Reproduce the load

To see how your server copes, load it in a throwaway world:

```
/kindling stress players 10 fly
/kindling stress mobs 1000
/kindling stress bases 4
```

The boss bar shows tick time and TPS. Raise one load at a time to find the one that hurts. `/kindling stress stop` ends the test and deletes the world.

## 4. Profile

For the code that takes the time, install the [spark](https://spark.lucko.me/) plugin and run `/spark profiler start`, wait a few minutes under load, `/spark profiler stop`.

Command details: [`/kindling doctor`](../reference/commands.md#kindling-doctor), [`/kindling stress`](../reference/commands.md#kindling-stress).
