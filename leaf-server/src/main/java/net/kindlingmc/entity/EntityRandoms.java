package net.kindlingmc.entity;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Chooses the random a new entity draws its UUID and its later rolls from. Purpur's {@code entity.shared-random}
 * gives every entity {@link Entity#SHARED_RANDOM}, which is not thread safe. World generation creates mobs on several
 * chunk workers at once, so two workers can read the same state and give two mobs the same UUID; the chunk system then
 * refuses to add the second one. An entity created off the tick threads therefore gets a random of its own, as every
 * entity does in vanilla.
 */
public final class EntityRandoms {

    private EntityRandoms() {
    }

    public static RandomSource forNewEntity(@Nullable Level level) {
        boolean sharedRandomEnabled = level == null || level.purpurConfig.entitySharedRandom;
        return sharedRandomEnabled && TickThread.isTickThread() ? Entity.SHARED_RANDOM : RandomSource.create();
    }
}
