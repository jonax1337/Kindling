package net.kindlingmc.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InertBehaviorTest {

    private final BehaviorControl<LivingEntity> inert = InertBehavior.instance();

    @Test
    void neverStarts() {
        assertFalse(this.inert.tryStart(null, null, 0L));
        assertEquals(Behavior.Status.STOPPED, this.inert.getStatus());
    }

    @Test
    void needsNoMemory() {
        assertTrue(this.inert.getRequiredMemories().isEmpty());
    }

    @Test
    void oneInstanceServesEveryBrain() {
        assertSame(this.inert, InertBehavior.instance());
    }
}
