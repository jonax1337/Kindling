package densitycompiler;

import java.util.Arrays;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferArena;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Verify path only. Paper's arena reuses buffers without clearing them, so a variant that forgets to write an
 * element would find the reference's old value there and pass. This arena fills every buffer it hands out with a
 * poison value first. The poison is a finite float, not a NaN, because the verifier accepts NaN against NaN.
 */
final class PoisonedArena implements DensityBufferArena {
    static final int POISON_BITS = 0x7f7fdead;
    private static final float POISON = Float.intBitsToFloat(POISON_BITS);

    private final DensityBufferArena arena;

    PoisonedArena(DensityBufferArena arena) {
        this.arena = arena;
    }

    @Override
    public ScopedDensityBuffer acquire(int size) {
        ScopedDensityBuffer buffer = this.arena.acquire(size);
        Arrays.fill(buffer.values, POISON);
        return buffer;
    }

    @Override
    public void release(ScopedDensityBuffer buffer) {
        this.arena.release(buffer);
    }
}
