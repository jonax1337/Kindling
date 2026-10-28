package net.kindlingmc.chunk;

/** How hard the machine pressed on the main thread during one host monitor sample, as the worker governor reads it. */
public enum HostPressure {
    /** The main thread waited for CPUs that others took, or the container's CPU limit stopped the server. */
    HIGH,
    /** Neither high nor clearly quiet, or the host took CPU time (steal) that more workers would not get. */
    MODERATE,
    /** Well below every contention threshold: there is room for another worker. */
    LOW,
    /** Nothing was measured that tells. */
    UNKNOWN
}
