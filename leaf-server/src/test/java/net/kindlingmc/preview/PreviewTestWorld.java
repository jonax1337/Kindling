package net.kindlingmc.preview;

import io.papermc.paper.configuration.WorldConfiguration;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.bukkit.support.RegistryHelper;
import sun.misc.Unsafe;

/**
 * A real overworld generator for the preview tests: its terrain, previews, and a search for chunks lying entirely in a
 * biome. Holds the stand-in Level that Terrain only references weakly, so keep the instance for the whole test.
 */
final class PreviewTestWorld {
    private static final int SEARCH_STEP = 4;
    private static final int SEARCH_RADIUS = 256;
    private static final int SURFACE_QUART_Y = 16;
    private static final int PROBES_PER_AXIS = 3;
    private static final int OVERWORLD_SEA_LEVEL = 63;

    private final long seed;
    private final RegistryAccess registries = RegistryHelper.registryAccess();
    private final Level level = standInLevel();
    private final Terrain terrain;
    private final PreviewGenerator previews;

    PreviewTestWorld(long seed) {
        this.seed = seed;
        this.terrain = createTerrain();
        this.previews = new PreviewGenerator(this.terrain);
    }

    long seed() {
        return this.seed;
    }

    int seaLevel() {
        return OVERWORLD_SEA_LEVEL;
    }

    RegistryAccess registries() {
        return this.registries;
    }

    Terrain terrain() {
        return this.terrain;
    }

    PreviewGenerator previews() {
        return this.previews;
    }

    /** An empty chunk of the world's height, for tests that build their own blocks. */
    ProtoChunk emptyChunk(ChunkPos pos) {
        return this.terrain.emptyChunk(pos);
    }

    /** The preview of a chunk with the trees of the seed and snow and ice, as PreviewWorld builds it. */
    ProtoChunk finishedPreview(PreviewTrees trees, ChunkPos pos) {
        ProtoChunk chunk = this.previews.generate(pos);
        trees.decorate(chunk);
        PreviewSnow.freeze(chunk, this.seed, this.seaLevel());
        return chunk;
    }

    /** Vanilla's vegetation for this world, with no configured feature seeds. */
    PreviewVegetation vegetation() {
        DimensionType overworld = this.registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD).value();
        PreviewRegion.World world = new PreviewRegion.World(this.seed, OVERWORLD_SEA_LEVEL, this.registries, overworld, FeatureFlags.DEFAULT_FLAGS,
            this.terrain.randomState(), this.terrain.containerFactory());
        return PreviewVegetation.of(this.terrain.generator(), world, new Reference2LongOpenHashMap<>());
    }

    /** Up to the wanted number of chunks, on a coarse grid, whose probe columns all lie in the given biome. */
    List<ChunkPos> someChunksOf(ResourceKey<Biome> biome, int wanted) {
        BiomeResolver climate = this.terrain.generator().getBiomeSource()
            .createResolver(this.terrain.randomState().createClimateSampler(SamplerContext.builder().enableCaches().build()));
        List<ChunkPos> found = new ArrayList<>();
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS && found.size() < wanted; x += SEARCH_STEP) {
            for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS && found.size() < wanted; z += SEARCH_STEP) {
                if (isEntirelyIn(climate, x, z, biome)) {
                    found.add(new ChunkPos(x, z));
                }
            }
        }
        return found;
    }

    private static boolean isEntirelyIn(BiomeResolver climate, int chunkX, int chunkZ, ResourceKey<Biome> biome) {
        int step = 16 / (PROBES_PER_AXIS - 1);
        for (int i = 0; i < PROBES_PER_AXIS; i++) {
            for (int j = 0; j < PROBES_PER_AXIS; j++) {
                int quartX = QuartPos.fromBlock(chunkX * 16 + i * step - (i == PROBES_PER_AXIS - 1 ? 1 : 0));
                int quartZ = QuartPos.fromBlock(chunkZ * 16 + j * step - (j == PROBES_PER_AXIS - 1 ? 1 : 0));
                if (!climate.getNoiseBiome(quartX, SURFACE_QUART_Y, quartZ).is(biome)) {
                    return false;
                }
            }
        }
        return true;
    }

    private Terrain createTerrain() {
        Holder<NoiseGeneratorSettings> settings = this.registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        Holder<MultiNoiseBiomeSourceParameterList> parameters = this.registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
            .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), settings);
        RandomState randomState = RandomState.create(this.registries.lookupOrThrow(Registries.NOISE), this.seed, settings.value());
        return new Terrain(this.seed, generator, settings.value(), randomState, PalettedContainerFactory.create(this.registries), this.level);
    }

    /** The same stand-in Level as PreviewGeneratorTest uses: a ServerLevel whose constructor never ran, answering only the flat-bedrock setting. */
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

    /** The block at a column and height of a chunk. */
    static BlockState blockAt(ProtoChunk chunk, int x, int y, int z) {
        return chunk.getSection(chunk.getSectionIndex(y)).getBlockState(x, y & 15, z);
    }
}
