package net.kindlingmc.preview;

import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ThreadLocalDensityBufferArena;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Supplier;
import net.minecraft.core.QuartPos;
import java.lang.ref.WeakReference;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * The work of the BIOMES and TERRAIN statuses, callable on part of a chunk: noise biomes per section, the density fill
 * of NoiseBasedChunkGenerator.doFill over any volume, and MaterialSystem.buildSurface, for one world (its seed,
 * generator, settings, the RandomState of its chunk map, the chunk container factory, the Level that
 * WorldGenerationContext reads, and the {@link PreviewShape} its chunks are built in). Left out, because they need a WorldGenRegion or structures: carvers and blending; the
 * beardifier is empty, as for a chunk without structures.
 *
 * <p>Thread safety: every field is immutable after construction and every method keeps its state in locals, so
 * several threads may call it at once. RandomState, the generator and the fluid picker are objects the server's own
 * chunk workers share the same way.
 */
final class Terrain {
    private static final MethodHandle DO_FILL = privateMethod("doFill",
        MethodType.methodType(void.class, NoiseChunk.class, ChunkAccess.class));
    private static final MethodHandle GLOBAL_FLUID_PICKER = privateGetter("globalFluidPicker", Supplier.class);

    private final long seed;
    private final NoiseBasedChunkGenerator generator;
    private final NoiseGeneratorSettings settings;
    private final RandomState randomState;
    private final PalettedContainerFactory containerFactory;
    /** Weak, so that the previews of a world never keep an unloaded world in memory. */
    private final WeakReference<Level> level;
    private final Aquifer.FluidPicker fluidPicker;
    private final PreviewShape shape;

    Terrain(long seed, NoiseBasedChunkGenerator generator, NoiseGeneratorSettings settings, RandomState randomState,
        PalettedContainerFactory containerFactory, Level level, PreviewShape shape) {
        this.seed = seed;
        this.generator = generator;
        this.settings = settings;
        this.randomState = randomState;
        this.containerFactory = containerFactory;
        this.level = new WeakReference<>(level);
        this.fluidPicker = globalFluidPicker(generator);
        this.shape = shape;
    }

    long seed() {
        return this.seed;
    }

    NoiseBasedChunkGenerator generator() {
        return this.generator;
    }

    NoiseGeneratorSettings settings() {
        return this.settings;
    }

    RandomState randomState() {
        return this.randomState;
    }

    PalettedContainerFactory containerFactory() {
        return this.containerFactory;
    }

    NoiseSettings noiseSettings() {
        return this.settings.noiseSettings();
    }

    PreviewShape shape() {
        return this.shape;
    }

    /** An empty chunk of the world's height; the terrain fills only the noise settings' part of it. */
    ProtoChunk emptyChunk(ChunkPos pos) {
        return new ProtoChunk(pos, UpgradeData.EMPTY, this.shape.worldHeight(), this.containerFactory, null);
    }

    DensityVolume chunkVolume(ChunkPos pos) {
        NoiseSettings noise = noiseSettings();
        return new DensityVolume(16, noise.height(), 16, pos.getMinBlockX(), noise.minY(), pos.getMinBlockZ());
    }

    /** ChunkGenerator.createBiomes, as the BIOMES status runs it (its structure manager is unused). */
    void createBiomes(ProtoChunk chunk) {
        this.generator.createBiomes(this.randomState, Blender.empty(), null, chunk).join();
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
    }

    /** The resolver createBiomes builds, for the sections minSectionY to maxSectionY only. */
    void createBiomes(ProtoChunk chunk, int minSectionY, int maxSectionY) {
        ThreadLocalDensityBufferArena arena = ThreadLocalDensityBufferArena.acquire();
        DensityBufferPool pool = arena == null ? this.randomState.acquireDensityBufferPool() : arena;
        try {
            Climate.Sampler sampler = this.randomState.createClimateSampler(SamplerContext.builder().enableCaches().useBufferArena(pool).build());
            fillBiomes(chunk, minSectionY, maxSectionY, sampler);
            chunk.setPersistedStatus(ChunkStatus.BIOMES);
        } finally {
            if (arena == null) {
                this.randomState.releaseDensityBufferPool(pool);
            } else {
                ThreadLocalDensityBufferArena.release(arena);
            }
        }
    }

    private void fillBiomes(ProtoChunk chunk, int minSectionY, int maxSectionY, Climate.Sampler sampler) {
        ChunkPos pos = chunk.getPos();
        int quartX = QuartPos.fromBlock(pos.getMinBlockX());
        int quartZ = QuartPos.fromBlock(pos.getMinBlockZ());
        BiomeResolver resolver = this.generator.getBiomeSource().createResolverForChunk(
            sampler, quartX, QuartPos.fromSection(minSectionY), quartZ, 4, QuartPos.fromSection(maxSectionY - minSectionY + 1), 4);
        for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
            chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY)).fillBiomesFromNoise(resolver, quartX, QuartPos.fromSection(sectionY), quartZ);
        }
    }

    NoiseChunk noiseChunk(DensityVolume volume) {
        return new NoiseChunk(this.randomState, Beardifier.EMPTY, this.settings, this.fluidPicker, Blender.empty(), volume);
    }

    /** NoiseBasedChunkGenerator.doFill: density and aquifer over the noise chunk's volume, plus the two worldgen heightmaps. */
    void fill(NoiseChunk noiseChunk, ProtoChunk chunk) {
        try {
            DO_FILL.invoke(this.generator, noiseChunk, chunk);
        } catch (Throwable t) {
            throw new IllegalStateException("doFill failed for " + chunk.getPos(), t);
        }
    }

    /**
     * MaterialSystem.buildSurface as NoiseBasedChunkGenerator.buildSurface calls it, with no set of possible biomes:
     * the surface rules then fold no biome conditions, which is exact but slower than the server's own call (that
     * passes the biomes of the 3 x 3 chunks around, which a preview does not have).
     */
    void buildSurface(ProtoChunk chunk, NoiseChunk noiseChunk, BiomeManager biomes) {
        Level world = this.level.get();
        if (world == null) {
            throw new IllegalStateException("the world of " + chunk.getPos() + " was unloaded");
        }
        WorldGenerationContext context = new WorldGenerationContext(this.generator, chunk.getHeightAccessorForGeneration(), world);
        this.randomState.surfaceSystem().buildSurface(this.randomState, biomes, context, chunk, noiseChunk,
            this.settings.materialRule().value(), null);
    }

    private static Aquifer.FluidPicker globalFluidPicker(NoiseBasedChunkGenerator generator) {
        try {
            return (Aquifer.FluidPicker) ((Supplier<?>) GLOBAL_FLUID_PICKER.invoke(generator)).get();
        } catch (Throwable t) {
            throw new IllegalStateException("cannot read NoiseBasedChunkGenerator.globalFluidPicker", t);
        }
    }

    private static MethodHandle privateMethod(String name, MethodType type) {
        try {
            return privateLookup().findVirtual(NoiseBasedChunkGenerator.class, name, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("NoiseBasedChunkGenerator." + name + " not found", e);
        }
    }

    private static MethodHandle privateGetter(String name, Class<?> type) {
        try {
            return privateLookup().findGetter(NoiseBasedChunkGenerator.class, name, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("NoiseBasedChunkGenerator." + name + " not found", e);
        }
    }

    private static MethodHandles.Lookup privateLookup() throws IllegalAccessException {
        return MethodHandles.privateLookupIn(NoiseBasedChunkGenerator.class, MethodHandles.lookup());
    }
}
