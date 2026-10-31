package net.kindlingmc.worldgen.density;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * SliceFunction's X, Y, Z and Xz samplers: the input runs on the volume Paper builds (the collapsed axes set to the
 * slice coordinate, sizes 1, same steps), or on the region itself when it already is that volume, as in Paper.
 * The demand is projected (a collapsed row is needed if any of its points is), the result broadcast back.
 */
final class SliceNode extends Node {
    private final Node input;
    private final boolean collapseX;
    private final boolean collapseY;
    private final boolean collapseZ;
    private final int x;
    private final int y;
    private final int z;

    /** Coordinates of axes that are not collapsed are ignored. */
    SliceNode(Node input, boolean[] collapse, int[] coordinate) {
        this.input = input;
        this.collapseX = collapse[0];
        this.collapseY = collapse[1];
        this.collapseZ = collapse[2];
        this.x = coordinate[0];
        this.y = coordinate[1];
        this.z = coordinate[2];
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        DensityVolume slice = this.sliceOf(region);
        if (slice.equals(region)) {
            this.input.evaluate(frame, region, out, demand);
            return;
        }
        Demand projected = frame.push(slice.size());
        try (ScopedDensityBuffer sliceBuffer = frame.acquire(slice)) {
            this.project(region, slice, demand, projected);
            this.input.evaluate(frame, slice, sliceBuffer, projected);
            this.broadcast(region, slice, sliceBuffer.values, out.values);
        } finally {
            frame.pop(1);
        }
    }

    @Override
    Node[] children() {
        return new Node[] {this.input};
    }

    /** The slice applied after the given one: the collapsed axes of both, this slice's coordinate where it collapses. */
    RegionPath then(RegionPath outer) {
        return outer.then(this.collapseX, this.collapseY, this.collapseZ, this.x, this.y, this.z);
    }

    DensityVolume sliceOf(DensityVolume region) {
        return new DensityVolume(
            this.collapseX ? 1 : region.sizeX(),
            this.collapseY ? 1 : region.sizeY(),
            this.collapseZ ? 1 : region.sizeZ(),
            this.collapseX ? this.x : region.minBlockX(),
            this.collapseY ? this.y : region.minBlockY(),
            this.collapseZ ? this.z : region.minBlockZ(),
            region.stepBlockX(),
            region.stepBlockY(),
            region.stepBlockZ()
        );
    }

    private void project(DensityVolume region, DensityVolume slice, Demand demand, Demand projected) {
        boolean[] needed = demand.mask;
        boolean[] mask = projected.mask;
        java.util.Arrays.fill(mask, 0, slice.size(), false);
        int index = 0;
        for (int iz = 0; iz < region.sizeZ(); ++iz) {
            for (int ix = 0; ix < region.sizeX(); ++ix) {
                for (int iy = 0; iy < region.sizeY(); ++iy) {
                    if (needed[index++]) {
                        mask[this.sliceIndex(slice, ix, iy, iz)] = true;
                    }
                }
            }
        }
        int count = 0;
        for (int i = 0; i < slice.size(); ++i) {
            count += mask[i] ? 1 : 0;
        }
        projected.count = count;
    }

    private void broadcast(DensityVolume region, DensityVolume slice, float[] sliceValues, float[] v) {
        int index = 0;
        for (int iz = 0; iz < region.sizeZ(); ++iz) {
            for (int ix = 0; ix < region.sizeX(); ++ix) {
                for (int iy = 0; iy < region.sizeY(); ++iy) {
                    v[index++] = sliceValues[this.sliceIndex(slice, ix, iy, iz)];
                }
            }
        }
    }

    private int sliceIndex(DensityVolume slice, int ix, int iy, int iz) {
        return slice.indexUnchecked(this.collapseX ? 0 : ix, this.collapseY ? 0 : iy, this.collapseZ ? 0 : iz);
    }
}
