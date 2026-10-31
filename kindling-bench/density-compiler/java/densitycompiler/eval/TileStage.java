package densitycompiler.eval;

import java.util.List;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Column tiling of the block level: the tree above the interpolated functions is evaluated one (x, z) column at a
 * time into small reused tiles and each column is copied into the output. Volumes Paper does not interpolate with
 * block step (any step other than 1, or a volume an interpolated function passes through unchanged) go to the
 * step-1 tree instead.
 */
final class TileStage implements DensitySampler {
    private final Tile root;
    private final List<InterpolatedTile> interpolated;
    private final TileFrame frame;
    private final DensitySampler stepOne;

    TileStage(Tile root, List<InterpolatedTile> interpolated, TileFrame frame, DensitySampler stepOne) {
        this.root = root;
        this.interpolated = interpolated;
        this.frame = frame;
        this.stepOne = stepOne;
    }

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        if (this.tiles(volume)) {
            this.sampleColumns(context, outputBuffer, volume);
        } else {
            this.stepOne.sampleVolume(context, outputBuffer, volume);
        }
    }

    @Override
    public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
        return this.stepOne.sampleValue(context, blockX, blockY, blockZ);
    }

    private boolean tiles(DensityVolume volume) {
        if (volume.stepBlockX() != 1 || volume.stepBlockY() != 1 || volume.stepBlockZ() != 1) {
            return false;
        }
        for (InterpolatedTile tile : this.interpolated) {
            if (tile.passesVolumeThrough(volume)) {
                return false;
            }
        }
        return true;
    }

    private void sampleColumns(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        this.frame.begin(context, volume);
        try {
            float[] column = this.frame.pushTile();
            int sizeY = volume.sizeY();
            for (int z = 0; z < volume.sizeZ(); ++z) {
                for (int x = 0; x < volume.sizeX(); ++x) {
                    this.root.fill(this.frame, column, x, z);
                    System.arraycopy(column, 0, outputBuffer.values, volume.indexUnchecked(x, 0, z), sizeY);
                }
            }
            for (InterpolatedTile tile : this.interpolated) {
                tile.recordCall(this.frame);
            }
        } finally {
            this.frame.end();
        }
    }
}
