package net.kindlingmc.entity;

import ca.spottedleaf.moonrise.common.util.TickThread;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Entities share {@link Entity#SHARED_RANDOM} only when they are created on a tick thread. */
@Normal
class EntityRandomsTest {

    @Test
    void entityCreatedOnATickThreadSharesTheRandom() throws InterruptedException {
        AtomicReference<RandomSource> chosen = new AtomicReference<>();
        Thread tickThread = new TickThread(() -> chosen.set(EntityRandoms.forNewEntity(null)), "test tick thread");
        tickThread.start();
        tickThread.join();
        assertSame(Entity.SHARED_RANDOM, chosen.get());
    }

    @Test
    void entityCreatedOffTheTickThreadsGetsARandomOfItsOwn() {
        RandomSource first = EntityRandoms.forNewEntity(null);
        RandomSource second = EntityRandoms.forNewEntity(null);
        assertNotSame(Entity.SHARED_RANDOM, first);
        assertNotSame(first, second);
    }
}
