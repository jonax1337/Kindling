package net.kindlingmc.bench;

import org.bukkit.Chunk;

/**
 * A line from a point in a direction, followed chunk by chunk: every call returns the next chunk the line enters, so no
 * chunk it passes through is skipped, however the line runs across the grid.
 */
final class ChunkRay {

    private static final int CHUNK_SIZE = 16;

    private final int stepX;
    private final int stepZ;
    private final double blocksPerChunkX;
    private final double blocksPerChunkZ;
    private int chunkX;
    private int chunkZ;
    private double reachNextX;
    private double reachNextZ;

    /** @param angle direction in radians, 0 along +x and a quarter turn along +z, as the flight directions are given */
    ChunkRay(double x, double z, double angle) {
        double directionX = Math.cos(angle);
        double directionZ = Math.sin(angle);
        chunkX = Math.floorDiv((int) Math.floor(x), CHUNK_SIZE);
        chunkZ = Math.floorDiv((int) Math.floor(z), CHUNK_SIZE);
        stepX = directionX >= 0 ? 1 : -1;
        stepZ = directionZ >= 0 ? 1 : -1;
        blocksPerChunkX = CHUNK_SIZE / Math.abs(directionX);
        blocksPerChunkZ = CHUNK_SIZE / Math.abs(directionZ);
        reachNextX = distanceToEdge(x, chunkX, stepX) / Math.abs(directionX);
        reachNextZ = distanceToEdge(z, chunkZ, stepZ) / Math.abs(directionZ);
    }

    long nextChunkKey() {
        if (reachNextX < reachNextZ) {
            chunkX += stepX;
            reachNextX += blocksPerChunkX;
        } else {
            chunkZ += stepZ;
            reachNextZ += blocksPerChunkZ;
        }
        return Chunk.getChunkKey(chunkX, chunkZ);
    }

    private static double distanceToEdge(double position, int chunk, int step) {
        return step > 0 ? (chunk + 1) * CHUNK_SIZE - position : position - chunk * CHUNK_SIZE;
    }
}
