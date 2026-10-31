package densitycompiler.eval;

import java.util.Arrays;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Per-thread state of one block-stage call: the context and volume, a call number that tiles use to compute their
 * per-call inputs once, a stack of column tiles, and every arena buffer held until the call ends. Tiles and held
 * buffers come from the context's arena, so the verify path poisons them. begin resets the tile stack, so tiles
 * pop without try/finally.
 */
final class TileFrame {
    private final boolean pruning;
    private SamplerContext context;
    private DensityVolume volume;
    private DensityVolume tileVolume;
    private long call;
    private ScopedDensityBuffer[] held = new ScopedDensityBuffer[16];
    private int heldCount;
    private float[][] tiles = new float[8][];
    private int tileCount;
    private int tileDepth;

    TileFrame(boolean pruning) {
        this.pruning = pruning;
    }

    void begin(SamplerContext context, DensityVolume volume) {
        this.context = context;
        this.volume = volume;
        this.tileVolume = new DensityVolume(1, volume.sizeY(), 1, 0, 0, 0);
        ++this.call;
        this.tileCount = 0;
        this.tileDepth = 0;
    }

    /** Releases every buffer the call held, newest first, and drops the references to them. */
    void end() {
        while (this.heldCount > 0) {
            ScopedDensityBuffer buffer = this.held[--this.heldCount];
            this.held[this.heldCount] = null;
            buffer.close();
        }
        Arrays.fill(this.tiles, 0, this.tileCount, null);
        this.context = null;
    }

    /** False for COMPILED_TILE: every branch of a block-level selector is evaluated in every column, as Paper does. */
    boolean pruning() {
        return this.pruning;
    }

    SamplerContext context() {
        return this.context;
    }

    DensityVolume volume() {
        return this.volume;
    }

    long call() {
        return this.call;
    }

    /** A buffer that stays valid until the call ends. */
    ScopedDensityBuffer hold(DensityVolume region) {
        if (this.heldCount == this.held.length) {
            this.held = Arrays.copyOf(this.held, this.heldCount * 2);
        }
        ScopedDensityBuffer buffer = this.context.acquireBuffer(region);
        this.held[this.heldCount++] = buffer;
        return buffer;
    }

    /** A free column tile of at least sizeY floats; release it with popTile. */
    float[] pushTile() {
        if (this.tileDepth == this.tileCount) {
            if (this.tileCount == this.tiles.length) {
                this.tiles = Arrays.copyOf(this.tiles, this.tileCount * 2);
            }
            this.tiles[this.tileCount++] = this.hold(this.tileVolume).values;
        }
        return this.tiles[this.tileDepth++];
    }

    void popTile() {
        --this.tileDepth;
    }
}
