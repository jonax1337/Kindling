package net.kindlingmc.spawn;

import net.minecraft.world.level.levelgen.RandomSupport;

/**
 * The seeds of one spawn try's two random generators, one for the start and the steps, one for the pick and the
 * checks: SplitMix64 outputs of the tick's seed, so every try of a tick draws from generators of its own.
 */
record TrySeeds(long positions, long checks) {

    static TrySeeds of(final long tickSeed, final int tryIndex) {
        final long base = tickSeed + 2L * tryIndex * RandomSupport.GOLDEN_RATIO_64;
        return new TrySeeds(
            RandomSupport.mixStafford13(base + RandomSupport.GOLDEN_RATIO_64),
            RandomSupport.mixStafford13(base + 2L * RandomSupport.GOLDEN_RATIO_64)
        );
    }
}
