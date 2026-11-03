package previewchunks;

import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * A preview chunk: the vanilla terrain of a vertical band around the surface instead of the whole column. The band
 * starts at the chunk's preliminary surface levels plus margins, reaches at least the sea surface, and grows until no
 * terrain touches its top layer and every column has at least margins.skinDepth() blocks of terrain inside it.
 */
final class Preview {
    /** The generated chunk (nothing below the band), its band, how often the band grew, the preliminary surface per column. */
    record Result(ProtoChunk chunk, Band band, int growths, float[] preliminarySurface) {
    }

    private final Terrain terrain;
    private final Margins margins;

    Preview(Terrain terrain, Margins margins) {
        this.terrain = terrain;
        this.margins = margins;
    }

    Result generate(ChunkPos pos) {
        return generate(pos, new ChunkBiomes.Outside(this.terrain.world()));
    }

    Result generate(ChunkPos pos, ChunkBiomes.Outside outside) {
        float[] preliminary = preliminarySurface(pos);
        Band band = initialBand(preliminary);
        for (int growths = 0; ; growths++) {
            ProtoChunk chunk = this.terrain.emptyChunk(pos);
            this.terrain.createBiomes(chunk, band.minSectionY(), band.maxSectionY());
            try (NoiseChunk noiseChunk = this.terrain.noiseChunk(band.volume(pos))) {
                this.terrain.fill(noiseChunk, chunk);
                Band needed = neededBand(chunk, band);
                if (needed.equals(band)) {
                    this.terrain.buildSurface(chunk, noiseChunk,
                        ChunkBiomes.sections(this.terrain.world(), chunk, band.minSectionY(), band.maxSectionY(), outside).manager());
                    return new Result(chunk, band, growths, preliminary);
                }
                band = needed;
            }
        }
    }

    /** The preview as it would be sent: generated, then the default block below the band (see {@link Skin}). */
    ProtoChunk generateSkin(ChunkPos pos) {
        Result result = generate(pos);
        World world = this.terrain.world();
        Skin.fillBelowBand(result.chunk(), result.band(), world.settings().defaultBlock(), world.containerFactory());
        return result.chunk();
    }

    /** The preliminary surface level of every column, indexed by {@link #column}. */
    float[] preliminarySurface(ChunkPos pos) {
        DensityVolume columns = new DensityVolume(16, 1, 16, pos.getMinBlockX(), 0, pos.getMinBlockZ());
        DensityBuffer buffer = DensityBuffer.createUnpooled(columns.size());
        RandomState randomState = this.terrain.world().randomState();
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

    static int column(int x, int z) {
        return z << 4 | x;
    }

    /** The highest terrain block of a column (what the OCEAN_FLOOR_WG heightmap tracks), or below the world when none. */
    static int topTerrainY(ChunkAccess chunk, int x, int z) {
        return chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
    }

    /** Terrain as the OCEAN_FLOOR_WG heightmap counts it: blocks that block motion, so not air and not fluids. */
    static boolean isTerrain(BlockState state) {
        return state.is(BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP);
    }

    private Band initialBand(float[] preliminary) {
        float lowest = Float.POSITIVE_INFINITY;
        float highest = Float.NEGATIVE_INFINITY;
        for (float level : preliminary) {
            lowest = Math.min(lowest, level);
            highest = Math.max(highest, level);
        }
        int seaSurfaceY = this.terrain.world().settings().seaLevel() - 1;
        return Band.covering(Mth.floor(lowest) - this.margins.below(),
            Math.max(Mth.ceil(highest) + this.margins.above(), seaSurfaceY), this.terrain.noiseSettings());
    }

    private Band neededBand(ProtoChunk chunk, Band band) {
        NoiseSettings limits = this.terrain.noiseSettings();
        Band needed = band;
        if (!band.reachesTop(limits) && hasTerrainInLayer(chunk, band.maxY())) {
            needed = needed.grownUp(this.margins.growStep(), limits);
        }
        if (!band.reachesBottom(limits) && lowestTerrainTop(chunk) < band.minY() + this.margins.skinDepth()) {
            needed = needed.grownDown(this.margins.growStep(), limits);
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
