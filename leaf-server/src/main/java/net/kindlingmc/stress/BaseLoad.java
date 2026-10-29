package net.kindlingmc.stress;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * The base plots of a stress test, in a grid east of the centre of the test world, beyond the widest mob area. Toward
 * the target it reserves one
 * plot at a time and builds it once its chunks have loaded, or clears one plot a tick; it steps the clocks every tick.
 * The clocks of different plots are not in step, as on a server.
 */
final class BaseLoad {

    private static final int PLOTS_PER_ROW = 4;
    private static final int FIRST_CHUNK_X = 10;
    private static final int CHUNK_GAP = 1;
    private static final int ROW_CHUNKS = 2;

    private final World world;
    private final KeptChunks kept;
    private final List<BasePlot> plots = new ArrayList<>();
    private @Nullable BasePlot reserved;
    private int target;

    BaseLoad(final World world, final KeptChunks kept) {
        this.world = world;
        this.kept = kept;
    }

    void aimAt(final int count) {
        this.target = count;
    }

    void tick(final long tick) {
        if (this.plots.size() < this.target) {
            this.buildNext();
        } else {
            // A plot reserved for a larger target must not be built later on top of another one.
            this.reserved = null;
            if (this.plots.size() > this.target) {
                this.plots.removeLast().clear();
            }
        }
        for (int i = 0; i < this.plots.size(); i++) {
            if ((tick + i) % BasePlot.CLOCK_TICKS == 0) {
                this.plots.get(i).clockStep();
            }
        }
    }

    private void buildNext() {
        if (this.reserved == null) {
            this.reserved = this.plotAt(this.plots.size());
            this.reserved.reserve();
        } else if (this.reserved.isReady()) {
            this.reserved.build();
            this.plots.add(this.reserved);
            this.reserved = null;
        }
    }

    private BasePlot plotAt(final int index) {
        final int firstChunkX = FIRST_CHUNK_X + index % PLOTS_PER_ROW * (BasePlot.CHUNKS + CHUNK_GAP);
        return new BasePlot(this.world, this.kept, firstChunkX, index / PLOTS_PER_ROW * ROW_CHUNKS);
    }

    int count() {
        return this.plots.size();
    }

    int target() {
        return this.target;
    }
}
