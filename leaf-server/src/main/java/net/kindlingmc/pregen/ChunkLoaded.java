package net.kindlingmc.pregen;

/** Called on the main thread when a requested chunk reached {@code FULL}, or when the request ended without a chunk. */
@FunctionalInterface
interface ChunkLoaded {

    void done(boolean loaded);
}
