package densitycompiler;

import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import org.jspecify.annotations.Nullable;

/**
 * One finalDensity call of one world: the volume and the beardifier of its context. Mostly a chunk, as
 * NoiseBasedChunkGenerator.doFill asks for; COLUMN and BOX are the other volumes Paper evaluates and only the
 * verifier checks them.
 */
record Chunk(World world, Shape shape, DensityVolume volume, @Nullable Beardifier beardifier) {

    enum Shape {
        /** 16 x height x 16, chunk aligned, with the chunk's beardifier. */
        CHUNK,
        /** 1 x height x 1 at any block, as iterateNoiseColumn asks for (base height, base column); with a beardifier only to test it. */
        COLUMN,
        /** A random box (unaligned, stepped, or one cell-aligned point); no Paper caller, a check that a fallback path stays exact. */
        BOX
    }

    static Chunk chunk(World world, int chunkX, int chunkZ, Beardifier beardifier) {
        int height = world.noiseSettings().height();
        return new Chunk(world, Shape.CHUNK, new DensityVolume(16, height, 16, chunkX * 16, world.noiseSettings().minY(), chunkZ * 16), beardifier);
    }

    static Chunk column(World world, int blockX, int blockZ) {
        return column(world, blockX, blockZ, null);
    }

    static Chunk column(World world, int blockX, int blockZ, @Nullable Beardifier beardifier) {
        return new Chunk(world, Shape.COLUMN, new DensityVolume(1, world.noiseSettings().height(), 1, blockX, world.noiseSettings().minY(), blockZ), beardifier);
    }

    static Chunk box(World world, DensityVolume volume) {
        return box(world, volume, null);
    }

    static Chunk box(World world, DensityVolume volume, @Nullable Beardifier beardifier) {
        return new Chunk(world, Shape.BOX, volume, beardifier);
    }

    boolean hasStructure() {
        return this.beardifier != null && this.beardifier != Beardifier.EMPTY;
    }

    @Override
    public String toString() {
        DensityVolume v = this.volume;
        return switch (this.shape) {
            case CHUNK -> "seed " + this.world.seed() + " chunk (" + Math.floorDiv(v.minBlockX(), 16) + ", " + Math.floorDiv(v.minBlockZ(), 16) + ")";
            case COLUMN -> "seed " + this.world.seed() + " column (" + v.minBlockX() + ", " + v.minBlockZ() + ")";
            case BOX -> "seed " + this.world.seed() + " box " + v.sizeX() + "x" + v.sizeY() + "x" + v.sizeZ() + " at ("
                + v.minBlockX() + ", " + v.minBlockY() + ", " + v.minBlockZ() + ")"
                + (v.stepBlockX() == 1 && v.stepBlockY() == 1 && v.stepBlockZ() == 1 ? "" : " step " + v.stepBlockX() + "/" + v.stepBlockY() + "/" + v.stepBlockZ());
        };
    }
}
