package aquifer;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;

/**
 * One world seed of one noise preset with everything an aquifer is built from: the real RandomState, the preset's
 * Aquifer.Config, the positional random factory NoiseChunk asks for, and the real fluid picker.
 */
record World(String preset, String kind, long seed, RandomState randomState, NoiseGeneratorSettings settings,
             Aquifer.Config config, PositionalRandomFactory aquiferRandom, Aquifer.FluidPicker fluidPicker) {
    private static final Identifier AQUIFER_RANDOM_NAME = Identifier.withDefaultNamespace("aquifer");
    /** Kind of the worlds the presets give; the synthetic ones name what they change. */
    private static final String PLAIN = "";
    private static final String LEGACY = "legacy-random";
    private static final String DEEP_SEA = "deep-sea";
    private static final String SEA_MINUS_10 = "sea-10";
    /** The deep world's volume: its lowest aquifer cells lie below -2048, where BlockPos wraps Y. */
    private static final int DEEP_MIN_Y = -2032;
    private static final int DEEP_HEIGHT = 4064;
    /** Below min(-54, sea level) the vanilla picker returns lava: -2040 keeps it below every block of the deep volume. */
    private static final int DEEP_SEA_LEVEL = -2040;
    /** The level the lava test (fluid surface level <= -10) cuts at. */
    private static final int LAVA_LEVEL_EDGE = -10;
    /** The real floodedness noise rarely exceeds 0.4; times 3 about half of all cells flood fully or partly. */
    private static final float FLOODEDNESS_GAIN = 3.0F;

    private static HolderLookup.Provider registries;

    static List<World> create(long[] seeds, List<String> presets) {
        HolderLookup.Provider lookup = registries();
        List<World> worlds = new ArrayList<>();
        for (String preset : presets) {
            Holder<NoiseGeneratorSettings> settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(settingsKey(preset));
            Aquifer.FluidPicker picker = realFluidPicker(lookup, settings);
            for (long seed : seeds) {
                worlds.add(of(preset, PLAIN, seed, settings.value(), picker));
            }
        }
        return worlds;
    }

    /** The same world with the Legacy positional random source, which no vanilla aquifer preset uses. */
    @SuppressWarnings("deprecation")
    World withLegacyRandom() {
        NoiseGeneratorSettings s = this.settings;
        NoiseGeneratorSettings legacy = new NoiseGeneratorSettings(s.noiseSettings(), s.defaultBlock(), s.defaultFluid(), s.noiseRouter(),
            s.materialRule(), s.spawnTarget(), s.seaLevel(), s.disableMobGeneration(), s.aquifers(), true, s.debugFunctions());
        return of(this.preset, LEGACY, this.seed, legacy, this.fluidPicker);
    }

    /**
     * A datapack-style world whose aquifer cells reach the BlockPos Y wrap (cell locations below -2048). The sea level
     * keeps the vanilla picker's lava below the volume, so water only exists where the floodedness noise puts it,
     * which is why that noise is amplified.
     */
    World withDeepSea() {
        return this.synthetic(DEEP_SEA, NoiseSettings.create(DEEP_MIN_Y, DEEP_HEIGHT), DEEP_SEA_LEVEL);
    }

    /** The preset's height with sea level -10: fully flooded cells sit exactly on the level the lava test cuts at. */
    World withSeaLevelMinus10() {
        return this.synthetic(SEA_MINUS_10, this.noiseSettings(), LAVA_LEVEL_EDGE);
    }

    private World synthetic(String changeName, NoiseSettings noise, int seaLevel) {
        NoiseGeneratorSettings s = this.settings;
        Aquifer.Config c = this.config;
        Aquifer.Config flooded = new Aquifer.Config(c.barrierNoise(),
            DensityFunctions.mul(c.fluidLevelFloodednessNoise(), DensityFunctions.constant(FLOODEDNESS_GAIN)),
            c.fluidLevelSpreadNoise(), c.lavaNoise(), c.exclusion(), c.surfaceLevel());
        @SuppressWarnings("deprecation")
        NoiseGeneratorSettings changed = new NoiseGeneratorSettings(noise, s.defaultBlock(), s.defaultFluid(), s.noiseRouter(), s.materialRule(),
            s.spawnTarget(), seaLevel, s.disableMobGeneration(), Optional.of(flooded), s.useLegacyRandomSource(), s.debugFunctions());
        return of(this.preset, changeName, this.seed, changed, realFluidPicker(registries(), Holder.direct(changed)));
    }

    /** Preset name plus what a synthetic world changed: the part of a tally label that names the world. */
    String label() {
        return this.kind.isEmpty() ? this.preset : this.preset + "/" + this.kind;
    }

    DensityFunction finalDensity() {
        return this.settings.noiseRouter().finalDensity();
    }

    NoiseSettings noiseSettings() {
        return this.settings.noiseSettings();
    }

    @Override
    public String toString() {
        return this.label() + " seed " + this.seed;
    }

    private static World of(String preset, String kind, long seed, NoiseGeneratorSettings settings, Aquifer.FluidPicker picker) {
        Aquifer.Config config = settings.aquifers().orElseThrow(() -> new IllegalArgumentException("Preset " + preset + " has no aquifers"));
        RandomState randomState = RandomState.create(registries().lookupOrThrow(Registries.NOISE), seed, settings);
        return new World(preset, kind, seed, randomState, settings, config,
            randomState.getOrCreateRandomFactory(AQUIFER_RANDOM_NAME), picker);
    }

    /** The picker NoiseBasedChunkGenerator builds, read from a real generator so no copy of its lambda can drift. */
    @SuppressWarnings("unchecked")
    private static Aquifer.FluidPicker realFluidPicker(HolderLookup.Provider lookup, Holder<NoiseGeneratorSettings> settings) {
        try {
            Holder<net.minecraft.world.level.biome.Biome> plains = lookup.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
            NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(plains), settings);
            Field field = NoiseBasedChunkGenerator.class.getDeclaredField("globalFluidPicker");
            field.setAccessible(true);
            return ((Supplier<Aquifer.FluidPicker>)field.get(generator)).get();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read the fluid picker of NoiseBasedChunkGenerator", e);
        }
    }

    /** Bootstrap wraps System.out/err into the logger, which can lose the last lines at exit: restore them. */
    private static synchronized HolderLookup.Provider registries() {
        if (registries == null) {
            PrintStream out = System.out;
            PrintStream err = System.err;
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            registries = VanillaRegistries.createWorldLookup();
            System.setOut(out);
            System.setErr(err);
        }
        return registries;
    }

    private static ResourceKey<NoiseGeneratorSettings> settingsKey(String preset) {
        return switch (preset.toLowerCase(Locale.ROOT)) {
            case "overworld" -> NoiseGeneratorSettings.OVERWORLD;
            case "amplified" -> NoiseGeneratorSettings.AMPLIFIED;
            case "large_biomes" -> NoiseGeneratorSettings.LARGE_BIOMES;
            default -> throw new IllegalArgumentException("Unknown or aquifer-less preset " + preset);
        };
    }
}
