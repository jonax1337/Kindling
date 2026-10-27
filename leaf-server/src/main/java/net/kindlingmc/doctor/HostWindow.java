package net.kindlingmc.doctor;

import java.time.Duration;

/** The stretches of time the host monitor reports on. */
public enum HostWindow {
    LAST_MINUTE(Duration.ofMinutes(1)),
    LAST_15_MINUTES(Duration.ofMinutes(15));

    private final Duration length;

    HostWindow(final Duration length) {
        this.length = length;
    }

    long nanos() {
        return this.length.toNanos();
    }
}
