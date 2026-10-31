package noisekernel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import net.minecraft.world.level.levelgen.synth.SmearedPerlinNoise;

/** The private vanilla addToVolumeVanilla kept by Paper for parity checks. Verify mode only. */
final class VanillaVariant implements Variant {
    private final Method perlin = vanillaMethod(PerlinNoise.class);
    private final Method smeared = vanillaMethod(SmearedPerlinNoise.class);

    @Override
    public String name() {
        return "VANILLA";
    }

    @Override
    public void run(KernelCall call, Work work) {
        Method method = call.kind() == Kind.SMEARED ? this.smeared : this.perlin;
        try {
            method.invoke(call.leaf().noise, work.buffer, call.volume(), call.xzScale(), call.yScale(), call.amplitude());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    @Override
    public void runAll(KernelCall[] calls, Work work) {
        for (KernelCall call : calls) {
            this.run(call, work);
        }
    }

    private static Method vanillaMethod(Class<?> owner) {
        try {
            Method method = owner.getDeclaredMethod(
                "addToVolumeVanilla", DensityBuffer.class, DensityVolume.class, double.class, double.class, float.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No addToVolumeVanilla in " + owner.getSimpleName(), e);
        }
    }
}
