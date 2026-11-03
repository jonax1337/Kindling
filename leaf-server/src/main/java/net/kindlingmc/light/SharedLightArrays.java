package net.kindlingmc.light;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * One read-only nibble array for every light section whose 4096 levels are all 15, and one for those that are all 0.
 * Starlight keeps a private 2 KiB array per initialised section, and most of them are uniform: the open sky above the
 * terrain and dark stone below it. Sharing them leaves every light level as it was.
 *
 * <p>Nothing may write into a shared array: {@code SWMRNibbleArray} copies its array before the first write after a
 * publish, and it never hands a shared array to its pool of reusable arrays.
 */
public final class SharedLightArrays {

    /** Bytes in one nibble array: 4096 light levels of 4 bits. */
    public static final int SIZE = 2048;

    private static final byte[] ALL_FULL = filled((byte) -1);
    private static final byte[] ALL_DARK = new byte[SIZE];
    private static final VarHandle LONGS = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());

    private SharedLightArrays() {
    }

    /** The shared array with the content of {@code nibbles} when it is all 15 or all 0; otherwise {@code nibbles} itself. */
    public static byte[] shareIfUniform(final byte[] nibbles) {
        final long first = (long) LONGS.get(nibbles, 0);
        if (first != 0L && first != -1L) {
            return nibbles;
        }
        for (int offset = Long.BYTES; offset < SIZE; offset += Long.BYTES) {
            if ((long) LONGS.get(nibbles, offset) != first) {
                return nibbles;
            }
        }
        return first == 0L ? ALL_DARK : ALL_FULL;
    }

    /** Whether {@code nibbles} is one of the shared arrays, which must never be written or reused. */
    public static boolean isShared(final byte[] nibbles) {
        return nibbles == ALL_FULL || nibbles == ALL_DARK;
    }

    private static byte[] filled(final byte value) {
        final byte[] nibbles = new byte[SIZE];
        Arrays.fill(nibbles, value);
        return nibbles;
    }
}
