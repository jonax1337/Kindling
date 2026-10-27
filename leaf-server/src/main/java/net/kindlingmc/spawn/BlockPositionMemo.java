package net.kindlingmc.spawn;

/**
 * Remembers the value looked up for the last block position, so asking for the same exact position again skips the
 * lookup. The owner decides when the value may be stale and calls {@link #clear()}. Not thread safe: one memo belongs
 * to the one thread that ticks the level it is kept for.
 *
 * @param <V> the remembered value, never {@code null}
 */
public final class BlockPositionMemo<V> {

    private int x;
    private int y;
    private int z;
    private V value;

    /** The value remembered for exactly this position, or {@code null} when there is none. */
    public V get(final int x, final int y, final int z) {
        return this.x == x && this.y == y && this.z == z ? this.value : null;
    }

    public void put(final int x, final int y, final int z, final V value) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.value = value;
    }

    public void clear() {
        this.value = null;
    }
}
