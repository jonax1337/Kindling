package net.kindlingmc.bench;

import java.lang.management.ManagementFactory;

/**
 * Heap bytes allocated so far by the calling thread and by all threads of the JVM, as HotSpot counts them. A JVM that
 * does not count reports -1, which {@link #isCounted()} tells apart from a real count.
 *
 * @param currentThread bytes the calling thread allocated
 * @param allThreads bytes all threads allocated since the JVM started, an approximation by the JVM's own account
 */
record AllocatedBytes(long currentThread, long allThreads) {

    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    static AllocatedBytes now() {
        return new AllocatedBytes(THREADS.getCurrentThreadAllocatedBytes(), THREADS.getTotalThreadAllocatedBytes());
    }

    boolean isCounted() {
        return currentThread >= 0 && allThreads >= 0;
    }

    AllocatedBytes since(AllocatedBytes earlier) {
        return new AllocatedBytes(currentThread - earlier.currentThread, allThreads - earlier.allThreads);
    }
}
