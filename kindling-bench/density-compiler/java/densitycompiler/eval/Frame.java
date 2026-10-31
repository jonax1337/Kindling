package densitycompiler.eval;

import java.util.Arrays;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Per-thread evaluation state: the context of the current corner call, the demand stack and the buffer of
 * sub-volume leaf calls. Scratch buffers come from the context's arena, as Paper's own nodes get theirs.
 */
final class Frame {
    private final boolean pruning;
    private final Tuning tuning;
    private SamplerContext context;
    private Demand[] demands = new Demand[0];
    private int depth;
    private ResizableBuffer leafBuffer = new ResizableBuffer(0);
    private final LeafCalls leafCalls = new LeafCalls();

    Frame(boolean pruning, Tuning tuning) {
        this.pruning = pruning;
        this.tuning = tuning;
    }

    void bind(SamplerContext context) {
        this.context = context;
    }

    SamplerContext context() {
        return this.context;
    }

    /** False for COMPILED_DENSE: every branch is evaluated on the full demand, as Paper does. */
    boolean pruning() {
        return this.pruning;
    }

    Tuning tuning() {
        return this.tuning;
    }

    ScopedDensityBuffer acquire(DensityVolume region) {
        return this.context.acquireBuffer(region);
    }

    /** An empty demand of the given size on top of the stack; release it with pop. */
    Demand push(int size) {
        if (this.depth == this.demands.length) {
            this.demands = Arrays.copyOf(this.demands, Math.max(8, this.depth * 2));
        }
        Demand demand = this.demands[this.depth];
        if (demand == null) {
            demand = new Demand(size);
            this.demands[this.depth] = demand;
        }
        ++this.depth;
        demand.reset(size);
        return demand;
    }

    void pop(int count) {
        this.depth -= count;
    }

    LeafCalls leafCalls() {
        return this.leafCalls;
    }

    ResizableBuffer leafBuffer(int size) {
        if (this.leafBuffer.capacity() < size) {
            this.leafBuffer = new ResizableBuffer(size);
        }
        this.leafBuffer.resize(size);
        return this.leafBuffer;
    }
}
