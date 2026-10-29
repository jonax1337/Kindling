package net.kindlingmc.stress;

/**
 * The flight of one fake player: out along its line to the range at about elytra speed, back to its start, and out
 * again, so the first pass generates chunks and every later one loads them. Each step starts from where the server last
 * accepted the player, so a move the server held back (while a teleport waits for its answer) never turns into a jump
 * the server refuses as moving too quickly.
 */
final class Flight {

    static final double BLOCKS_PER_TICK = 1;

    private boolean outward = true;

    /** The offset to move to next, from the player's current {@code offset} along its line. */
    double next(final double offset) {
        if (offset >= PlayerLayout.FLIGHT_RANGE) {
            this.outward = false;
        } else if (offset <= 0) {
            this.outward = true;
        }
        final double step = this.outward ? BLOCKS_PER_TICK : -BLOCKS_PER_TICK;
        return Math.clamp(offset + step, 0, PlayerLayout.FLIGHT_RANGE);
    }
}
