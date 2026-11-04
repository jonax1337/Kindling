# Switch from Leaf

Kindling reads Leaf's configuration as it is and keeps Leaf's brand id, so plugins that check for Leaf keep working. What an existing `config/leaf-global.yml` does not get by itself are Kindling's defaults, because Leaf writes new defaults only into a fresh file.

1. Stop the server and back up the worlds and `config/`.
2. Replace the Leaf jar with `kindling-26.3-<build>.jar`. Keep your start command; see [Memory and JVM flags](memory-and-jvm.md) for the recommended one.
3. In `config/leaf-global.yml`, turn on what Kindling turns on:

   ```yaml
   async:
     async-entity-tracker:
       enabled: true
     async-playerdata-save:
       enabled: true
   performance:
     sleeping-block-entity: true
   ```

4. For vanilla behaviour, turn off what Leaf turns on:

   ```yaml
   async:
     async-mob-spawning:
       enabled: false
   performance:
     optimize-player-movement: false
     skip-map-item-data-updates-if-map-does-not-have-craftmaprenderer: false
   ```

   Leave `async.async-pathfinding.enabled` at `false`.

5. Leave `chunk-system.worker-threads` in `config/paper-global.yml` at `-1`, so Kindling picks its default worker count.
6. Start the server and run `/kindling doctor`. Its "vanilla parity" finding lists every remaining Leaf or Gale value that differs from vanilla, with the value to set. The full list is in [Vanilla differences](../reference/vanilla-differences.md#defaults-set-back-to-vanilla).

Kindling's own settings have working defaults and need no entry: [Configuration](../reference/configuration.md).
