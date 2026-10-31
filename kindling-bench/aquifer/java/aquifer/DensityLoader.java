package aquifer;

import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ThreadLocalDensityBufferArena;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/** The real finalDensity values of a volume, as doFill reads them (NBCG:448). */
final class DensityLoader {
    private DensityLoader() {
    }

    static float[] load(World world, DensityVolume volume) {
        ThreadLocalDensityBufferArena arena = ThreadLocalDensityBufferArena.acquire();
        try {
            SamplerContext context = SamplerContext.builder()
                .setUserFields(ContextMap.builder().set(Beardifier.CONTEXT_KEY, Beardifier.EMPTY).build())
                .useBufferArena(arena)
                .enableCaches()
                .build();
            try (ScopedDensityBuffer buffer = world.randomState().samplersWithContext(context).get(world.finalDensity()).sampleVolume(volume)) {
                float[] values = new float[volume.size()];
                System.arraycopy(buffer.values, 0, values, 0, values.length);
                return values;
            }
        } finally {
            ThreadLocalDensityBufferArena.release(arena);
        }
    }
}
