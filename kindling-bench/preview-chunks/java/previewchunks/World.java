package previewchunks;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.RandomState;

/** One world seed with the RandomState the server's ChunkMap builds for it. */
record World(long seed, Vanilla vanilla, RandomState randomState) {

    static World create(Vanilla vanilla, long seed) {
        return new World(seed, vanilla, RandomState.create(vanilla.registries().lookupOrThrow(Registries.NOISE), seed, vanilla.settings()));
    }
}
