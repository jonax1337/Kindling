package densitycompiler.eval;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * InterpolatedFunction.Sampler.sampleWithBlockStep, one column at a time. The corner volume is Paper's formula for
 * the whole call volume and is evaluated once per call (lazily, so a branch no column needs never runs its corners).
 * Each output value is the same float expression as in fillCell: z lerps, then the x lerp, then
 * value = v_0_ + valueStep * y0 and value += valueStep per y, with y0 the first in-volume y of the cell.
 */
final class InterpolatedTile extends Tile {
    private final CornerSampler corners;
    private final int cellSizeXz;
    private final int cellSizeY;
    private final float cellSizeXzInv;
    private final float cellSizeYInv;
    private final PruneCounter counter;
    private long preparedCall = -1;
    private float[] cornerValues;
    private int minCellX;
    private int minCellY;
    private int minCellZ;
    private int cellCountY;
    private int cornerSizeX;
    private int cornerSizeY;
    private int cornerSizeZ;

    /** cellSizes is (xz, y), cellSizesInv Paper's precomputed (1 / xz, 1 / y). */
    InterpolatedTile(CornerSampler corners, int[] cellSizes, float[] cellSizesInv, PruneCounter counter) {
        this.corners = corners;
        this.cellSizeXz = cellSizes[0];
        this.cellSizeY = cellSizes[1];
        this.cellSizeXzInv = cellSizesInv[0];
        this.cellSizeYInv = cellSizesInv[1];
        this.counter = counter;
    }

    /** Paper's first branch: an aligned volume (for step 1: single blocks on every axis) goes to the input unchanged. */
    boolean passesVolumeThrough(DensityVolume volume) {
        return (volume.stepBlockX() == this.cellSizeXz || volume.sizeX() == 1)
            && (volume.stepBlockY() == this.cellSizeY || volume.sizeY() == 1)
            && (volume.stepBlockZ() == this.cellSizeXz || volume.sizeZ() == 1)
            && Math.floorMod(volume.minBlockX(), this.cellSizeXz) == 0
            && Math.floorMod(volume.minBlockY(), this.cellSizeY) == 0
            && Math.floorMod(volume.minBlockZ(), this.cellSizeXz) == 0;
    }

    /** Counts whether this call evaluated the corner volume; called once at the end of every call. */
    void recordCall(TileFrame frame) {
        this.counter.recordBranch(1, this.preparedCall == frame.call() ? 1 : 0);
    }

    @Override
    void fill(TileFrame frame, float[] out, int x, int z) {
        if (this.preparedCall != frame.call()) {
            this.evaluateCorners(frame);
        }
        DensityVolume volume = frame.volume();
        int blockX = volume.minBlockX() + x;
        int blockZ = volume.minBlockZ() + z;
        int cellX = Mth.floorDiv(blockX, this.cellSizeXz) - this.minCellX;
        int cellZ = Mth.floorDiv(blockZ, this.cellSizeXz) - this.minCellZ;
        int nextCellX = Math.min(cellX + 1, this.cornerSizeX - 1);
        int nextCellZ = Math.min(cellZ + 1, this.cornerSizeZ - 1);
        float alphaX = (blockX - (this.minCellX + cellX) * this.cellSizeXz) * this.cellSizeXzInv;
        float alphaZ = (blockZ - (this.minCellZ + cellZ) * this.cellSizeXz) * this.cellSizeXzInv;
        int column00 = this.cornerColumn(cellX, cellZ);
        int column10 = this.cornerColumn(nextCellX, cellZ);
        int column01 = this.cornerColumn(cellX, nextCellZ);
        int column11 = this.cornerColumn(nextCellX, nextCellZ);
        float lower = this.level(alphaX, alphaZ, column00, column10, column01, column11, 0);
        for (int cellY = 0; cellY < this.cellCountY; ++cellY) {
            int nextCellY = Math.min(cellY + 1, this.cornerSizeY - 1);
            float upper = this.level(alphaX, alphaZ, column00, column10, column01, column11, nextCellY);
            this.fillCellColumn(volume, out, cellY, lower, upper);
            lower = upper;
        }
    }

    /** Paper's cell volume formula (sampleWithBlockStep) and one corner call on it, held until the call ends. */
    private void evaluateCorners(TileFrame frame) {
        DensityVolume volume = frame.volume();
        this.minCellX = Mth.floorDiv(volume.minBlockX(), this.cellSizeXz);
        this.minCellY = Mth.floorDiv(volume.minBlockY(), this.cellSizeY);
        this.minCellZ = Mth.floorDiv(volume.minBlockZ(), this.cellSizeXz);
        int cellCountX = Mth.floorDiv(volume.maxBlockX(), this.cellSizeXz) - this.minCellX + 1;
        this.cellCountY = Mth.floorDiv(volume.maxBlockY(), this.cellSizeY) - this.minCellY + 1;
        int cellCountZ = Mth.floorDiv(volume.maxBlockZ(), this.cellSizeXz) - this.minCellZ + 1;
        this.cornerSizeX = Math.floorMod(volume.maxBlockX(), this.cellSizeXz) == 0 ? cellCountX : cellCountX + 1;
        this.cornerSizeY = Math.floorMod(volume.maxBlockY(), this.cellSizeY) == 0 ? this.cellCountY : this.cellCountY + 1;
        this.cornerSizeZ = Math.floorMod(volume.maxBlockZ(), this.cellSizeXz) == 0 ? cellCountZ : cellCountZ + 1;
        DensityVolume cornerVolume = new DensityVolume(this.cornerSizeX, this.cornerSizeY, this.cornerSizeZ, this.minCellX * this.cellSizeXz,
            this.minCellY * this.cellSizeY, this.minCellZ * this.cellSizeXz, this.cellSizeXz, this.cellSizeY, this.cellSizeXz);
        ScopedDensityBuffer buffer = frame.hold(cornerVolume);
        this.corners.sampleVolume(frame.context(), buffer, cornerVolume);
        this.cornerValues = buffer.values;
        this.preparedCall = frame.call();
    }

    /** cornerVolume.indexUnchecked(cellX, 0, cellZ); a corner column is contiguous in y. */
    private int cornerColumn(int cellX, int cellZ) {
        return (cellX + cellZ * this.cornerSizeX) * this.cornerSizeY;
    }

    /** fillCell's v_0_ (or v_1_) of one corner level: lerp over z on both x sides, then over x, in Paper's order. */
    private float level(float alphaX, float alphaZ, int column00, int column10, int column01, int column11, int cellY) {
        float[] c = this.cornerValues;
        float lowX = Mth.lerp(alphaZ, c[column00 + cellY], c[column01 + cellY]);
        float highX = Mth.lerp(alphaZ, c[column10 + cellY], c[column11 + cellY]);
        return Mth.lerp(alphaX, lowX, highX);
    }

    /** fillCell's inner y loop for one cell of this column. */
    private void fillCellColumn(DensityVolume volume, float[] out, int cellY, float lower, float upper) {
        int cellOutputY = (this.minCellY + cellY) * this.cellSizeY - volume.minBlockY();
        int y0 = Math.max(0, -cellOutputY);
        int y1 = Math.min(this.cellSizeY, volume.sizeY() - cellOutputY) - 1;
        float valueStep = (upper - lower) * this.cellSizeYInv;
        float value = lower + valueStep * y0;
        int index = cellOutputY + y0;
        for (int y = y0; y <= y1; ++y) {
            out[index++] = value;
            value += valueStep;
        }
    }
}
