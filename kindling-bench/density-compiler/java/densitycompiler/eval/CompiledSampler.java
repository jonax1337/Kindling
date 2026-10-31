package densitycompiler.eval;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * finalDensity through the evaluator. The corner volumes of the interpolated functions always go through the node
 * graph. Above them, the block level is either Paper's own sampler tree rebuilt around the corner samplers (step 1)
 * or, with tiling, the column-tiled block stage. One instance per world and thread: nodes, tiles and frames hold
 * per-call state, so a second thread would corrupt it. The first thread to call sampleVolume owns the instance and
 * any other thread fails loudly instead of racing. Paper shares one sampler per function between all workers, so
 * this class must not replace it there until the state moves into a per-call frame (DESIGN, review point 7).
 */
public final class CompiledSampler implements DensitySampler {
    private final DensitySampler paper;
    private final DensitySampler blockLevel;
    private final List<PruneCounter> counters;
    private Thread owner;

    private CompiledSampler(DensitySampler paper, DensitySampler blockLevel, List<PruneCounter> counters) {
        this.paper = paper;
        this.blockLevel = blockLevel;
        this.counters = counters;
    }

    public static CompiledSampler compile(DensitySampler paper, Techniques techniques, Tuning tuning) {
        TreeBuilder corners = new TreeBuilder();
        BlockLevelRebuild rebuild = new BlockLevelRebuild(corners, new Frame(techniques.pruning(), tuning));
        List<PruneCounter> counters = new ArrayList<>();
        DensitySampler blockLevel;
        if (techniques.tiling()) {
            TileBuilder tiles = new TileBuilder(rebuild);
            blockLevel = tiles.build(paper, new TileFrame(techniques.pruning()));
            counters.addAll(tiles.counters());
        } else {
            blockLevel = rebuild.rebuild(paper);
        }
        counters.addAll(corners.counters());
        return new CompiledSampler(paper, blockLevel, List.copyOf(counters));
    }

    public List<PruneCounter> counters() {
        return this.counters;
    }

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        this.claim();
        this.blockLevel.sampleVolume(context, outputBuffer, volume);
    }

    private void claim() {
        Thread current = Thread.currentThread();
        if (this.owner == null) {
            this.owner = current;
        } else if (this.owner != current) {
            throw new IllegalStateException("CompiledSampler is confined to " + this.owner.getName() + " but was called from " + current.getName());
        }
    }

    @Override
    public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
        return this.paper.sampleValue(context, blockX, blockY, blockZ);
    }
}
