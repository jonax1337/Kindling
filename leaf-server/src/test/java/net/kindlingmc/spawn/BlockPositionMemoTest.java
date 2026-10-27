package net.kindlingmc.spawn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BlockPositionMemoTest {

    private final BlockPositionMemo<Object> memo = new BlockPositionMemo<>();
    private final Object value = new Object();

    @Test
    void remembersNothingWhenEmpty() {
        assertNull(this.memo.get(0, 0, 0));
    }

    @Test
    void returnsTheValueForTheExactPosition() {
        this.memo.put(12, -64, -7, this.value);

        assertSame(this.value, this.memo.get(12, -64, -7));
    }

    @Test
    void missesWhenAnyCoordinateDiffers() {
        this.memo.put(12, 64, -7, this.value);

        assertNull(this.memo.get(13, 64, -7));
        assertNull(this.memo.get(12, 65, -7));
        assertNull(this.memo.get(12, 64, -8));
    }

    @Test
    void keepsOnlyTheLastPosition() {
        final Object other = new Object();
        this.memo.put(1, 2, 3, this.value);
        this.memo.put(4, 5, 6, other);

        assertNull(this.memo.get(1, 2, 3));
        assertSame(other, this.memo.get(4, 5, 6));
    }

    @Test
    void forgetsTheValueWhenCleared() {
        this.memo.put(1, 2, 3, this.value);

        this.memo.clear();

        assertNull(this.memo.get(1, 2, 3));
    }
}
