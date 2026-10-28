package net.kindlingmc.chunk;

/**
 * The bounds of the worker governor and how long it waits before it adds a worker.
 *
 * @param floor         the fewest chunk workers: Paper's default for the machine, at most the ceiling
 * @param ceiling       the configured count, or Kindling's default when none is configured
 * @param quietSamples  how many samples of low pressure in a row add one worker
 */
public record GovernorLimits(int floor, int ceiling, int quietSamples) {

    public static GovernorLimits of(final int paperDefault, final int configured, final int quietSamples) {
        final int ceiling = Math.max(1, configured);
        return new GovernorLimits(Math.clamp(paperDefault, 1, ceiling), ceiling, Math.max(1, quietSamples));
    }

    /** Whether there is a count to choose at all. */
    public boolean leavesRoom() {
        return this.floor < this.ceiling;
    }
}
