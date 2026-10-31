package densitycompiler.eval;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;

/**
 * The output buffer of sub-volume leaf calls. It overrides no DensityBuffer method, so Paper's call sites on
 * get/set/fill stay monomorphic (DESIGN, open review point 6).
 */
final class ResizableBuffer extends DensityBuffer {
    ResizableBuffer(int capacity) {
        super(capacity);
    }

    void resize(int size) {
        this.size = size;
    }
}
