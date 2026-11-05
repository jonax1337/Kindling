# Counter lag machines

Large redstone builds, TNT cannons and crowds of minecarts can take a server's tick time far above 50 ms. Kindling keeps vanilla's behaviour by default, so each counter below changes gameplay a little and is your choice. Check first that one of them is the cause: [Find the cause of lag](find-lag.md).

## Redstone: switch to Alternate Current

Paper ships faster redstone dust than vanilla's. On Kindling's observer lag machine it cut the tick time from 34.4 to 11.0 ms.

1. In `config/paper-world-defaults.yml`, set:

   ```yaml
   misc:
     redstone-implementation: ALTERNATE_CURRENT
   ```

   A single world can use its own value in its `paper-world.yml`.
2. Restart the server.
3. Test the redstone builds that matter on your server.

Alternate Current changes the order in which redstone updates reach other blocks. Clocks, farms and item sorters kept working in Kindling's tests, but builds that depend on vanilla's order, such as some update detectors and zero-tick contraptions, can behave differently. `EIGENCRAFT` is the other alternative; it was slower than Alternate Current in the same test (15.7 ms). Set `VANILLA` and restart to switch back.

## Minecarts: limit the pushes

A pit of minecarts makes every cart push every other cart in reach, every tick. With the limit on, each cart pushes at most a few other carts per tick; on Kindling's minecart lag machine that cut the tick time from 22.1 to 19.0 ms:

```yaml
# config/leaf-global.yml
performance:
  minecart-push-limit:
    enabled: true
    max-pushes: 4
```

A crowd of carts then spreads out more slowly than in vanilla. A cart with no more than `max-pushes` other carts in reach behaves as in vanilla. Details: [`minecart-push-limit`](../reference/configuration.md#minecart-push-limit).

## TNT: know Spigot's budget

Spigot ticks at most `max-tnt-per-tick` primed TNT per tick (100 by default, in `spigot.yml` under `world-settings`). Under heavy TNT use, the TNT beyond the budget does not explode later: its fuse stops, so it piles up and keeps loading the server. Raise the value, or set `0` for no limit, if TNT farms or cannons must keep working under load; lower it to slow down TNT griefing at the cost of TNT that stalls.

## Check the result

Run `/kindling doctor`. It names the redstone implementations the worlds use. Run [`/kindling stress`](../reference/commands.md#kindling-stress) or the [bench's lag scenario](../../kindling-bench/README.md#the-lag-scenario) before and after a change to compare the tick times.

The measurements behind these numbers: [Lag machines](../measurements/lag-machines.md).
