package net.kindlingmc.preview;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Preview chunks: the vanilla terrain of a vertical band around the surface instead of the whole column, with the
 * surface rules applied and the default block below the band. The band starts at the chunk's preliminary surface
 * levels plus margins, reaches at least the sea surface, and grows until no terrain touches its top layer and every
 * column has at least {@link #SKIN_DEPTH} blocks of terrain inside it. Inside the band the density is bit-identical to
 * the full chunk's.
 *
 * <p>Compared with the BIOMES and TERRAIN statuses, the surface rules get no set of possible biomes (exact, slower),
 * and the biomes of neighbouring chunks come from the climate sampler instead of stored chunks. Carvers, features,
 * structures and light are not part of a preview.
 *
 * <p>Thread safety: an instance holds only immutable shared state (see {@link Terrain}); {@link #generate} keeps all
 * per-call state, including its own climate sampler and neighbour biome cache, in locals.
 */
public final class PreviewGenerator {
    /** Blocks above the highest preliminary surface level the band starts with. */
    private static final int ABOVE = 24;
    /** Blocks below the lowest preliminary surface level the band starts with. */
    private static final int BELOW = 24;
    /** Blocks of terrain every column must have inside the band. */
    private static final int SKIN_DEPTH = 16;
    /** Blocks the band grows by when it is too small. */
    private static final int GROW_STEP = 16;

    private final Terrain terrain;

    PreviewGenerator(Terrain terrain) {
        this.terrain = terrain;
    }

    /** Empty unless the level uses a NoiseBasedChunkGenerator and its dimension has sky light and no ceiling. */
    public static Optional<PreviewGenerator> forLevel(ServerLevel level) {
        DimensionType dimension = level.dimensionType();
        if (!dimension.hasSkyLight() || dimension.hasCeiling()) {
            return Optional.empty();
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noise) || !spansWorldHeight(noise.generatorSettings().value().noiseSettings(), level)) {
            return Optional.empty();
        }
        Terrain terrain = new Terrain(level.getSeed(), noise, noise.generatorSettings().value(), level.getChunkSource().randomState(),
            level.palettedContainerFactory(), level);
        return Optional.of(new PreviewGenerator(terrain));
    }

    /**
     * Previews are built at the noise settings' height; the client expects the world's. A data pack may raise the
     * world's build limit without the noise, so such worlds get no previews rather than malformed chunk packets.
     */
    private static boolean spansWorldHeight(NoiseSettings noise, ServerLevel level) {
        return noise.minY() == level.getMinY() && noise.height() == level.getHeight();
    }

    /** The preview of one chunk: band terrain with surface rules, stone below. Safe to call concurrently from several threads. */
    public ProtoChunk generate(ChunkPos pos) {
        ChunkBiomes.Outside outside = new ChunkBiomes.Outside(this.terrain);
        Band band = initialBand(preliminarySurface(pos));
        while (true) {
            ProtoChunk chunk = this.terrain.emptyChunk(pos);
            this.terrain.createBiomes(chunk, band.minSectionY(), band.maxSectionY());
            try (NoiseChunk noiseChunk = this.terrain.noiseChunk(band.volume(pos))) {
                this.terrain.fill(noiseChunk, chunk);
                Band needed = neededBand(chunk, band);
                if (needed.equals(band)) {
                    this.terrain.buildSurface(chunk, noiseChunk,
                        ChunkBiomes.sections(this.terrain.seed(), chunk, band.minSectionY(), band.maxSectionY(), outside).manager());
                    Skin.fillBelowBand(chunk, band, this.terrain.settings().defaultBlock(), this.terrain.containerFactory());
                    return chunk;
                }
                band = needed;
            }
        }
    }

    /** Terrain as the OCEAN_FLOOR_WG heightmap counts it: blocks that block motion, so not air and not fluids. */
    static boolean isTerrain(BlockState state) {
        return state.is(BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP);
    }

    /** The preliminary surface level of every column, indexed by {@link #column}. */
    private float[] preliminarySurface(ChunkPos pos) {
        DensityVolume columns = new DensityVolume(16, 1, 16, pos.getMinBlockX(), 0, pos.getMinBlockZ());
        DensityBuffer buffer = DensityBuffer.createUnpooled(columns.size());
        RandomState randomState = this.terrain.randomState();
        randomState.samplersWithContext(SamplerContext.builder().enableCaches().build())
            .get(randomState.surfaceSystem().preliminarySurfaceFunction())
            .sampleVolume(buffer, columns);
        float[] levels = new float[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                levels[column(x, z)] = buffer.get(columns.indexUnchecked(x, 0, z));
            }
        }
        return levels;
    }

    private static int column(int x, int z) {
        return z << 4 | x;
    }

    /** The highest terrain block of a column (what the OCEAN_FLOOR_WG heightmap tracks), or below the world when none. */
    private static int topTerrainY(ChunkAccess chunk, int x, int z) {
        return chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
    }

    private Band initialBand(float[] preliminary) {
        float lowest = Float.POSITIVE_INFINITY;
        float highest = Float.NEGATIVE_INFINITY;
        for (float level : preliminary) {
            lowest = Math.min(lowest, level);
            highest = Math.max(highest, level);
        }
        int seaSurfaceY = this.terrain.settings().seaLevel() - 1;
        return Band.covering(Mth.floor(lowest) - BELOW, Math.max(Mth.ceil(highest) + ABOVE, seaSurfaceY), this.terrain.noiseSettings());
    }

    private Band neededBand(ProtoChunk chunk, Band band) {
        NoiseSettings limits = this.terrain.noiseSettings();
        Band needed = band;
        if (!band.reachesTop(limits) && hasTerrainInLayer(chunk, band.maxY())) {
            needed = needed.grownUp(GROW_STEP, limits);
        }
        if (!band.reachesBottom(limits) && lowestTerrainTop(chunk) < band.minY() + SKIN_DEPTH) {
            needed = needed.grownDown(GROW_STEP, limits);
        }
        return needed;
    }

    private static boolean hasTerrainInLayer(ProtoChunk chunk, int y) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        int sectionY = y & 15;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (isTerrain(section.getBlockState(x, sectionY, z))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int lowestTerrainTop(ProtoChunk chunk) {
        int lowest = Integer.MAX_VALUE;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                lowest = Math.min(lowest, topTerrainY(chunk, x, z));
            }
        }
        return lowest;
    }
}
