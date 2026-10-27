package net.kindlingmc.os;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Gives the calling thread a nice value through the C library: {@code setpriority(PRIO_PROCESS, gettid(), nice)}. Linux
 * keeps a nice value per thread, so this changes only the calling thread. Needs glibc 2.30 or musl 1.2.2 for
 * {@code gettid}. The class fails to initialise where the C library lacks these functions or native access is denied.
 */
final class LinuxNice {

    private static final int PRIO_PROCESS = 0;
    private static final String ERRNO = "errno";
    private static final Linker LINKER = Linker.nativeLinker();
    private static final StructLayout CALL_STATE = Linker.Option.captureStateLayout();
    private static final VarHandle ERRNO_VALUE = CALL_STATE.varHandle(MemoryLayout.PathElement.groupElement(ERRNO));
    private static final MethodHandle GET_TID = LINKER.downcallHandle(
        LINKER.defaultLookup().findOrThrow("gettid"), FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle SET_PRIORITY = LINKER.downcallHandle(
        LINKER.defaultLookup().findOrThrow("setpriority"),
        FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT),
        Linker.Option.captureCallState(ERRNO));

    private LinuxNice() {
    }

    /**
     * Gives the calling thread the nice value, unless it already has that or a higher one: a priority is never raised.
     *
     * @throws IllegalStateException when the current value cannot be read or the C library refuses the new one
     */
    static void lowerCurrentThread(final int nice) {
        final int threadId = currentThreadId();
        final int current = ThreadNice.ofThread(threadId)
            .orElseThrow(() -> new IllegalStateException("cannot read the nice value of thread " + threadId));
        if (current >= nice) {
            return;
        }
        final int errno = setNice(threadId, nice);
        if (errno != 0) {
            throw new IllegalStateException("setpriority failed with errno " + errno);
        }
    }

    private static int currentThreadId() {
        try {
            return (int) GET_TID.invokeExact();
        } catch (final Throwable e) {
            throw new IllegalStateException("gettid failed", e);
        }
    }

    /** Returns 0 on success, else the C library's error number. */
    private static int setNice(final int threadId, final int nice) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment callState = arena.allocate(CALL_STATE);
            final int result = (int) SET_PRIORITY.invokeExact(callState, PRIO_PROCESS, threadId, nice);
            return result == 0 ? 0 : (int) ERRNO_VALUE.get(callState, 0L);
        } catch (final Throwable e) {
            throw new IllegalStateException("setpriority failed", e);
        }
    }
}
