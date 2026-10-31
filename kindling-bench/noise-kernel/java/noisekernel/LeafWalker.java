package noisekernel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NoiseStack;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

/**
 * Walks NoiseStack layers down to the leaf noises and records one KernelCall per leaf, scaling
 * exactly like NoiseStack.addToVolume (xzScale * frequency, yScale * frequency, amplitude * layerAmplitude).
 */
final class LeafWalker {
    private static final String LAYER_CLASS = "net.minecraft.world.level.levelgen.synth.NoiseStack$Layer";

    private final Field layersField = field(NoiseStack.class, "layers");
    private final Method layerNoise;
    private final Method layerFrequency;
    private final Method layerAmplitude;
    private final Map<PerlinNoise, Leaf> leaves = new IdentityHashMap<>();

    LeafWalker() {
        Class<?> layer = layerClass();
        this.layerNoise = method(layer, "noise");
        this.layerFrequency = method(layer, "frequency");
        this.layerAmplitude = method(layer, "amplitude");
    }

    void record(Noise noise, DensityVolume volume, double xzScale, double yScale, float amplitude, List<KernelCall> out) {
        if (noise instanceof NoiseStack stack) {
            this.recordStack(stack, volume, xzScale, yScale, amplitude, out);
        } else if (noise instanceof PerlinNoise leaf) {
            Leaf resolved = this.leaves.computeIfAbsent(leaf, Leaf::of);
            out.add(new KernelCall(resolved, volume, xzScale, yScale, amplitude));
        } else {
            throw new IllegalArgumentException("Unsupported noise " + noise.getClass().getName());
        }
    }

    List<Leaf> leaves() {
        return List.copyOf(this.leaves.values());
    }

    private void recordStack(NoiseStack stack, DensityVolume volume, double xzScale, double yScale, float amplitude, List<KernelCall> out) {
        try {
            for (Object layer : (Object[])this.layersField.get(stack)) {
                double frequency = (double)this.layerFrequency.invoke(layer);
                float layerAmplitudeValue = (float)this.layerAmplitude.invoke(layer);
                Noise child = (Noise)this.layerNoise.invoke(layer);
                this.record(child, volume, xzScale * frequency, yScale * frequency, amplitude * layerAmplitudeValue, out);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot walk NoiseStack layers", e);
        }
    }

    private static Class<?> layerClass() {
        try {
            return Class.forName(LAYER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(LAYER_CLASS, e);
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(owner.getSimpleName() + "." + name, e);
        }
    }

    private static Method method(Class<?> owner, String name) {
        try {
            Method method = owner.getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(owner.getSimpleName() + "." + name, e);
        }
    }
}
