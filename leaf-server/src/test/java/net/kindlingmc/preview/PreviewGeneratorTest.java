package net.kindlingmc.preview;

import io.papermc.paper.configuration.WorldConfiguration;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A preview against the BIOMES and TERRAIN work on the whole chunk of a real overworld generator: the terrain top of
 * every column is the same (the heightmap doFill maintains), the surface blocks agree except where a neighbouring
 * chunk's biome is looked up differently, and the packet builds. Several threads generating at once give the same
 * terrain tops as one thread.
 */
@Normal
class PreviewGeneratorTest {
    private static final int[][] CHUNKS = {{0, 0}, {5, -3}, {-17, 40}, {1000, -2000}, {-31, -31}, {250, 90}};
    private static final long[] SEEDS = {1L, 2L};

    @Test
    void terrainTopsMatchTheFullChunk() {
        for (long seed : SEEDS) {
            Terrain terrain = terrain(seed);
            PreviewGenerator previews = new PreviewGenerator(terrain);
            for (int[] chunk : CHUNKS) {
                ChunkPos pos = new ChunkPos(chunk[0], chunk[1]);
                assertEquals(0, differingTops(fullChunk(terrain, pos), previews.generate(pos)), "terrain tops, seed " + seed + " chunk " + pos);
            }
        }
    }

    @Test
    void surfaceBlocksMatchTheFullChunk() {
        Terrain terrain = terrain(1L);
        PreviewGenerator previews = new PreviewGenerator(terrain);
        int differing = 0;
        int total = 0;
        for (int[] chunk : CHUNKS) {
            ChunkPos pos = new ChunkPos(chunk[0], chunk[1]);
            ProtoChunk full = fullChunk(terrain, pos);
            ProtoChunk preview = previews.generate(pos);
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int top = full.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                    total++;
                    if (full.getBlockState(pos.getMinBlockX() + x, top, pos.getMinBlockZ() + z)
                        != preview.getBlockState(pos.getMinBlockX() + x, top, pos.getMinBlockZ() + z)) {
                        differing++;
                    }
                }
            }
        }
        assertTrue(differing * 100 <= total, differing + " of " + total + " surface blocks differ");
    }

    @Test
    void packetBuilds() {
        Terrain terrain = terrain(1L);
        ProtoChunk chunk = new PreviewGenerator(terrain).generate(new ChunkPos(3, 4));
        PreviewPacket packet = PreviewPacket.of(chunk);
        assertNotNull(packet.packet());
        assertTrue(packet.rawBytes() > 0);
    }

    @Test
    void severalThreadsGiveTheSameTerrainTops() throws Exception {
        Terrain terrain = terrain(2L);
        PreviewGenerator previews = new PreviewGenerator(terrain);
        List<ChunkPos> positions = new ArrayList<>();
        for (int[] chunk : CHUNKS) {
            positions.add(new ChunkPos(chunk[0], chunk[1]));
        }
        List<ProtoChunk> serial = new ArrayList<>();
        for (ChunkPos pos : positions) {
            serial.add(previews.generate(pos));
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<ProtoChunk>> parallel = new ArrayList<>();
            for (int round = 0; round < 3; round++) {
                for (ChunkPos pos : positions) {
                    parallel.add(pool.submit(() -> previews.generate(pos)));
                }
            }
            for (int i = 0; i < parallel.size(); i++) {
                assertEquals(0, differingTops(serial.get(i % positions.size()), parallel.get(i).get()), "concurrent preview " + i);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static int differingTops(ProtoChunk expected, ProtoChunk actual) {
        int differing = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (expected.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) != actual.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z)) {
                    differing++;
                }
            }
        }
        return differing;
    }

    /** The BIOMES and TERRAIN work on the whole chunk, minus carvers. */
    private static ProtoChunk fullChunk(Terrain terrain, ChunkPos pos) {
        ProtoChunk chunk = terrain.emptyChunk(pos);
        terrain.createBiomes(chunk);
        try (NoiseChunk noiseChunk = terrain.noiseChunk(terrain.chunkVolume(pos))) {
            terrain.fill(noiseChunk, chunk);
            terrain.buildSurface(chunk, noiseChunk, ChunkBiomes.sections(terrain.seed(), chunk, chunk.getMinSectionY(), chunk.getMaxSectionY(),
                new ChunkBiomes.Outside(terrain)).manager());
        }
        return chunk;
    }

    private static Terrain terrain(long seed) {
        RegistryAccess registries = RegistryHelper.registryAccess();
        Holder<NoiseGeneratorSettings> settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        Holder<MultiNoiseBiomeSourceParameterList> parameters = registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
            .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), settings);
        RandomState randomState = RandomState.create(registries.lookupOrThrow(Registries.NOISE), seed, settings.value());
        return new Terrain(seed, generator, settings.value(), randomState, PalettedContainerFactory.create(registries), STAND_IN_LEVEL);
    }

    /** Held here because Terrain only holds its level weakly. */
    private static final Level STAND_IN_LEVEL = standInLevel();

    /**
     * A Level for WorldGenerationContext where no server runs. Paper's flat-bedrock condition reads
     * level().paperConfig().environment.generateFlatBedrock and nothing else; this answers false, Paper's default. It is a
     * ServerLevel whose constructor never ran: any other use of it fails.
     */
    private static Level standInLevel() {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            WorldConfiguration config = (WorldConfiguration) unsafe.allocateInstance(WorldConfiguration.class);
            config.environment = (WorldConfiguration.Environment) unsafe.allocateInstance(WorldConfiguration.Environment.class);
            Level level = (Level) unsafe.allocateInstance(ServerLevel.class);
            Field paperConfig = Level.class.getDeclaredField("paperConfig");
            paperConfig.setAccessible(true);
            paperConfig.set(level, config);
            return level;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot build the stand-in level", e);
        }
    }
}
