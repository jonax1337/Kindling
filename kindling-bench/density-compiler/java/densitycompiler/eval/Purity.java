package densitycompiler.eval;

import java.util.Set;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.GradientFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction;

/**
 * Which Paper leaves may be called on a sub-volume: a sampler is volume-pure when the value it writes for a point
 * depends on that point only. Whitelist by declaring class (DESIGN, "Purity"): noises (coordinates per axis in
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
}
