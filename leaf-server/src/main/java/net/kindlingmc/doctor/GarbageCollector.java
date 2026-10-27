package net.kindlingmc.doctor;

/** The collectors a server JVM can run with. */
public enum GarbageCollector {
    G1,
    ZGC,
    SHENANDOAH,
    PARALLEL,
    SERIAL
}
