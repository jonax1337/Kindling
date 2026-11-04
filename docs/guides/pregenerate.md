# Pregenerate a world

Generating the world in advance takes chunk generation off the main thread while players are online.

## Run it once

```
/pregen start world 5000
```

This generates every chunk within 5 000 blocks of the world spawn. To centre elsewhere, add the coordinates: `/pregen start world 5000 1200 -800`.

- Watch it: `/pregen status`.
- Pause it for an event: `/pregen pause world`, later `/pregen resume world`.
- Stop it for good: `/pregen cancel world`.

A run steps back by itself while the average tick is above 40 ms, keeps its progress across restarts and stops before the disk runs out.

## Run it on every startup

Set the radii and turn on the automatic run in `config/leaf-global.yml`:

```yaml
performance:
  pregeneration:
    automatic:
      enabled: true
      overworld-radius: 5000
      nether-radius: 1000
      end-radius: 3000
```

Each world is generated until its radius is done, then never again.

## Keep up with explorers

[Frontier pregeneration](../reference/configuration.md#pregenerationfrontier) generates a margin around the areas players have visited, while nobody is online. It is off by default because the world folder grows with every visited area.

## Share a machine

If other servers run on the same machine, lower `max-parallel` (default 32), the number of chunks a run keeps in flight, or lower `chunk-system.worker-threads` for this server. On Linux, `max-main-thread-wait` can also throttle a run while the main thread waits for a CPU. It is off by default: at 0.10 it halved pregeneration on the bench host, and on a VPS an almost idle main thread already waits 10 to 14 %. Higher values are not tested.

All settings: [pregeneration](../reference/configuration.md#pregeneration). All subcommands: [`/pregen`](../reference/commands.md#pregen).
