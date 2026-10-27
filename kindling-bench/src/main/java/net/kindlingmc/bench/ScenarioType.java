package net.kindlingmc.bench;

import java.util.Locale;

/** Which load a run puts on the server, chosen with the scenario key {@code type}. */
enum ScenarioType {

    /** Players fly out over new terrain, then stand among mobs; see {@link BenchRun}. */
    MIXED,
    /** Players stand in survival bases full of hoppers, furnaces and redstone; see {@link BaseRun}. */
    BASES,
    /** Block entities and plugin stand-ins in the cases where sleeping block entities can differ; see {@link SleepingLayout}. */
    SLEEPING,
    /** Villagers competing for beds and job sites in sealed halls; see {@link VillagerLayout}. */
    VILLAGERS;

    static ScenarioType named(String name) {
        return valueOf(name.trim().toUpperCase(Locale.ROOT));
    }
}
