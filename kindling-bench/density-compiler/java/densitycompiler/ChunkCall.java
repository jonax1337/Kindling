package densitycompiler;

import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ThreadLocalDensityBufferArena;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferArena;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import org.jspecify.annotations.Nullable;

/**
 * One density call of NoiseBasedChunkGenerator.doFill: thread-local arena, the context NoiseChunk builds, one
 * sampleVolume over the volume. Context creation is part of the call because the server pays it per chunk too.
 */
final class ChunkCall {
    private static final int SINK_STRIDE = 1531;

    private ChunkCall() {
    }

    /** Runs the call and returns a few output bits, so the JIT cannot drop the work. */
    static int run(DensitySampler sampler, boolean caches, Chunk chunk) {
        return execute(sampler, caches, chunk, null, null);
    }

    /**
     * Runs the call with every buffer poisoned first (see PoisonedArena) and copies every output float, in volume
     * order, into target. If probeTarget is not null, the point queries of Probe on the call's context follow.
     */
    static void runInto(DensitySampler sampler, boolean caches, Chunk chunk, float[] target, float @Nullable [] probeTarget) {
        execute(sampler, caches, chunk, target, probeTarget);
    }

    /** A context as NoiseChunk builds it, without a blender (blending is off in this harness). */
    static SamplerContext context(@Nullable Beardifier beardifier, DensityBufferArena arena, boolean caches) {
        SamplerContext.Builder builder = SamplerContext.builder()
            .setUserFields(ContextMap.builder().set(Beardifier.CONTEXT_KEY, beardifier).build())
            .useBufferArena(arena);
        return (caches ? builder.enableCaches() : builder).build();
    }

    private static int execute(DensitySampler sampler, boolean caches, Chunk chunk, float @Nullable [] target, float @Nullable [] probeTarget) {
        DensityVolume volume = chunk.volume();
        ThreadLocalDensityBufferArena arena = ThreadLocalDensityBufferArena.acquire();
        try {
            SamplerContext context = context(chunk.beardifier(), target == null ? arena : new PoisonedArena(arena), caches);
            try (ScopedDensityBuffer output = context.acquireBuffer(volume)) {
                sampler.sampleVolume(context, output, volume);
                if (target != null) {
                    System.arraycopy(output.values, 0, target, 0, volume.size());
                }
                if (probeTarget != null) {
                    Probe.sample(chunk, context, probeTarget);
                }
                return sink(output.values, volume.size());
            }
        } finally {
            ThreadLocalDensityBufferArena.release(arena);
        }
    }

    private static int sink(float[] values, int size) {
        int bits = 0;
        for (int i = 0; i < size; i += SINK_STRIDE) {
            bits ^= Float.floatToRawIntBits(values[i]);
        }
        return bits;
    }
}
