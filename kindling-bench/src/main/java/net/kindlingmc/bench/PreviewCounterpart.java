package net.kindlingmc.bench;

import java.util.Optional;
import net.kindlingmc.preview.FidelityCounts;
import net.kindlingmc.preview.PreviewFidelity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** The seed preview of a chunk, built when it is compared; it needs nothing from the server before that. */
final class PreviewCounterpart implements FidelityCounterpart {

    @Override
    public void prepare(int chunkX, int chunkZ) {
    }

    @Override
    public void load(int chunkX, int chunkZ) {
    }

    @Override
    public boolean isSettled(int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public Optional<FidelityCounts> compare(ServerLevel level, LevelChunk real) {
        return PreviewFidelity.compare(level, real);
    }

    @Override
    public void release(int chunkX, int chunkZ) {
    }
}
