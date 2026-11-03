package net.kindlingmc.bench;

import java.util.Optional;
import net.kindlingmc.preview.FidelityCounts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** What a fidelity run compares the real chunks of the main world with; see {@link FidelityAgainst}. All calls on the tick thread. */
interface FidelityCounterpart {

    /** Runs before the run loads the real chunk, to fix the order its generation runs in. */
    void prepare(int chunkX, int chunkZ);

    /** Starts producing the counterpart of the chunk, after the run has loaded the real chunk. */
    void load(int chunkX, int chunkZ);

    /** Whether the counterpart of the chunk has everything it needs to be compared, such as settled light. */
    boolean isSettled(int chunkX, int chunkZ);

    /** The counts of one chunk, or none where the world has nothing to compare with. */
    Optional<FidelityCounts> compare(ServerLevel level, LevelChunk real);

    /** Gives back what {@link #load} held on to. */
    void release(int chunkX, int chunkZ);
}
