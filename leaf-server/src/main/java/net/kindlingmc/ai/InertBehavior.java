package net.kindlingmc.ai;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * Stands in for a behaviour that could never start, such as a search for a job site by a villager that cannot hold
 * one. It has no state, so one instance serves every brain.
 */
public final class InertBehavior<E extends LivingEntity> implements BehaviorControl<E> {

    private static final InertBehavior<LivingEntity> INSTANCE = new InertBehavior<>();

    private InertBehavior() {
    }

    @SuppressWarnings("unchecked")
    public static <E extends LivingEntity> InertBehavior<E> instance() {
        return (InertBehavior<E>) INSTANCE;
    }

    @Override
    public Behavior.Status getStatus() {
        return Behavior.Status.STOPPED;
    }

    @Override
    public Set<MemoryModuleType<?>> getRequiredMemories() {
        return Set.of();
    }

    @Override
    public boolean tryStart(final ServerLevel level, final E body, final long timestamp) {
        return false;
    }

    @Override
    public void tickOrStop(final ServerLevel level, final E body, final long timestamp) {
    }

    @Override
    public void doStop(final ServerLevel level, final E body, final long timestamp) {
    }

    @Override
    public String debugString() {
        return "Inert";
    }
}
