package net.kindlingmc.worldgen.density;

import java.util.Set;
import net.minecraft.world.level.levelgen.densityfunction.CachingDensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.ContextBoundSampler;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.GradientFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction;

/**
 * Which Paper leaves may be called on a sub-volume: a sampler is volume-pure when the value it writes for a point
 * depends on that point only. Whitelist by declaring class (prototype DESIGN.md, "Purity"): noises (coordinates per axis in
 * double from the volume minimum, exact for integer blocks), gradients, constants and element-wise ops, each only
 * if all its child samplers are volume-pure. Anything else, splines and context-bound samplers included, is not.
 */
final class Purity {
    private static final Set<Class<?>> PURE_FAMILIES = Set.of(NoiseFunction.class, GradientFunction.class, ConstantFunction.class,
        BinaryFunction.class, UnaryFunction.class, ClampFunction.class);

    private Purity() {
    }

    static boolean isVolumePure(DensitySampler sampler) {
        Class<?> type = sampler.getClass();
        Class<?> family = type.getEnclosingClass();
        if (!type.isRecord() || family == null || !PURE_FAMILIES.contains(family) || SamplerRecords.hasSamplerArray(sampler)) {
            return false;
        }
        for (DensitySampler child : SamplerRecords.children(sampler)) {
            if (!isVolumePure(child)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether evaluating the sampler may read or fill a cache cell of the context: it is or contains a
     * CachingDensitySampler, or it is not a record, so its children cannot be enumerated (splines, unknown types).
     * A context-bound sampler counts by its fallback (the context's own samplers are the beardifier and the blender's).
     */
    static boolean reachesCache(DensitySampler sampler) {
        if (sampler instanceof CachingDensitySampler || !sampler.getClass().isRecord()) {
            return true;
        }
        if (sampler instanceof ContextBoundSampler(var key, DensitySampler fallback)) {
            return reachesCache(fallback);
        }
        for (Object component : SamplerRecords.components(sampler)) {
            if (component instanceof DensitySampler child && reachesCache(child)) {
                return true;
            }
            if (component instanceof DensitySampler[] children) {
                for (DensitySampler child : children) {
                    if (reachesCache(child)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
