package aquifer;

import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/**
 * One aquifer instance's life: a world, the volume it is built for, and the phases of calls replayed on it.
 * The group names what is being checked and becomes the tally label.
 */
record ChunkCase(Group group, World world, DensityVolume volume, long seed, boolean syntheticDensities) {
    private static final String PRNG = "Xoroshiro128PlusPlus";
    private static final int SHUFFLE_CALLS = 20_000;
    private static final int EDGE_CALLS = 15_000;
    /** The lowest blocks, whose candidate cells include the bottom grid row: the one row whose locations wrap. */
    private static final int DEEP_FLOOR_ROWS = 4;

    enum Group {
        /** A whole chunk in doFill order, then the carving mask on the same aquifer. */
        FILL,
        /** A 1 x height x 1 volume replayed top to bottom, as getBaseHeight and getBaseColumn do. */
        COLUMN,
        /** Random positions in random order with fuzzed densities: the lazy caches must not depend on order. */
        SHUFFLE,
        /** Coordinates and settings vanilla presets never reach: +-30,000,000, a 4064-high volume, the Legacy random source. */
        EDGE,
        /**
         * A 4064-high volume at minY -2032 in the deep-sea world, synthetic densities: first only the lowest rows (their cells
         * lie below -2048, where BlockPos wraps Y), then the whole height.
         */
        DEEP,
        /** A chunk in the sea-level -10 world, real densities: doFill order, carving mask, then a shuffled replay on one instance. */
        FLOODED
    }

    /** The calls replayed on the case's aquifer in one go. Phases of a case share one aquifer instance. */
    record Phase(String label, CallList calls) {
    }

    ChunkCase {
        if (syntheticDensities && (group == Group.FILL || group == Group.COLUMN || group == Group.FLOODED)) {
            throw new IllegalArgumentException(group + " replays real densities");
        }
    }

    List<Phase> phases() {
        RandomGenerator random = RandomGeneratorFactory.of(PRNG).create(this.seed);
        float[] densities = this.syntheticDensities ? null : DensityLoader.load(this.world, this.volume);
        return switch (this.group) {
            case FILL -> List.of(new Phase("FILL", CallList.fill(this.volume, densities)), new Phase("CARVE", CallList.carve(this.volume, random)));
            case COLUMN -> List.of(new Phase("COLUMN", CallList.fill(this.volume, densities)));
            case SHUFFLE -> List.of(new Phase("SHUFFLE", CallList.sampled(this.volume, densities, SHUFFLE_CALLS, CallList.Densities.FUZZED, random)));
            case EDGE -> List.of(new Phase("EDGE", CallList.sampled(this.volume, densities, EDGE_CALLS, CallList.Densities.AS_IS, random)));
            case DEEP -> List.of(new Phase("DEEP-FLOOR", CallList.floor(this.volume, DEEP_FLOOR_ROWS)),
                new Phase("DEEP", CallList.sampled(this.volume, densities, EDGE_CALLS, CallList.Densities.AS_IS, random)));
            case FLOODED -> List.of(new Phase("FLOODED-FILL", CallList.fill(this.volume, densities)),
                new Phase("FLOODED-CARVE", CallList.carve(this.volume, random)),
                new Phase("FLOODED-SHUFFLE", CallList.sampled(this.volume, densities, SHUFFLE_CALLS, CallList.Densities.FUZZED, random)));
        };
    }

    @Override
    public String toString() {
        DensityVolume v = this.volume;
        return this.group + " " + this.world + " volume " + v.sizeX() + "x" + v.sizeY() + "x" + v.sizeZ() + " at ("
            + v.minBlockX() + ", " + v.minBlockY() + ", " + v.minBlockZ() + ")" + (this.syntheticDensities ? " synthetic densities" : "");
    }
}
