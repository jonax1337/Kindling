package densitycompiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import densitycompiler.eval.CompiledSampler;
import densitycompiler.eval.PruneCounter;
import densitycompiler.eval.Techniques;
import densitycompiler.eval.Tuning;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;

/**
 * finalDensity through the evaluator (eval.CompiledSampler), with corner-level pruning and block-level column tiling
 * switched on or off.
 * Keeps the pruning counters of every sampler it built, summed per node over worlds and threads.
 */
final class CompiledVariant implements Variant {
    private static final int TOP_LEAVES = 12;

    private final String name;
    private final Techniques techniques;
    private final Tuning tuning;
    private final boolean pruning;
    private final List<List<PruneCounter>> built = new ArrayList<>();

    private CompiledVariant(String name, Techniques techniques, Tuning tuning) {
        this.name = name;
        this.techniques = techniques;
        this.tuning = tuning;
        this.pruning = techniques.pruning();
    }

    /** Every node on Paper's full volumes: proves the builder and the cache replacement exact. */
    static CompiledVariant dense(Tuning tuning) {
        return new CompiledVariant("COMPILED_DENSE", Techniques.DENSE, tuning);
    }

    /** Range choices, lerps and interval selects evaluate a branch only where Paper keeps its value. */
    static CompiledVariant pruned(Tuning tuning) {
        return new CompiledVariant("COMPILED_PRUNE", Techniques.PRUNE, tuning);
    }

    /** The block level per column in small tiles, every branch evaluated: isolates the tiling gain. */
    static CompiledVariant tiled(Tuning tuning) {
        return new CompiledVariant("COMPILED_TILE", Techniques.TILE, tuning);
    }

    /** Pruning and tiling together. */
    static CompiledVariant full(Tuning tuning) {
        return new CompiledVariant("COMPILED", Techniques.ALL, tuning);
    }

    /** The same variant under another name, so one run can hold it at several tunings. */
    CompiledVariant named(String suffix) {
        return new CompiledVariant(this.name + suffix, this.techniques, this.tuning);
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public boolean cachesEnabled() {
        return true;
    }

    @Override
    public boolean benchmarkedByDefault() {
        return true;
    }

    @Override
    public DensitySampler sampler(World world) {
        CompiledSampler sampler = CompiledSampler.compile(world.paperSampler(), this.techniques, this.tuning);
        synchronized (this.built) {
            this.built.add(sampler.counters());
        }
        return sampler;
    }

    /**
     * Per selector branch and the most expensive leaves when pruning, else only the leaf total (which must show
     * nothing skipped). Call after the threads that used the samplers have ended (verify and bench join them first).
     */
    @Override
    public String diagnostics() {
        Map<String, PruneCounter> totals = this.totals();
        StringBuilder text = new StringBuilder(this.name).append(" pruning counters (summed over worlds and threads):\n");
        PruneCounter leafTotal = null;
        List<PruneCounter> leaves = new ArrayList<>();
        for (PruneCounter counter : totals.values()) {
            if (counter.isLeaf()) {
                leaves.add(counter);
                leafTotal = add(leafTotal, counter);
            } else if (this.pruning && counter.regionPoints() > 0) {
                text.append("  ").append(counter).append('\n');
            }
        }
        if (this.pruning) {
            leaves.sort((a, b) -> Long.compare(b.regionPoints(), a.regionPoints()));
            leaves.stream().limit(TOP_LEAVES).forEach(counter -> text.append("  ").append(counter).append('\n'));
        }
        if (leafTotal != null && leafTotal.regionPoints() > 0) {
            text.append(String.format(Locale.ROOT, "  all %d Paper leaves: %d of %d points evaluated (%.1f %% skipped)%n", leaves.size(),
                leafTotal.evaluatedPoints(), leafTotal.regionPoints(), 100.0 - 100.0 * leafTotal.evaluatedPoints() / leafTotal.regionPoints()));
        }
        return text.toString();
    }

    private Map<String, PruneCounter> totals() {
        Map<String, PruneCounter> totals = new LinkedHashMap<>();
        synchronized (this.built) {
            for (List<PruneCounter> counters : this.built) {
                for (PruneCounter counter : counters) {
                    counter.addTo(totals.computeIfAbsent(counter.label(), label -> counter.emptyCopy()));
                }
            }
        }
        return totals;
    }

    private static PruneCounter add(PruneCounter total, PruneCounter counter) {
        PruneCounter sum = total == null ? counter.emptyCopy() : total;
        counter.addTo(sum);
        return sum;
    }
}
