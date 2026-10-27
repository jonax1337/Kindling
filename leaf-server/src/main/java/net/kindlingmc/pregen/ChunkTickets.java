package net.kindlingmc.pregen;

/** The tickets that keep a chunk's data in memory while a pregeneration needs its neighbours. */
interface ChunkTickets {

    void add(long chunkKey);

    void remove(long chunkKey);
}
