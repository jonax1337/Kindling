package net.kindlingmc.tracker;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import net.minecraft.world.entity.Entity;

/**
 * Takes the flags with which the main thread asks the tracker to send an entity's movement. Leaf's async tracker
 * sends entities on its own threads while the main thread may set these flags again (knockback, hurt,
 * {@code setVelocity}). Clearing a flag after reading it would drop a request made in between; taking it with one
 * atomic read-and-clear keeps such a request for the next send, as vanilla's tracker does for a flag set after it ran.
 */
public final class TrackerFlags {

    private static final VarHandle NEEDS_SYNC = entityFlag("needsSync");
    private static final VarHandle SYNC_VELOCITY = entityFlag("syncVelocity");

    private TrackerFlags() {
    }

    public static boolean takeNeedsSync(final Entity entity) {
        return entity.needsSync && (boolean) NEEDS_SYNC.getAndSet(entity, false);
    }

    public static boolean takeSyncVelocity(final Entity entity) {
        return entity.syncVelocity && (boolean) SYNC_VELOCITY.getAndSet(entity, false);
    }

    private static VarHandle entityFlag(final String name) {
        try {
            return MethodHandles.lookup().findVarHandle(Entity.class, name, boolean.class);
        } catch (final ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
