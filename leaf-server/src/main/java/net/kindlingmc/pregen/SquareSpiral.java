package net.kindlingmc.pregen;

/**
 * Numbers the chunks of a square around a centre ring by ring, starting at the centre: index 0 is the centre, ring
 * {@code r} holds the {@code 8r} indices from {@code (2r-1)²} on. A single index therefore says how far a pregeneration
 * got, and the chunks near the centre (where players start) always come first.
 */
public final class SquareSpiral {

    private SquareSpiral() {
    }

    /** The number of chunks in the square of the given radius in chunks, centre included. */
    public static long size(final int radius) {
        final long side = 2L * radius + 1;
        return side * side;
    }

    /** The chunk offset from the centre for the given index; x in the high and z in the low 32 bits. */
    public static long offset(final long index) {
        if (index == 0) {
            return pack(0, 0);
        }
        final int ring = ringOf(index);
        final long inner = 2L * ring - 1;
        final long along = index - inner * inner;
        final int side = (int) (along / (2L * ring));
        final int step = (int) (along % (2L * ring));
        return switch (side) {
            case 0 -> pack(ring, -ring + 1 + step);
            case 1 -> pack(ring - 1 - step, ring);
            case 2 -> pack(-ring, ring - 1 - step);
            default -> pack(-ring + 1 + step, -ring);
        };
    }

    public static int x(final long offset) {
        return (int) (offset >> 32);
    }

    public static int z(final long offset) {
        return (int) offset;
    }

    private static int ringOf(final long index) {
        int ring = (int) Math.ceil((Math.sqrt(index + 1.0) - 1) / 2);
        while (size(ring) <= index) {
            ring++;
        }
        while (ring > 1 && size(ring - 1) > index) {
            ring--;
        }
        return ring;
    }

    private static long pack(final int x, final int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
