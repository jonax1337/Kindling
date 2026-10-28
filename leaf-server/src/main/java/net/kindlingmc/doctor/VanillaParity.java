package net.kindlingmc.doctor;

import java.util.ArrayList;
import java.util.List;

/**
 * Which running values of the performance settings make the server differ from vanilla and Paper.
 * A fresh config file is written with vanilla values; an existing file keeps what it had.
 */
final class VanillaParity {

    private static final String GALE_GLOBAL = "gale-global.yml: ";
    private static final String GALE_WORLD = "gale-world-defaults.yml or gale-world.yml: ";
    private static final String LEAF_GLOBAL = "leaf-global.yml: ";
    /** Vanilla's {@code ItemEntity#setDefaultPickUpDelay}. */
    private static final int VANILLA_PICKUP_DELAY = 10;
    /** A negative limit or a negative delay switches Gale's setting off. */
    private static final int UNLIMITED = -1;
    /** An interval of 1 means every tick. */
    private static final int EVERY_TICK = 1;

    private VanillaParity() {
    }

    /** One setting whose running value is not vanilla's. */
    record Deviation(String setting, String running, String vanilla) {
    }

    /** The running values of the settings that are checked; Gale's world settings are those of one world. */
    record Values(
        int projectileChunkLoadsPerTick,
        int projectileChunkLoadsPerProjectile,
        int checkStuckInWallInterval,
        int updateEntityLineOfSightInterval,
        int villagerItemRepickupDelay,
        double entityWakeUpDeviation,
        boolean skipMapItemDataUpdates,
        boolean optimizePlayerMovement,
        boolean asyncPathfinding,
        boolean asyncMobSpawning
    ) {
    }

    static List<Deviation> deviations(final Values values) {
        final List<Deviation> deviations = new ArrayList<>();
        addIf(deviations, values.projectileChunkLoadsPerTick() >= 0,
            GALE_WORLD + "small-optimizations.max-projectile-chunk-loads.per-tick", values.projectileChunkLoadsPerTick(), UNLIMITED);
        addIf(deviations, values.projectileChunkLoadsPerProjectile() >= 0,
            GALE_WORLD + "small-optimizations.max-projectile-chunk-loads.per-projectile.max", values.projectileChunkLoadsPerProjectile(), UNLIMITED);
        addIf(deviations, values.checkStuckInWallInterval() > EVERY_TICK,
            GALE_WORLD + "small-optimizations.reduced-intervals.check-stuck-in-wall", values.checkStuckInWallInterval(), EVERY_TICK);
        addIf(deviations, values.updateEntityLineOfSightInterval() > EVERY_TICK,
            GALE_GLOBAL + "small-optimizations.reduced-intervals.update-entity-line-of-sight", values.updateEntityLineOfSightInterval(), EVERY_TICK);
        addIf(deviations, !isVanillaPickupDelay(values.villagerItemRepickupDelay()),
            GALE_WORLD + "small-optimizations.reduced-intervals.villager-item-repickup", values.villagerItemRepickupDelay(), UNLIMITED);
        addIf(deviations, values.entityWakeUpDeviation() > 0,
            GALE_WORLD + "gameplay-mechanics.entity-wake-up-duration-ratio-standard-deviation", values.entityWakeUpDeviation(), 0);
        addIf(deviations, values.skipMapItemDataUpdates(),
            LEAF_GLOBAL + "performance.skip-map-item-data-updates-if-map-does-not-have-craftmaprenderer", true, false);
        addIf(deviations, values.optimizePlayerMovement(),
            LEAF_GLOBAL + "performance.optimize-player-movement", true, false);
        addIf(deviations, values.asyncPathfinding(),
            LEAF_GLOBAL + "async.async-pathfinding.enabled", true, false);
        addIf(deviations, values.asyncMobSpawning(),
            LEAF_GLOBAL + "async.async-mob-spawning.enabled", true, false);
        return deviations;
    }

    /** Gale falls back to vanilla's delay for a negative value, and 10 is that delay. */
    private static boolean isVanillaPickupDelay(final int delay) {
        return delay < 0 || delay == VANILLA_PICKUP_DELAY;
    }

    private static void addIf(final List<Deviation> deviations, final boolean deviates, final String setting, final Object running, final Object vanilla) {
        if (deviates) {
            deviations.add(new Deviation(setting, String.valueOf(running), String.valueOf(vanilla)));
        }
    }
}
