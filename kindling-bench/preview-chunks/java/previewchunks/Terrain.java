package previewchunks;

import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ThreadLocalDensityBufferArena;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.jspecify.annotations.Nullable;

/**
 * The work of the BIOMES and TERRAIN statuses, callable on part of a chunk: noise biomes per section, the density fill
 * of NoiseBasedChunkGenerator.doFill over any volume, and MaterialSystem.buildSurface. Left out, because they need a
 * WorldGenRegion or structures: carvers and blending; the beardifier is empty, as for a chunk without structures.
 */
final class Terrain {
    /**
     * The biome set buildSurface folds biome conditions with. The server passes the biomes of the 3 x 3 chunks around
     * (ChunkStatusTasks.collectPossibleBiomes), which the harness does not have. NONE folds nothing (exact, slower than
     * the server); OWN folds with the chunk's own palettes (less than the server's set, so faster, and at chunk edges
     * possibly not exact). The two bracket the server's cost.
     */
    enum PossibleBiomes {
        NONE,
        OWN;

        @Nullable Set<Holder<Biome>> of(ChunkAccess chunk) {
            if (this == NONE) {
                return null;
            }
            Set<Holder<Biome>> biomes = new ObjectOpenHashSet<>();
            chunk.collectBiomesInPalette(biomes);
            return biomes;
        }
    }

    private static final MethodHandle DO_FILL = privateMethod("doFill",
        MethodType.methodType(void.class, NoiseChunk.class, ChunkAccess.class));
    private static final MethodHandle GLOBAL_FLUID_PICKER = privateGetter("globalFluidPicker", Supplier.class);

    private final World world;
    private final PossibleBiomes possibleBiomes;
    private final Aquifer.FluidPicker fluidPicker;

    Terrain(World world, PossibleBiomes possibleBiomes) {
        this.world = world;
        this.possibleBiomes = possibleBiomes;
        this.fluidPicker = globalFluidPicker(world.vanilla().generator());
    }

    World world() {
        return this.world;
    }

    NoiseSettings noiseSettings() {
        return this.world.vanilla().settings().noiseSettings();
    }

    /** The reference: the BIOMES and TERRAIN work on the whole chunk, as the server does it (minus carvers). */
    ProtoChunk generateFull(ChunkPos pos) {
        return generateFull(pos, new ChunkBiomes.Outside(this.world));
    }

    ProtoChunk generateFull(ChunkPos pos, ChunkBiomes.Outside outside) {
        ProtoChunk chunk = emptyChunk(pos);
        createBiomes(chunk);
        try (NoiseChunk noiseChunk = noiseChunk(chunkVolume(pos))) {
            fill(noiseChunk, chunk);
            buildSurface(chunk, noiseChunk, ChunkBiomes.allSections(this.world, chunk, outside).manager());
        }
        return chunk;
    }

    ProtoChunk emptyChunk(ChunkPos pos) {
        NoiseSettings noise = noiseSettings();
        LevelHeightAccessor height = LevelHeightAccessor.create(noise.minY(), noise.height());
        return new ProtoChunk(pos, UpgradeData.EMPTY, height, this.world.vanilla().containerFactory(), null);
    }

    DensityVolume chunkVolume(ChunkPos pos) {
        NoiseSettings noise = noiseSettings();
        return new DensityVolume(16, noise.height(), 16, pos.getMinBlockX(), noise.minY(), pos.getMinBlockZ());
    }

    /** ChunkGenerator.createBiomes, as the BIOMES status runs it (its structure manager is unused). */
    void createBiomes(ProtoChunk chunk) {
        this.world.vanilla().generator().createBiomes(this.world.randomState(), Blender.empty(), null, chunk).join();
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
    }

    /** The resolver createBiomes builds, for the sections minSectionY to maxSectionY only. */
    void createBiomes(ProtoChunk chunk, int minSectionY, int maxSectionY) {
        RandomState randomState = this.world.randomState();
        ThreadLocalDensityBufferArena arena = ThreadLocalDensityBufferArena.acquire();
        DensityBufferPool pool = arena == null ? randomState.acquireDensityBufferPool() : arena;
        try {
            Climate.Sampler sampler = randomState.createClimateSampler(SamplerContext.builder().enableCaches().useBufferArena(pool).build());
            fillBiomes(chunk, minSectionY, maxSectionY, sampler);
            chunk.setPersistedStatus(ChunkStatus.BIOMES);
        } finally {
            if (arena == null) {
                randomState.releaseDensityBufferPool(pool);
            } else {
                ThreadLocalDensityBufferArena.release(arena);
            }
        }
    }

    private void fillBiomes(ProtoChunk chunk, int minSectionY, int maxSectionY, Climate.Sampler sampler) {
        ChunkPos pos = chunk.getPos();
        int quartX = QuartPos.fromBlock(pos.getMinBlockX());
        int quartZ = QuartPos.fromBlock(pos.getMinBlockZ());
        BiomeResolver resolver = this.world.vanilla().generator().getBiomeSource().createResolverForChunk(
            sampler, quartX, QuartPos.fromSection(minSectionY), quartZ, 4, QuartPos.fromSection(maxSectionY - minSectionY + 1), 4);
        for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
            chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY)).fillBiomesFromNoise(resolver, quartX, QuartPos.fromSection(sectionY), quartZ);
        }
    }

    NoiseChunk noiseChunk(DensityVolume volume) {
        return new NoiseChunk(this.world.randomState(), Beardifier.EMPTY, this.world.vanilla().settings(), this.fluidPicker, Blender.empty(), volume);
    }

    /** NoiseBasedChunkGenerator.doFill: density and aquifer over the noise chunk's volume, plus the two worldgen heightmaps. */
    void fill(NoiseChunk noiseChunk, ProtoChunk chunk) {
        try {
            DO_FILL.invoke(this.world.vanilla().generator(), noiseChunk, chunk);
        } catch (Throwable t) {
            throw new IllegalStateException("doFill failed for " + chunk.getPos(), t);
        }
    }

    /** MaterialSystem.buildSurface as NoiseBasedChunkGenerator.buildSurface calls it, with the chosen set of possible biomes. */
    void buildSurface(ProtoChunk chunk, NoiseChunk noiseChunk, BiomeManager biomes) {
        RandomState randomState = this.world.randomState();
        WorldGenerationContext context = new WorldGenerationContext(this.world.vanilla().generator(), chunk.getHeightAccessorForGeneration(),
            StandInLevel.INSTANCE);
        randomState.surfaceSystem().buildSurface(randomState, biomes, context, chunk, noiseChunk,
            this.world.vanilla().settings().materialRule().value(), this.possibleBiomes.of(chunk));
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
