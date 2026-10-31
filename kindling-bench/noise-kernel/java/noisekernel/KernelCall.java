package noisekernel;

import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** One recorded leaf call: exactly the arguments the server passes to the leaf's addToVolume. */
record KernelCall(Leaf leaf, DensityVolume volume, double xzScale, double yScale, float amplitude) {
    Kind kind() {
        return this.leaf.kind;
    }

    int samples() {
        return this.volume.size();
    }
}
