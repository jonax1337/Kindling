package previewchunks;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** A vertical range of whole noise cells, minY to maxY inclusive, inside the noise settings' height. */
record Band(int minY, int maxY) {
    /** The overworld's interpolation cell height (InterpolatedFunction cell_size_y); its corners sit at multiples of it. */
    static final int CELL_HEIGHT = 8;

    /** The smallest band of whole cells that holds lowestY to highestY, cut to the world's height. */
    static Band covering(int lowestY, int highestY, NoiseSettings limits) {
        int min = Math.max(limits.minY(), Math.floorDiv(lowestY, CELL_HEIGHT) * CELL_HEIGHT);
        int max = Math.min(topY(limits), Math.floorDiv(highestY, CELL_HEIGHT) * CELL_HEIGHT + CELL_HEIGHT - 1);
        return new Band(min, max);
    }

    Band grownUp(int blocks, NoiseSettings limits) {
        return covering(this.minY, this.maxY + blocks, limits);
    }

    Band grownDown(int blocks, NoiseSettings limits) {
        return covering(this.minY - blocks, this.maxY, limits);
    }

    boolean reachesTop(NoiseSettings limits) {
        return this.maxY >= topY(limits);
    }

    boolean reachesBottom(NoiseSettings limits) {
        return this.minY <= limits.minY();
    }

    int height() {
        return this.maxY - this.minY + 1;
    }

    int minSectionY() {
        return SectionPos.blockToSectionCoord(this.minY);
    }

    int maxSectionY() {
        return SectionPos.blockToSectionCoord(this.maxY);
    }

    DensityVolume volume(ChunkPos pos) {
        return new DensityVolume(16, height(), 16, pos.getMinBlockX(), this.minY, pos.getMinBlockZ());
    }

    private static int topY(NoiseSettings limits) {
        return limits.minY() + limits.height() - 1;
    }
}
