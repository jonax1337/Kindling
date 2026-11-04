# Getting started

In this tutorial you start a Kindling server, check that it runs Kindling's defaults, and pregenerate the area around spawn. It takes about fifteen minutes plus the pregeneration time.

You need Java 25 and a machine with at least 4 GB of free memory.

## 1. Download the jar

Download the newest `kindling-26.3-<build>.jar` from [Releases](https://github.com/jonax1337/Kindling/releases) into an empty folder.

## 2. Start the server once

```bash
java -Xms3G -Xmx3G -XX:+UseCompactObjectHeaders -jar kindling-26.3-<build>.jar --nogui
```

Accept the EULA in `eula.txt` when asked and start again. When the log shows `Done`, the server is up.

## 3. Check the version

In the server console, type:

```
version
```

The answer names the Kindling build and its commit. If a newer release exists, it says so.

## 4. Ask the doctor

```
kindling doctor
```

You see a list of `WARN`, `INFO` and `OK` lines about this machine and its settings. Every `WARN` names its fix. On a fresh server most lines are `OK`.

## 5. Pregenerate around spawn

```
pregen start world 2000
```

The server answers with what it started. Watch the progress:

```
pregen status
```

The run steps back by itself when ticks get slow, and resumes after a restart. When `status` reports no more runs, every chunk within 2 000 blocks of spawn exists.

## What you have now

A Kindling server with its measured defaults and a pregenerated spawn area. Players who explore beyond it get new chunks ahead of their path first.

Next:

- [Switch an existing Leaf server](guides/switch-from-leaf.md), [choose memory and JVM flags](guides/memory-and-jvm.md), [find the cause of lag](guides/find-lag.md).
- Why Kindling is faster where it is: [Measurements](MEASUREMENTS.md).
