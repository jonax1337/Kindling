package net.kindlingmc.pregen;

/** What a {@link PregenTask} needs from a world; the server level implements it, the tests replace it. */
interface PregenWorld {

    long UNKNOWN_FREE_BYTES = Long.MAX_VALUE;

    /** Identifies the world in {@code pregeneration.properties}. */
    String name();

    /** Tells a regenerated world from the one a saved run belongs to. */
    long seed();

    /** Whether the world is still loaded in the server; an unloaded one cannot be pregenerated. */
    boolean isLoaded();

    ChunkBorder border();

    /** Requests the chunk at the lowest priority; {@code done} is called once on the main thread. */
    void load(int chunkX, int chunkZ, ChunkLoaded done);

    ChunkTickets tickets();

    /** The space left on the file store holding the world, or {@link #UNKNOWN_FREE_BYTES} if it cannot be read. */
    long freeDiskBytes();
}
