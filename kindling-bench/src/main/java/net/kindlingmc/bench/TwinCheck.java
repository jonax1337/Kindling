package net.kindlingmc.bench;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Whether a world generates the same terrain as another: the same seed, the same generator with the same noise
 * settings, and the same surface height and biome at a few columns spread over the map. The seed also fixes the
 * decoration and structure seeds, which vanilla derives from the level seed and the chunk position alone.
 */
final class TwinCheck {

    private static final int[][] SAMPLED_COLUMNS = {
        {0, 0}, {1_000, -1_000}, {-3_000, 2_500}, {24_000, 24_000}, {-12_345, 6_789}, {500_000, -500_000}
    };

    private TwinCheck() {
    }

    /** The first way the twin generates differently from the main world, or none if it does not. */
    static Optional<String> firstDifference(ServerLevel main, ServerLevel twin) {
        if (main.getSeed() != twin.getSeed()) {
            return Optional.of("the seeds differ");
        }
        if (main.getChunkSource().getGeneratorState().getLevelSeed() != twin.getChunkSource().getGeneratorState().getLevelSeed()) {
            return Optional.of("the structure seeds differ");
        }
        Optional<String> generator = generatorDifference(main.getChunkSource().getGenerator(), twin.getChunkSource().getGenerator());
        if (generator.isPresent()) {
            return generator;
        }
        return sampledColumnDifference(main, twin);
    }

    private static Optional<String> generatorDifference(ChunkGenerator main, ChunkGenerator twin) {
        if (main.getClass() != twin.getClass()) {
            return Optional.of("the generators are " + main.getClass().getSimpleName() + " and " + twin.getClass().getSimpleName());
        }
        if (main instanceof NoiseBasedChunkGenerator mainNoise && twin instanceof NoiseBasedChunkGenerator twinNoise
                && !Objects.equals(mainNoise.generatorSettings().unwrapKey(), twinNoise.generatorSettings().unwrapKey())) {
            return Optional.of("the noise settings differ");
        }
        return Optional.empty();
    }

    private static Optional<String> sampledColumnDifference(ServerLevel main, ServerLevel twin) {
        for (int[] column : SAMPLED_COLUMNS) {
            int x = column[0];
            int z = column[1];
            if (surfaceHeight(main, x, z) != surfaceHeight(twin, x, z)) {
                return Optional.of("the surface height differs at block (" + x + ", " + z + ")");
            }
            if (!Objects.equals(biomeAtSeaLevel(main, x, z), biomeAtSeaLevel(twin, x, z))) {
                return Optional.of("the biome differs at block (" + x + ", " + z + ")");
            }
        }
        return Optional.empty();
    }

    private static int surfaceHeight(ServerLevel level, int x, int z) {
        RandomState randomState = level.getChunkSource().randomState();
        return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
    }

    private static Object biomeAtSeaLevel(ServerLevel level, int x, int z) {
        Holder<Biome> biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(level.getSeaLevel()), QuartPos.fromBlock(z));
        return biome.unwrapKey();
    }
}
