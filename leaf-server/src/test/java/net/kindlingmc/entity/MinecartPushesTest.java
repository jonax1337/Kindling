package net.kindlingmc.entity;

import org.dreeam.leaf.config.modules.opt.MinecartPushLimit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MinecartPushesTest {

    private final boolean enabledDefault = MinecartPushLimit.enabled;
    private final int maxPushesDefault = MinecartPushLimit.maxPushes;

    @AfterEach
    void restoreTheSettings() {
        MinecartPushLimit.enabled = this.enabledDefault;
        MinecartPushLimit.maxPushes = this.maxPushesDefault;
    }

    @Test
    void theLimitIsOffByDefault() {
        assertFalse(this.enabledDefault);
    }

    @Test
    void withTheLimitOffACartPushesEveryCartInReachLikeVanilla() {
        MinecartPushLimit.enabled = false;
        MinecartPushLimit.maxPushes = 2;

        assertEquals(Integer.MAX_VALUE, MinecartPushes.allowedPerTick());
    }

    @Test
    void withTheLimitOnACartPushesAtMostTheConfiguredNumber() {
        MinecartPushLimit.enabled = true;
        MinecartPushLimit.maxPushes = 2;

        assertEquals(2, MinecartPushes.allowedPerTick());
    }
}
