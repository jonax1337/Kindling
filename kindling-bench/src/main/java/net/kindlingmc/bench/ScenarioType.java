package net.kindlingmc.bench;

import java.util.Locale;

/** Which load a run puts on the server, chosen with the scenario key {@code type}. */
enum ScenarioType {

    /** Players fly out over new terrain, then stand among mobs; see {@link BenchRun}. */
    MIXED,
    /** Players stand in survival bases full of hoppers, furnaces and redstone; see {@link BaseRun}. */
    BASES;

    static ScenarioType named(String name) {
        return valueOf(name.trim().toUpperCase(Locale.ROOT));
    }
}
