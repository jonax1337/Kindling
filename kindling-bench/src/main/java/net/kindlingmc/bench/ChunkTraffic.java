package net.kindlingmc.bench;

/**
 * What the server sent a fake player of its chunk stream.
 *
 * @param chunkPackets chunk data packets
 * @param batches chunk batches (a batch is one start and one finished packet around its chunks); stays 0 as long as
 *        the server does not send chunks in batches
 */
record ChunkTraffic(long chunkPackets, long batches) {

    static final ChunkTraffic NONE = new ChunkTraffic(0, 0);

    ChunkTraffic plus(ChunkTraffic other) {
        return new ChunkTraffic(chunkPackets + other.chunkPackets, batches + other.batches);
    }

    ChunkTraffic minus(ChunkTraffic other) {
        return new ChunkTraffic(chunkPackets - other.chunkPackets, batches - other.batches);
    }
}
