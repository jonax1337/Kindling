package net.kindlingmc.preview;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * The noise biomes the surface rules read around one chunk. Inside the chunk's generated sections they come from the
 * chunk, as in vanilla. Elsewhere (neighbouring chunks, sections a preview leaves out) they come from {@link Outside}.
 * One instance serves one generation on one thread.
 */
final class ChunkBiomes implements BiomeResolver {
    /**
     * Biomes outside a chunk's generated sections, computed from the climate once per quart and kept, standing in for
     * the stored biomes of the neighbours a WorldGenRegion would read. The climate's biome search remembers its last
     * result (MC-241546), so a quart can come out differently depending on what was computed before it; that is
     * acceptable for a preview. Not thread-safe: make one per generation.
     */
    static final class Outside {
        private final BiomeResolver climate;
        private final Long2ObjectOpenHashMap<Holder<Biome>> known = new Long2ObjectOpenHashMap<>();

        Outside(Terrain terrain) {
            this.climate = terrain.generator().getBiomeSource()
                .createResolver(terrain.randomState().createClimateSampler(SamplerContext.builder().enableCaches().build()));
        }

        Holder<Biome> get(int quartX, int quartY, int quartZ) {
            long key = BlockPos.asLong(quartX, quartY, quartZ);
            Holder<Biome> biome = this.known.get(key);
            if (biome == null) {
                biome = this.climate.getNoiseBiome(quartX, quartY, quartZ);
                this.known.put(key, biome);
            }
            return biome;
        }
    }

    private final long seed;
    private final ChunkAccess chunk;
    private final Outside outside;
    private final int minQuartX;
    private final int minQuartZ;
    private final int minQuartY;
    private final int maxQuartY;
    private final int worldMinQuartY;
    private final int worldMaxQuartY;

    private ChunkBiomes(long seed, ChunkAccess chunk, int minSectionY, int maxSectionY, Outside outside) {
        this.seed = seed;
        this.chunk = chunk;
        this.outside = outside;
        this.minQuartX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
        this.minQuartZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
        this.minQuartY = QuartPos.fromSection(minSectionY);
        this.maxQuartY = QuartPos.fromSection(maxSectionY + 1) - 1;
        this.worldMinQuartY = QuartPos.fromSection(chunk.getMinSectionY());
        this.worldMaxQuartY = QuartPos.fromSection(chunk.getMaxSectionY() + 1) - 1;
    }

    static ChunkBiomes sections(long seed, ChunkAccess chunk, int minSectionY, int maxSectionY, Outside outside) {
        return new ChunkBiomes(seed, chunk, minSectionY, maxSectionY, outside);
    }

    BiomeManager manager() {
        return new BiomeManager(this, BiomeManager.obfuscateSeed(this.seed));
    }

    /** Quarts above or below the world read the nearest quart inside it, as ChunkAccess.getNoiseBiome does for every chunk. */
    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        int clampedY = Mth.clamp(quartY, this.worldMinQuartY, this.worldMaxQuartY);
        if (isGenerated(quartX, clampedY, quartZ)) {
            return this.chunk.getNoiseBiome(quartX, clampedY, quartZ);
        }
        return this.outside.get(quartX, clampedY, quartZ);
    }

    private boolean isGenerated(int quartX, int quartY, int quartZ) {
        return quartX >= this.minQuartX && quartX < this.minQuartX + 4
            && quartZ >= this.minQuartZ && quartZ < this.minQuartZ + 4
            && quartY >= this.minQuartY && quartY <= this.maxQuartY;
    }
}
