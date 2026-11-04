package net.kindlingmc.worldgen.density;

import java.util.Arrays;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import org.jspecify.annotations.Nullable;

/**
 * The state of one corner-volume evaluation: the context, the demand stack, the scratch of sub-volume leaf calls and
 * the stamps of the leaves evaluated in this call. One frame per thread is reused; it holds no reference to a context
 * or a buffer between calls, so it keeps no chunk alive and is the same for every world. A call that finds the
 * frame of its thread busy gets a fresh one.
 */
final class Frame {
    private static final ThreadLocal<Frame> OF_THREAD = ThreadLocal.withInitial(Frame::new);

    private @Nullable SamplerContext context;
    private Tuning tuning = Tuning.DEFAULT;
    private Demand[] demands = new Demand[0];
    private int depth;
    private ResizableBuffer leafBuffer = new ResizableBuffer(0);
    private final LeafCalls leafCalls = new LeafCalls();
    private long call;
    private long[] stamps = new long[0];

    private Frame() {
    }

    /** The frame for one call on this thread; release it with {@link #exit()}. */
    static Frame enter(SamplerContext context, Tuning tuning, int stampCount) {
        Frame frame = OF_THREAD.get();
        if (frame.context != null) {
            frame = new Frame();
        }
        frame.context = context;
        frame.tuning = tuning;
        ++frame.call;
        if (frame.stamps.length < stampCount) {
            frame.stamps = Arrays.copyOf(frame.stamps, stampCount);
        }
        return frame;
    }

    /** Drops the context, so nothing of the chunk stays reachable from the frame. */
    void exit() {
        this.context = null;
        this.depth = 0;
    }

    SamplerContext context() {
        return this.context;
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

    void stamp(int index) {
        this.stamps[index] = this.call;
    }

    boolean stamped(int index) {
        return this.stamps[index] == this.call;
    }
}
