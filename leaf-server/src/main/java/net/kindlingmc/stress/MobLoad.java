package net.kindlingmc.stress;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

/**
 * The mobs of a stress test: farm animals, villagers and monsters that leave villagers alone (no zombies, which would
 * kill them and fill the log with their deaths), on the ground around the centre of the test world,
 * the more of them the wider spread. The area is kept loaded, so the mobs tick even with nobody watching, and a mob is
 * only placed into a chunk that has finished loading. Mobs that die are replaced until the target is reached again.
 */
final class MobLoad {

    private static final List<EntityType> MIX = List.of(EntityType.COW, EntityType.SHEEP, EntityType.PIG,
        EntityType.CHICKEN, EntityType.VILLAGER, EntityType.SPIDER, EntityType.SKELETON);
    private static final int SPAWN_TRIES_PER_TICK = 50;
    private static final int MIN_SPREAD = 24;
    private static final int SPREAD_PER_ROOT_MOB = 2;

    private final World world;
    private final KeptChunks kept;
    private final List<Entity> mobs = new ArrayList<>();
    private final Random random = new Random();
    private int target;
    private int keptSpread = -1;

    MobLoad(final World world, final KeptChunks kept) {
        this.world = world;
        this.kept = kept;
    }

    void aimAt(final int count) {
        this.target = count;
    }

    void tick() {
        this.mobs.removeIf(mob -> !mob.isValid());
        if (this.mobs.size() < this.target) {
            this.spawnSome(this.spread());
        }
        while (this.mobs.size() > this.target) {
            this.mobs.removeLast().remove();
        }
    }

    private int spread() {
        return Math.max(MIN_SPREAD, (int) Math.sqrt(this.target) * SPREAD_PER_ROOT_MOB);
    }

    private void spawnSome(final int spread) {
        this.keepArea(spread);
        for (int tries = 0; tries < SPAWN_TRIES_PER_TICK && this.mobs.size() < this.target; tries++) {
            final int x = this.random.nextInt(-spread, spread + 1);
            final int z = this.random.nextInt(-spread, spread + 1);
            if (this.kept.isLoaded(x >> 4, z >> 4)) {
                this.mobs.add(this.spawnAt(x, z));
            }
        }
    }

    private void keepArea(final int spread) {
        if (spread <= this.keptSpread) {
            return;
        }
        for (int chunkX = -spread >> 4; chunkX <= spread >> 4; chunkX++) {
            for (int chunkZ = -spread >> 4; chunkZ <= spread >> 4; chunkZ++) {
                this.kept.hold(chunkX, chunkZ);
            }
        }
        this.keptSpread = spread;
    }

    private Entity spawnAt(final int x, final int z) {
        final Location ground = new Location(this.world, x + 0.5, this.world.getHighestBlockYAt(x, z) + 1, z + 0.5);
        final Entity mob = this.world.spawnEntity(ground, MIX.get(this.random.nextInt(MIX.size())));
        if (mob instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
        }
        return mob;
    }

    void removeAll() {
        this.target = 0;
        for (final Entity mob : this.mobs) {
            mob.remove();
        }
        this.mobs.clear();
    }

    int count() {
        return this.mobs.size();
    }

    int target() {
        return this.target;
    }
}
