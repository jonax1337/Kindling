package net.kindlingmc.worldgen.density;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.CachingDensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * finalDensity with the inputs of its interpolated functions replaced by corner samplers. Above them it is Paper's
 * own sampler tree, rebuilt record by record, so the block level, the interpolation and everything without an
 * interpolated function stay Paper's code. A call with an active blender goes to Paper's sampler as a whole, and so
 * does every point query. Built once per RandomState; immutable, shared by all chunk workers.
 */
final class CompiledFinalDensity implements DensitySampler {
    private static final Class<?> INTERPOLATED = SamplerRecords.type("net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction$Sampler");

    final DensitySampler paper;
    private final DensitySampler blockLevel;
    /**
     * Every cache a corner sampler leaves a cell for: those of the node graphs and those inside the touching leaves it
     * replays (a spline's coordinates, say). Verify mode and tests compare all of their cells.
     */
    final CachingDensitySampler[] caches;
    final int corners;
    final int interpolated;

    private CompiledFinalDensity(DensitySampler paper, DensitySampler blockLevel, CachingDensitySampler[] caches, int corners, int interpolated) {
        this.paper = paper;
        this.blockLevel = blockLevel;
        this.caches = caches;
        this.corners = corners;
        this.interpolated = interpolated;
    }

    /** The compiled sampler, or paper itself when no interpolated function can be compiled. */
    static DensitySampler compile(DensitySampler paper, Tuning tuning) {
        Map<DensitySampler, Node> roots = new IdentityHashMap<>();
        collectInterpolated(paper, roots, new IdentityHashMap<>());
        TreeBuilder builder = new TreeBuilder();
        Map<DensitySampler, CornerPlan> plans = new IdentityHashMap<>();
        for (Map.Entry<DensitySampler, Node> entry : roots.entrySet()) {
            Node root = builder.build(input(entry.getKey()));
            entry.setValue(root);
            CornerPlan plan = CornerPlan.of(root);
            if (plan != null) {
                plans.put(entry.getKey(), plan);
            }
        }
        if (plans.isEmpty()) {
            return paper;
        }
        Map<DensitySampler, DensitySampler> replaced = new IdentityHashMap<>();
        Map<Integer, CachingDensitySampler> caches = new LinkedHashMap<>();
        for (Map.Entry<DensitySampler, CornerPlan> entry : plans.entrySet()) {
            DensitySampler interpolated = entry.getKey();
            DensitySampler input = input(interpolated);
            CornerPlan plan = entry.getValue();
            CornerSampler corner = new CornerSampler(roots.get(interpolated), input, plan, tuning, builder.touchingLeaves());
            replaced.put(interpolated, SamplerRecords.rebuild(interpolated, child -> child == input ? corner : child));
            for (CacheNode cache : plan.caches) {
                caches.putIfAbsent(cache.cacheId, new CachingDensitySampler(cache.cacheId, cache.paperInput));
            }
            for (OpaqueNode leaf : plan.leaves) {
                CacheScan.collect(leaf.paper, caches);
            }
        }
        DensitySampler blockLevel = rebuild(paper, replaced, new IdentityHashMap<>());
        return new CompiledFinalDensity(paper, blockLevel, caches.values().toArray(new CachingDensitySampler[0]), plans.size(), roots.size());
    }

    private static DensitySampler input(DensitySampler interpolated) {
        return (DensitySampler)SamplerRecords.component(interpolated, "input");
    }

    /** The interpolated samplers of the block level (not those inside another one's input). */
    private static void collectInterpolated(DensitySampler sampler, Map<DensitySampler, Node> found, Map<DensitySampler, Boolean> seen) {
        if (seen.put(sampler, Boolean.TRUE) != null) {
            return;
        }
        if (sampler.getClass() == INTERPOLATED) {
            found.put(sampler, null);
        } else if (SamplerRecords.isRecord(sampler)) {
            for (DensitySampler child : SamplerRecords.children(sampler)) {
                collectInterpolated(child, found, seen);
            }
        }
    }

    private static DensitySampler rebuild(DensitySampler sampler, Map<DensitySampler, DensitySampler> replaced, Map<DensitySampler, DensitySampler> done) {
        DensitySampler result = done.get(sampler);
        if (result == null) {
            result = replaced.get(sampler);
            if (result == null) {
                result = SamplerRecords.isRecord(sampler) && sampler.getClass() != INTERPOLATED
                    ? SamplerRecords.rebuild(sampler, child -> rebuild(child, replaced, done))
                    : sampler;
            }
            done.put(sampler, result);
        }
        return result;
    }

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        Blender blender = context.getField(Blender.CONTEXT_KEY);
        if (blender != null && !blender.isEmpty()) {
            this.paper.sampleVolume(context, outputBuffer, volume);
        } else {
            this.blockLevel.sampleVolume(context, outputBuffer, volume);
        }
    }

    @Override
    public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
        return this.paper.sampleValue(context, blockX, blockY, blockZ);
    }
}
