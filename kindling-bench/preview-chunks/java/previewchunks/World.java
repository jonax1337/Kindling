package previewchunks;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * One world as the generators need it: its seed, noise generator and settings, the RandomState the server's ChunkMap
 * builds for it, the chunk container factory, and the Level that WorldGenerationContext reads (the server's own level
 * in a running server, a stand-in in the harness).
 */
record World(long seed, NoiseBasedChunkGenerator generator, NoiseGeneratorSettings settings, RandomState randomState,
    PalettedContainerFactory containerFactory, Level level) {

    /** The harness's world for a seed, built from the server jar's built-in registries. */
    static World create(Vanilla vanilla, long seed) {
        RandomState randomState = RandomState.create(vanilla.registries().lookupOrThrow(Registries.NOISE), seed, vanilla.settings());
        return new World(seed, vanilla.generator(), vanilla.settings(), randomState, vanilla.containerFactory(), StandInLevel.INSTANCE);
    }
}
