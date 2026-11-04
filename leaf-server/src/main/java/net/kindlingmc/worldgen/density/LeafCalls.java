package net.kindlingmc.worldgen.density;

import java.util.Arrays;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Splits a partial demand on a volume-pure Paper leaf into sub-volume calls: the bounding box of the demand, or the
 * y-runs of each column. scan() measures both; the caller picks by cost and calls box() or runs(). Reused per frame.
 */
final class LeafCalls {
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private int runCount;
    private int runPoints;
    /** Per run: column x, column z, first y, length. */
    private int[] runs = new int[64];

    void scan(DensityVolume region, Demand demand, int runGap) {
        this.minX = this.minY = this.minZ = Integer.MAX_VALUE;
        this.maxX = this.maxY = this.maxZ = -1;
        this.runCount = 0;
        this.runPoints = 0;
        int sizeY = region.sizeY();
        for (int iz = 0; iz < region.sizeZ(); ++iz) {
            for (int ix = 0; ix < region.sizeX(); ++ix) {
                this.scanColumn(demand.mask, region.indexUnchecked(ix, 0, iz), sizeY, ix, iz, runGap);
            }
        }
    }

    private void scanColumn(boolean[] mask, int base, int sizeY, int ix, int iz, int runGap) {
        int y = 0;
        while (y < sizeY) {
            if (!mask[base + y]) {
                ++y;
                continue;
            }
            int start = y;
            int end = y;
            ++y;
            while (y < sizeY && (mask[base + y] || y - end <= runGap)) {
                if (mask[base + y]) {
                    end = y;
                }
                ++y;
            }
            this.addRun(ix, iz, start, end - start + 1);
        }
    }

    private void addRun(int ix, int iz, int startY, int length) {
        int at = this.runCount * 4;
        if (at + 4 > this.runs.length) {
            this.runs = Arrays.copyOf(this.runs, this.runs.length * 2);
        }
        this.runs[at] = ix;
        this.runs[at + 1] = iz;
        this.runs[at + 2] = startY;
        this.runs[at + 3] = length;
        ++this.runCount;
        this.runPoints += length;
        this.minX = Math.min(this.minX, ix);
        this.maxX = Math.max(this.maxX, ix);
        this.minZ = Math.min(this.minZ, iz);
        this.maxZ = Math.max(this.maxZ, iz);
        this.minY = Math.min(this.minY, startY);
        this.maxY = Math.max(this.maxY, startY + length - 1);
    }

    int boxPoints() {
        return (this.maxX - this.minX + 1) * (this.maxY - this.minY + 1) * (this.maxZ - this.minZ + 1);
    }

    int runPoints() {
        return this.runPoints;
    }

    int runCount() {
        return this.runCount;
    }

    /** One Paper call on the bounding box, scattered into out. */
    void box(Frame frame, DensitySampler paper, DensityVolume region, DensityBuffer out) {
        int sizeX = this.maxX - this.minX + 1;
        int sizeY = this.maxY - this.minY + 1;
        int sizeZ = this.maxZ - this.minZ + 1;
        DensityVolume box = new DensityVolume(sizeX, sizeY, sizeZ, region.blockX(this.minX), region.blockY(this.minY), region.blockZ(this.minZ),
            region.stepBlockX(), region.stepBlockY(), region.stepBlockZ());
        ResizableBuffer buffer = frame.leafBuffer(box.size());
        paper.sampleVolume(frame.context(), buffer, box);
        for (int iz = 0; iz < sizeZ; ++iz) {
            for (int ix = 0; ix < sizeX; ++ix) {
                System.arraycopy(buffer.values, box.indexUnchecked(ix, 0, iz), out.values,
                    region.indexUnchecked(this.minX + ix, this.minY, this.minZ + iz), sizeY);
            }
        }
    }

    /** One Paper call per recorded run, each a 1 x length x 1 volume. */
    void runs(Frame frame, DensitySampler paper, DensityVolume region, DensityBuffer out) {
        SamplerContext context = frame.context();
        for (int r = 0; r < this.runCount; ++r) {
            int ix = this.runs[r * 4];
            int iz = this.runs[r * 4 + 1];
            int startY = this.runs[r * 4 + 2];
            int length = this.runs[r * 4 + 3];
            DensityVolume run = new DensityVolume(1, length, 1, region.blockX(ix), region.blockY(startY), region.blockZ(iz),
                region.stepBlockX(), region.stepBlockY(), region.stepBlockZ());
            ResizableBuffer buffer = frame.leafBuffer(length);
            paper.sampleVolume(context, buffer, run);
            System.arraycopy(buffer.values, 0, out.values, region.indexUnchecked(ix, startY, iz), length);
        }
    }
}
