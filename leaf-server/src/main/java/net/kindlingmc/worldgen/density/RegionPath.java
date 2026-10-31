package net.kindlingmc.worldgen.density;

import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/**
 * Where a node sits below the corner sampler: which axes the slices on the way collapse, and to which coordinate.
 * Applied to the corner volume it gives the exact region the node is evaluated on, as Paper's slice samplers build it.
 */
record RegionPath(boolean collapseX, boolean collapseY, boolean collapseZ, int x, int y, int z) {
    static final RegionPath ROOT = new RegionPath(false, false, false, 0, 0, 0);

    /** This path followed by one more slice (the inner slice sets the coordinate of the axes it collapses). */
    RegionPath then(boolean sliceX, boolean sliceY, boolean sliceZ, int sliceAtX, int sliceAtY, int sliceAtZ) {
        return new RegionPath(
            this.collapseX || sliceX, this.collapseY || sliceY, this.collapseZ || sliceZ,
            sliceX ? sliceAtX : this.x, sliceY ? sliceAtY : this.y, sliceZ ? sliceAtZ : this.z
        );
    }

    DensityVolume apply(DensityVolume volume) {
        if (!this.collapseX && !this.collapseY && !this.collapseZ) {
            return volume;
        }
        return new DensityVolume(
            this.collapseX ? 1 : volume.sizeX(),
            this.collapseY ? 1 : volume.sizeY(),
            this.collapseZ ? 1 : volume.sizeZ(),
            this.collapseX ? this.x : volume.minBlockX(),
            this.collapseY ? this.y : volume.minBlockY(),
            this.collapseZ ? this.z : volume.minBlockZ(),
            volume.stepBlockX(),
            volume.stepBlockY(),
            volume.stepBlockZ()
        );
    }
}
