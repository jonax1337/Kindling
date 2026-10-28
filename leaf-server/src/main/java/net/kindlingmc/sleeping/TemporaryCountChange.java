package net.kindlingmc.sleeping;

/**
 * The one stack of an inventory whose count was changed for a moment and not yet set back. With a plugin listening to
 * {@code InventoryMoveItemEvent}, Paper's hopper sets the stack it moves from to the moved amount for the event and
 * sets it back when nothing moved; with Lithium's change tracking each of these count changes would wake every hopper
 * sleeping on the inventory. While a change is open nothing else in the inventory has changed (any other change ends
 * it), so once the stack is back at its old count the inventory holds what it held before and nobody needs waking.
 *
 * @param <S> the type of the stack
 */
public final class TemporaryCountChange<S> {

    private S stack;
    private int countBefore;

    /** Whether a stack's count was changed for a moment and has not been set back or followed by another change. */
    public boolean isOpen() {
        return this.stack != null;
    }

    /** Remembers that {@code stack}, which held {@code countBefore} items, is about to get another count for a moment. */
    public void begin(final S stack, final int countBefore) {
        this.stack = stack;
        this.countBefore = countBefore;
    }

    /** Whether setting {@code stack} to {@code newCount} items sets the open change back. */
    public boolean isUndoneBy(final S stack, final int newCount) {
        return this.stack == stack && this.countBefore == newCount;
    }

    /** The inventory changed otherwise, or the stack is back at its old count: nothing is open any more. */
    public void end() {
        this.stack = null;
    }
}
