package aquifer;

import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ThreadLocalDensityBufferArena;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import org.jspecify.annotations.Nullable;

/**
 * The environment of one chunk's aquifer as NoiseChunk builds it: a thread-local buffer arena and one sampler
 * context with caches, shared by the aquifer and finalDensity. With tracing on, the aquifer sees sampler and
 * picker wrappers that log every call.
 */
final class AquiferRun implements AutoCloseable {
    private final World world;
    private final ThreadLocalDensityBufferArena arena;
    private final DensitySamplerSet plainSamplers;
    private final @Nullable TraceLogs trace;
    private @Nullable ScopedDensityBuffer densityBuffer;

    private AquiferRun(World world, ThreadLocalDensityBufferArena arena, DensitySamplerSet plainSamplers, @Nullable TraceLogs trace) {
        this.world = world;
        this.arena = arena;
        this.plainSamplers = plainSamplers;
        this.trace = trace;
    }

    static AquiferRun open(World world, boolean traced) {
        ThreadLocalDensityBufferArena arena = ThreadLocalDensityBufferArena.acquire();
        SamplerContext context = SamplerContext.builder()
            .setUserFields(ContextMap.builder().set(Beardifier.CONTEXT_KEY, Beardifier.EMPTY).build())
            .useBufferArena(arena)
            .enableCaches()
            .build();
        return new AquiferRun(world, arena, world.randomState().samplersWithContext(context), traced ? TraceLogs.create() : null);
    }

    /** Builds the aquifer the way NoiseChunk does, from the world's config, random factory and fluid picker. */
    Aquifer construct(Variant variant, DensityVolume volume) {
        AquiferInputs inputs = this.trace == null ? this.plainInputs(volume) : this.tracedInputs(volume, this.trace);
        return variant.create(inputs);
    }

    /**
     * The finalDensity call doFill makes after the aquifer exists; it fills the context caches the aquifer's functions
     * read. As in doFill, the buffer stays acquired until the run is closed. The values are not needed here.
     */
    void fillDensityCaches(DensityVolume volume) {
        this.densityBuffer = this.plainSamplers.get(this.world.finalDensity()).sampleVolume(volume);
    }

    @Nullable TraceLogs trace() {
        return this.trace;
    }

    @Override
    public void close() {
        if (this.densityBuffer != null) {
            this.densityBuffer.close();
        }
        ThreadLocalDensityBufferArena.release(this.arena);
    }

    private AquiferInputs plainInputs(DensityVolume volume) {
        return new AquiferInputs(this.plainSamplers, this.world.config(), this.world.aquiferRandom(), volume, this.world.fluidPicker());
    }

    private AquiferInputs tracedInputs(DensityVolume volume, TraceLogs logs) {
        DensitySamplerSet samplers = new TracingSamplerSet(this.plainSamplers, this.world.config(), logs.samplers());
        Aquifer.FluidPicker picker = new TracingFluidPicker(this.world.fluidPicker(), logs.picker());
        return new AquiferInputs(samplers, this.world.config(), this.world.aquiferRandom(), volume, picker);
    }
}
