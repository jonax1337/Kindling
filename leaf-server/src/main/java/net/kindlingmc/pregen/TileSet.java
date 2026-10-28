package net.kindlingmc.pregen;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;

/**
 * A set of tiles of {@value TileSpiral#TILE_SIDE}×{@value TileSpiral#TILE_SIDE} chunks on the fixed grid of the world,
 * tile {@code (x, z)} holding the chunks {@code x*16 … x*16+15} and {@code z*16 … z*16+15}. Persisted as text, row by
 * row with runs of neighbours: {@code -3:-2..4,9;5:0} is the tiles (-2..4, -3), (9, -3) and (0, 5).
 */
final class TileSet {

    /** The largest tile coordinate a world border allows; a text with a larger one is not a set of tiles of a world. */
    static final int MAX_COORDINATE = TileSpiral.MAX_RADIUS / TileSpiral.TILE_SIDE + 1;

    private static final String ROW_SEPARATOR = ";";
    private static final String ROW_START = ":";
    private static final String RUN_SEPARATOR = ",";
    private static final String RUN_RANGE = "..";
    private static final long COLUMN_MASK = 0xFFFFFFFFL;

    private final LongOpenHashSet tiles = new LongOpenHashSet();

    static long key(final int tileX, final int tileZ) {
        return SquareSpiral.pack(tileX, tileZ);
    }

    static int x(final long key) {
        return SquareSpiral.x(key);
    }

    static int z(final long key) {
        return SquareSpiral.z(key);
    }

    /** The tile holding a chunk; chunk coordinates are floored, so chunk -1 is in tile -1. */
    static long tileOfChunk(final int chunkX, final int chunkZ) {
        return key(chunkX >> 4, chunkZ >> 4);
    }

    /** @return whether the tile was not in the set yet */
    boolean add(final long key) {
        return this.tiles.add(key);
    }

    boolean contains(final long key) {
        return this.tiles.contains(key);
    }

    int size() {
        return this.tiles.size();
    }

    long[] toArray() {
        return this.tiles.toLongArray();
    }

    TileSet copy() {
        final TileSet copy = new TileSet();
        copy.tiles.addAll(this.tiles);
        return copy;
    }

    String toText() {
        final long[] rowMajor = this.rowMajorKeys();
        final StringBuilder text = new StringBuilder();
        int index = 0;
        while (index < rowMajor.length) {
            final int row = rowOf(rowMajor[index]);
            if (!text.isEmpty()) {
                text.append(ROW_SEPARATOR);
            }
            text.append(row).append(ROW_START);
            index = appendRuns(text, rowMajor, index, row);
        }
        return text.toString();
    }

    /** @throws IllegalArgumentException if the text is not what {@link #toText()} writes */
    static TileSet parse(final String text) {
        final TileSet tiles = new TileSet();
        if (text.isBlank()) {
            return tiles;
        }
        for (final String row : text.trim().split(ROW_SEPARATOR, -1)) {
            final int rowStart = row.indexOf(ROW_START);
            if (rowStart < 0) {
                throw new IllegalArgumentException("a row has no ':': '" + row + "'");
            }
            final int z = coordinate(row.substring(0, rowStart));
            for (final String run : row.substring(rowStart + 1).split(RUN_SEPARATOR, -1)) {
                addRun(tiles, z, run);
            }
        }
        return tiles;
    }

    /** The keys with the row in the high bits, so that sorting them walks the rows from the lowest and each row from the left. */
    private long[] rowMajorKeys() {
        final long[] keys = this.tiles.toLongArray();
        for (int i = 0; i < keys.length; i++) {
            keys[i] = rowMajor(x(keys[i]), z(keys[i]));
        }
        Arrays.sort(keys);
        return keys;
    }

    private static long rowMajor(final int x, final int z) {
        return ((long) z << 32) | ((long) x - Integer.MIN_VALUE);
    }

    private static int rowOf(final long rowMajor) {
        return (int) (rowMajor >> 32);
    }

    private static int columnOf(final long rowMajor) {
        return (int) ((rowMajor & COLUMN_MASK) + Integer.MIN_VALUE);
    }

    /** Appends the runs of the row that starts at {@code from}; returns the index of the first key of the next row. */
    private static int appendRuns(final StringBuilder text, final long[] rowMajor, final int from, final int row) {
        int index = from;
        while (index < rowMajor.length && rowOf(rowMajor[index]) == row) {
            if (index > from) {
                text.append(RUN_SEPARATOR);
            }
            final int first = columnOf(rowMajor[index]);
            int last = first;
            index++;
            while (index < rowMajor.length && rowOf(rowMajor[index]) == row && columnOf(rowMajor[index]) == last + 1) {
                last++;
                index++;
            }
            text.append(first);
            if (last > first) {
                text.append(RUN_RANGE).append(last);
            }
        }
        return index;
    }

    private static void addRun(final TileSet tiles, final int z, final String run) {
        final int range = run.indexOf(RUN_RANGE);
        final int first = coordinate(range < 0 ? run : run.substring(0, range));
        final int last = range < 0 ? first : coordinate(run.substring(range + RUN_RANGE.length()));
        if (last < first) {
            throw new IllegalArgumentException("a run ends before it starts: '" + run + "'");
        }
        for (int x = first; x <= last; x++) {
            tiles.add(key(x, z));
        }
    }

    private static int coordinate(final String text) {
        final int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("not a tile coordinate: '" + text + "'");
        }
        if (Math.abs(value) > MAX_COORDINATE) {
            throw new IllegalArgumentException("a tile coordinate is beyond any world border: " + value);
        }
        return value;
    }
}
