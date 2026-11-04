package previewchunks;

import java.io.PrintStream;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.Util;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * The registries of the server jar's built-in data pack, the overworld generator and the chunk container factory,
 * loaded the way Paper's test RegistryHelper loads them (static layer with tags, then the worldgen registries).
 */
record Vanilla(RegistryAccess registries, NoiseBasedChunkGenerator generator, NoiseGeneratorSettings settings,
    PalettedContainerFactory containerFactory) {

    static Vanilla load() {
        PrintStream out = System.out;
        PrintStream err = System.err;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess registries = loadRegistries();
        // Bootstrap wraps System.out/err into the logger, which can lose the last lines at exit.
        System.setOut(out);
        System.setErr(err);
        return create(registries);
    }

    private static Vanilla create(RegistryAccess registries) {
        Holder<NoiseGeneratorSettings> settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
            .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        Holder<MultiNoiseBiomeSourceParameterList> parameters = registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
            .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), settings);
        return new Vanilla(registries, generator, settings.value(), PalettedContainerFactory.create(registries));
    }

    private static RegistryAccess loadRegistries() {
        PackRepository packs = ServerPacksSource.createVanillaTrustedRepository();
        List<String> packNames = FeatureFlags.REGISTRY.toNames(FeatureFlags.DEFAULT_FLAGS).stream().map(Identifier::getPath).toList();
        MinecraftServer.configurePackRepository(packs,
            new WorldDataConfiguration(new DataPackConfig(packNames, List.of()), FeatureFlags.DEFAULT_FLAGS), true, false);
        try (MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, packs.openAllSelected())) {
            LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
            List<Registry.PendingTags<?>> pendingTags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
            List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLD), pendingTags);
            RegistryAccess.Frozen worldgen = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLD_REGISTRIES, Util.backgroundExecutor()).join();
            pendingTags.forEach(Registry.PendingTags::apply);
            return layers.replaceFrom(RegistryLayer.WORLD, worldgen).compositeAccess().freeze();
        }
    }
}
