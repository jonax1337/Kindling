package net.kindlingmc.stress;

import java.time.Duration;

/**
 * Notices when a stress test has pushed the server too far: tick times above {@value #LIMIT_MSPT} ms (4 TPS) for
 * {@link #LIMIT} of real time in a row, however few ticks that is. Real players share the server, so the test then
 * stops by itself.
 */
final class OverloadWatch {

    static final double LIMIT_MSPT = 250;
    static final Duration LIMIT = Duration.ofSeconds(20);

    private Duration over = Duration.ZERO;

    /**
     * Takes the tick time of the time since the last call, {@code elapsed}, and answers whether the server has now been
     * over the limit too long.
     */
    boolean isOverloaded(final double mspt, final Duration elapsed) {
        this.over = mspt > LIMIT_MSPT ? this.over.plus(elapsed) : Duration.ZERO;
        return this.over.compareTo(LIMIT) >= 0;
    }
}
