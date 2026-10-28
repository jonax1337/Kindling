package net.kindlingmc.sleeping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryCountChangeTest {

    private static final int COUNT_BEFORE = 64;

    private final TemporaryCountChange<Object> change = new TemporaryCountChange<>();
    private final Object stack = new Object();

    @Test
    void nothingIsOpenAtFirst() {
        assertFalse(this.change.isOpen());
        assertFalse(this.change.isUndoneBy(this.stack, COUNT_BEFORE));
    }

    @Test
    void theOldCountOfTheSameStackUndoesTheChange() {
        this.change.begin(this.stack, COUNT_BEFORE);

        assertTrue(this.change.isOpen());
        assertTrue(this.change.isUndoneBy(this.stack, COUNT_BEFORE));
    }

    @Test
    void anotherCountDoesNotUndoTheChange() {
        this.change.begin(this.stack, COUNT_BEFORE);

        assertFalse(this.change.isUndoneBy(this.stack, COUNT_BEFORE - 1));
    }

    @Test
    void anotherStackDoesNotUndoTheChange() {
        this.change.begin(this.stack, COUNT_BEFORE);

        assertFalse(this.change.isUndoneBy(new Object(), COUNT_BEFORE));
    }

    @Test
    void anEndedChangeCannotBeUndone() {
        this.change.begin(this.stack, COUNT_BEFORE);
        this.change.end();

        assertFalse(this.change.isOpen());
        assertFalse(this.change.isUndoneBy(this.stack, COUNT_BEFORE));
    }
}
