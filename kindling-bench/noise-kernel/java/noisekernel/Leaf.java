package noisekernel;

import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import net.minecraft.world.level.levelgen.synth.GradientNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import net.minecraft.world.level.levelgen.synth.SmearedPerlinNoise;

/** A leaf noise object together with the non-public state the vector and native kernels read once at setup. */
final class Leaf {
    final Kind kind;
    final PerlinNoise noise;
    final char[] pAdj;
    final MemorySegment pAdjSegment;
    final double offsetX;
    final double offsetY;
    final double offsetZ;
    final double fudgeYScale;

    private Leaf(Kind kind, PerlinNoise noise) {
        this.kind = kind;
        this.noise = noise;
        this.pAdj = noise.moonrise$getPAdj();
        this.pAdjSegment = MemorySegment.ofArray(this.pAdj);
        this.offsetX = readDouble(GradientNoise.class, "offsetX", noise);
        this.offsetY = readDouble(GradientNoise.class, "offsetY", noise);
        this.offsetZ = readDouble(GradientNoise.class, "offsetZ", noise);
        this.fudgeYScale = kind == Kind.SMEARED ? readDouble(SmearedPerlinNoise.class, "fudgeYScale", noise) : 0.0;
    }

    static Leaf of(PerlinNoise noise) {
        return new Leaf(kindOf(noise), noise);
    }

    private static Kind kindOf(PerlinNoise noise) {
        if (noise.getClass() == SmearedPerlinNoise.class) {
            return Kind.SMEARED;
        }
        if (noise.getClass() == PerlinNoise.class) {
            return Kind.PERLIN;
        }
        throw new IllegalArgumentException("Unsupported leaf class " + noise.getClass().getName());
    }

    private static double readDouble(Class<?> owner, String name, Object target) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.getDouble(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + owner.getSimpleName() + "." + name, e);
        }
    }
}
