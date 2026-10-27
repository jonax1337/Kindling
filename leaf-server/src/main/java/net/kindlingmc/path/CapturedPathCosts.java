package net.kindlingmc.path;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.Nullable;

/**
 * The pathfinding costs of one mob, read on the main thread before its path search starts. Leaf's async pathfinding
 * searches on another thread; reading these instead of the mob keeps that search off the mob's mutable cost map and
 * lets the mob's end-of-search hook run on the main thread without changing the costs the search sees.
 */
public final class CapturedPathCosts {

    private static final PathType[] PATH_TYPES = PathType.values();

    private final float[] costs = new float[PATH_TYPES.length];
    private @Nullable Mob mob;

    public void capture(final Mob mob) {
        for (PathType pathType : PATH_TYPES) {
            this.costs[pathType.ordinal()] = mob.getPathfindingMalus(pathType);
        }
        this.mob = mob;
    }

    public void release() {
        this.mob = null;
    }

    /**
     * The captured cost while a search for this mob runs; the mob's current cost for any other mob or outside a
     * search, as before.
     */
    public float malus(final Mob mob, final PathType pathType) {
        return mob == this.mob ? this.costs[pathType.ordinal()] : mob.getPathfindingMalus(pathType);
    }
}
