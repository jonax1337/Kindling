package net.kindlingmc.doctor;

/** The load over every window, published as one value so a reader never sees two windows from different samples. */
record HostLoads(HostLoad lastMinute, HostLoad last15Minutes) {

    static final HostLoads NONE = new HostLoads(HostLoad.NONE, HostLoad.NONE);

    HostLoad of(final HostWindow window) {
        return switch (window) {
            case LAST_MINUTE -> this.lastMinute;
            case LAST_15_MINUTES -> this.last15Minutes;
        };
    }
}
