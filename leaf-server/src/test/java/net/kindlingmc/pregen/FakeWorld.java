package net.kindlingmc.pregen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** A world for {@link PregenTask} tests: it records what the task asks for and completes loads when told to. */
final class FakeWorld implements PregenWorld, ChunkTickets {

    static final class Request {
        final int chunkX;
        final int chunkZ;
        final ChunkLoaded done;
        boolean answered;

        Request(final int chunkX, final int chunkZ, final ChunkLoaded done) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.done = done;
        }
    }

    final List<Request> requests = new ArrayList<>();
    final Set<Long> tickets = new TreeSet<>();
    ChunkBorder border = new ChunkBorder(-1_000_000, 1_000_000, -1_000_000, 1_000_000);
    long freeBytes = UNKNOWN_FREE_BYTES;
    long seed = 42L;
    boolean loaded = true;

    @Override
    public String name() {
        return "minecraft:fake";
    }

    @Override
    public long seed() {
        return this.seed;
    }

    @Override
    public boolean isLoaded() {
        return this.loaded;
    }

    @Override
    public ChunkBorder border() {
        return this.border;
    }

    @Override
    public void load(final int chunkX, final int chunkZ, final ChunkLoaded done) {
        this.requests.add(new Request(chunkX, chunkZ, done));
    }

    @Override
    public ChunkTickets tickets() {
        return this;
    }

    @Override
    public void add(final long chunkKey) {
        this.tickets.add(chunkKey);
    }

    @Override
    public void remove(final long chunkKey) {
        this.tickets.remove(chunkKey);
    }

    @Override
    public long freeDiskBytes() {
        return this.freeBytes;
    }

    /** Completes the request with the given number (in the order they were made), as the chunk system does. */
    void complete(final int requestNumber, final boolean generated) {
        final Request request = this.requests.get(requestNumber);
        request.answered = true;
        request.done.done(generated);
    }

    /** Completes every request not answered yet. */
    void completeAll() {
        for (int number = 0; number < this.requests.size(); number++) {
            if (!this.requests.get(number).answered) {
                this.complete(number, true);
            }
        }
    }
}
