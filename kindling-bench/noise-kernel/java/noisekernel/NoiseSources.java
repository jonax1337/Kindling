package noisekernel;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The overworld volume noises built from real server objects: NormalNoise from the vanilla
 * data/minecraft/worldgen/noise/*.json in the server jar, BlendedNoise from the NoiseRouterData settings.
 */
final class NoiseSources {
    /** A noise as the router samples it: NoiseFunction.Sampler(noise, xzScale, yScale). */
    record Source(String name, Noise noise, double xzScale, double yScale) {
    }

    /** Overworld NoiseFunction entries of NoiseRouterData that are sampled as plain volumes (no shift). */
    private record Entry(String noise, double xzScale, double yScale) {
    }

    private static final Entry[] OVERWORLD_ENTRIES = {
        new Entry("jagged", 1500.0, 0.0),
        new Entry("spaghetti_roughness", 1.0, 1.0),
        new Entry("spaghetti_3d_rarity", 2.0, 1.0),
        new Entry("cave_entrance", 0.75, 0.5),
        new Entry("noodle", 1.0, 1.0),
        new Entry("noodle_ridge_a", 2.6666666666666665, 2.6666666666666665),
        new Entry("noodle_ridge_b", 2.6666666666666665, 2.6666666666666665),
        new Entry("pillar", 25.0, 0.3),
        new Entry("spaghetti_2d_modulator", 2.0, 1.0),
        new Entry("cave_layer", 1.0, 8.0),
        new Entry("cave_cheese", 1.0, 0.6666666666666666),
        new Entry("aquifer_barrier", 1.0, 0.5),
        new Entry("aquifer_fluid_level_floodedness", 1.0, 0.67),
        new Entry("aquifer_fluid_level_spread", 1.0, 0.7142857142857143),
        new Entry("aquifer_lava", 1.0, 1.0),
        new Entry("ore_veininess", 1.5, 1.5),
        new Entry("ore_gap", 1.0, 1.0),
        new Entry("ore_vein_a", 4.0, 4.0),
        new Entry("ore_vein_b", 4.0, 4.0)
    };

    private NoiseSources() {
    }

    static List<Source> overworld(long seed) {
        PositionalRandomFactory random = new XoroshiroRandomSource(seed).forkPositional();
        List<Source> sources = new ArrayList<>();
        for (Entry entry : OVERWORLD_ENTRIES) {
            NormalNoise parameters = loadNormalNoise(entry.noise());
            Noise noise = parameters.create(random.fromHashOf("minecraft:" + entry.noise()));
            sources.add(new Source(entry.noise(), noise, entry.xzScale(), entry.yScale()));
        }
        sources.addAll(blendedTerrain(random));
        return sources;
    }

    /** BlendedNoise.compileSampler(random): the three FBM stacks with the multipliers it hands to NoiseFunction.Sampler. */
    private static List<Source> blendedTerrain(PositionalRandomFactory random) {
        // NoiseRouterData: new BlendedNoise(0.25, 0.125, 80.0, 160.0, 8.0) for BASE_3D_NOISE_OVERWORLD
        BlendedNoise blended = new BlendedNoise(0.25, 0.125, 80.0, 160.0, 8.0);
        BlendedNoise.FbmSet fbms = blended.createFbmSet(random.fromHashOf("minecraft:terrain"));
        double xzMultiplier = 684.412 * blended.xzScale();
        double yMultiplier = 684.412 * blended.yScale();
        return List.of(
            new Source("blended_min_limit", fbms.minLimitNoise(), xzMultiplier, yMultiplier),
            new Source("blended_max_limit", fbms.maxLimitNoise(), xzMultiplier, yMultiplier),
            new Source("blended_main", fbms.mainNoise(), xzMultiplier / blended.xzFactor(), yMultiplier / blended.yFactor()));
    }

    private static NormalNoise loadNormalNoise(String name) {
        String resource = "/data/minecraft/worldgen/noise/" + name + ".json";
        try (InputStream stream = NoiseSources.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Not on the classpath: " + resource);
            }
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return NormalNoise.DIRECT_CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> {
                    throw new IllegalStateException(resource + ": " + error);
                })
                .orElseThrow();
        } catch (IOException e) {
            throw new IllegalStateException(resource, e);
        }
    }
}
