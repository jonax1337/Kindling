package net.kindlingmc.entity;

import org.dreeam.leaf.config.modules.opt.MinecartPushLimit;

/**
 * How many other minecarts one minecart may push in one tick. Vanilla has no limit; with
 * {@code performance.minecart-push-limit} on, a cart in a crowd stops pushing after the configured number, which keeps
 * minecart lag machines from making every cart push every other one each tick.
 */
public final class MinecartPushes {

    private MinecartPushes() {
    }

    /** The pushes a minecart has for one tick: {@link Integer#MAX_VALUE}, as many as vanilla makes, while the limit is off. */
    public static int allowedPerTick() {
        return MinecartPushLimit.enabled ? MinecartPushLimit.maxPushes : Integer.MAX_VALUE;
    }
}
