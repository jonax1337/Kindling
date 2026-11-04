# Memory and JVM flags

## With 4 GB or more for the server

```bash
java -Xms3G -Xmx3G -XX:+UseCompactObjectHeaders -jar kindling-26.3-<build>.jar --nogui
```

A 3 GB heap avoids most full collections while players explore new terrain. Keep the default G1 collector.

## With less memory

Use a 2 GB heap with three G1 flags:

```bash
java -Xms2G -Xmx2G -XX:+UseCompactObjectHeaders \
  -XX:G1ReservePercent=25 -XX:-G1UseAdaptiveIHOP -XX:InitiatingHeapOccupancyPercent=35 \
  -jar kindling-26.3-<build>.jar --nogui
```

Without them, a 2 GB heap freezes for close to a second every 15 to 20 seconds while players explore. They cost about 5 % more CPU.

## Give memory back while nobody is online

1. Let the heap shrink: start with a smaller `-Xms` than `-Xmx`, for example `-Xms512M -Xmx3G`.
2. Turn on [`idle-memory`](../reference/configuration.md#idle-memory) in `config/leaf-global.yml`.

After five empty minutes the server collects, trims the native heap and logs how much it gave back.

## Check the result

Run `/kindling doctor`. It checks the heap against the machine's memory, the collector and the native memory settings, and names the fix for each warning.

The measurements behind these numbers: [JVM and memory](../measurements/memory-jvm.md).
