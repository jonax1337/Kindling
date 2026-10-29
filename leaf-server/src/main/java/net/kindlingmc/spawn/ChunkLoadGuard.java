package net.kindlingmc.spawn;

/**
 * Keeps the spawn pre-checks from loading chunks. While the main thread waits for them, a load on a helper thread
 * would wait for the main thread forever, and a load on the main thread would change the world the helpers read.
 * {@code ServerChunkCache} calls {@link #refuseLoadWhileRecording} right before it loads a chunk synchronously; during a
 * recording that throws {@link ChunkNotPresent} instead, and the main thread does that step itself later, the vanilla
 * way. The pre-checks only look chunks up, so this is the safety net for a spawn rule that reads further than expected.
 */
public final class ChunkLoadGuard {

    private static final ThreadLocal<Boolean> RECORDING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** A chunk a pre-check needs is not loaded at the status it needs. */
    public static final class ChunkNotPresent extends RuntimeException {
        static final ChunkNotPresent INSTANCE = new ChunkNotPresent();

        private ChunkNotPresent() {
            super("chunk not present", null, false, false);
        }
    }

    private ChunkLoadGuard() {
    }

    public static void refuseLoadWhileRecording() {
        if (RECORDING.get()) {
            throw ChunkNotPresent.INSTANCE;
        }
    }

    static void startRecording() {
        RECORDING.set(Boolean.TRUE);
    }

    static void stopRecording() {
        RECORDING.set(Boolean.FALSE);
    }
}
