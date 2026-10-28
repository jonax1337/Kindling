package net.kindlingmc.spawn;

import net.minecraft.server.level.ServerLevel;
import org.dreeam.leaf.config.modules.opt.ThrottleNaturalMobSpawning;

/**
 * Tells whether a world uses the failed-spawn-attempt counters of its chunks. Only the two spawn throttles read them
 * (Leaf's {@code throttle-mob-spawning} and Paper's {@code failed-attempts-threshold}), both off by default; while
 * neither is on, the counters are neither counted nor saved, so chunks carry no {@code Paper.FailedSpawnAttempts} tag
 * that vanilla never writes.
 */
public final class FailedSpawnThrottle {

    private FailedSpawnThrottle() {
    }

    public static boolean isEnabled(final ServerLevel level) {
        return ThrottleNaturalMobSpawning.enabled
            || level.paperConfig().entities.spawning.spawningThrottle.failedAttemptsThreshold.enabled();
    }
}
