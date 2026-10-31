package densitycompiler;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;

/** One world seed with the real RandomState of the chosen noise settings, built the way ChunkMap builds it. */
record World(long seed, RandomState randomState, NoiseSettings noiseSettings, DensityFunction finalDensity,
    List<DensityFunction> probeFunctions) {

    /** The sampler Paper's own DensityFunctionCompiler produces for finalDensity; compiled on first use. */
    DensitySampler paperSampler() {
        return this.randomState.getSampler(this.finalDensity);
    }

    static List<World> create(long[] seeds, String dimension) {
        HolderLookup.Provider registries = bootstrap();
        NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
            .getOrThrow(settingsKey(dimension)).value();
        return Arrays.stream(seeds)
            .mapToObj(seed -> new World(seed, RandomState.create(registries.lookupOrThrow(Registries.NOISE), seed, settings),
                settings.noiseSettings(), settings.noiseRouter().finalDensity(), probeFunctions(settings)))
            .toList();
    }

    /**
     * The functions the aquifer reads point by point from the context finalDensity has just filled (the six of its
     * Config), plus the erosion and depth they share cache nodes with.
     */
    private static List<DensityFunction> probeFunctions(NoiseGeneratorSettings settings) {
        List<DensityFunction> functions = new ArrayList<>(List.of(settings.noiseRouter().erosion(), settings.noiseRouter().depth()));
        settings.aquifers().ifPresent(config -> functions.addAll(List.of(config.barrierNoise(), config.fluidLevelFloodednessNoise(),
            config.fluidLevelSpreadNoise(), config.lavaNoise(), config.exclusion(), config.surfaceLevel())));
        return functions;
    }

    /** Bootstrap wraps System.out/err into the logger, which can lose the last lines at exit: restore them. */
    private static HolderLookup.Provider bootstrap() {
        PrintStream out = System.out;
        PrintStream err = System.err;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.Provider registries = VanillaRegistries.createWorldLookup();
        System.setOut(out);
        System.setErr(err);
        return registries;
    }

    private static ResourceKey<NoiseGeneratorSettings> settingsKey(String dimension) {
        return switch (dimension.toLowerCase(Locale.ROOT)) {
            case "overworld" -> NoiseGeneratorSettings.OVERWORLD;
            case "amplified" -> NoiseGeneratorSettings.AMPLIFIED;
            case "large_biomes" -> NoiseGeneratorSettings.LARGE_BIOMES;
            case "nether" -> NoiseGeneratorSettings.NETHER;
            case "end" -> NoiseGeneratorSettings.END;
            case "caves" -> NoiseGeneratorSettings.CAVES;
            case "floating_islands" -> NoiseGeneratorSettings.FLOATING_ISLANDS;
            default -> throw new IllegalArgumentException("Unknown dimension " + dimension);
        };
    }
}
