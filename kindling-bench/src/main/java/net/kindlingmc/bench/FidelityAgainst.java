package net.kindlingmc.bench;

import java.util.Locale;

/** What a fidelity run compares each sampled chunk with, chosen with the scenario key {@code fidelity-against}. */
enum FidelityAgainst {

    /** The seed preview of the chunk, as the server sends it. */
    PREVIEW,
    /** A second real generation of the chunk in a twin world of the same seed, generated in a different order; see {@link TwinWorld}. */
    TWIN,
    /**
     * The control of {@link #TWIN}: the twin generates every chunk in the same order as the main world, so the two
     * should differ only where generation is not deterministic.
     */
    TWIN_SAME_ORDER;

    static FidelityAgainst named(String name) {
        return valueOf(name.trim().toUpperCase(Locale.ROOT));
    }

    /** The value in the scenario file and in the result. */
    String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
