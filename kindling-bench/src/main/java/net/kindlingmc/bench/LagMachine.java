package net.kindlingmc.bench;

import java.util.Locale;
import java.util.function.Function;

/** A lag machine a {@link ScenarioType#LAG lag} run can build beside the counted contraptions; see {@link LagLayout}. */
enum LagMachine {

    /** Observer clocks driving dust over lamps: block updates and light. */
    OBSERVERS(ObserverStorm::new),
    /** A chamber where TNT keeps exploding: explosions and Spigot's TNT budget. */
    TNT(TntChamber::lagMachine),
    /** Pits crammed with minecarts: entity collisions. */
    CARTS(CartPit::new);

    private final Function<ChunkSite, Contraption> builder;

    LagMachine(Function<ChunkSite, Contraption> builder) {
        this.builder = builder;
    }

    Contraption in(ChunkSite site) {
        return builder.apply(site);
    }

    static LagMachine named(String name) {
        return valueOf(name.trim().toUpperCase(Locale.ROOT));
    }
}
