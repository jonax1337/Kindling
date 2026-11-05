package net.kindlingmc.bench;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/**
 * A lag machine of redstone: eight observer clocks, each driving a line of dust laid over redstone lamps, so every
 * pulse updates a dozen dust blocks and switches a dozen lamps, and with them the light around. It runs by itself
 * from the build on and counts nothing.
 */
final class ObserverStorm implements Contraption {

    private static final int FIRST_ROW = 1;
    private static final int LAST_ROW = 15;
    private static final int ROW_SPACING = 2;
    private static final int WATCHING_EAST_X = 1;
    private static final int WATCHING_WEST_X = 2;
    private static final int FIRST_DUST_X = 3;
    /** The dust ends one block short of the chunk's edge, so it never powers a neighbour. */
    private static final int LAST_DUST_X = 14;
    private static final int FLOOR = 0;
    private static final int ON_FLOOR = 1;

    private final ChunkSite site;

    ObserverStorm(ChunkSite site) {
        this.site = site;
    }

    @Override
    public void build() {
        for (int z = FIRST_ROW; z <= LAST_ROW; z += ROW_SPACING) {
            buildRow(z);
        }
    }

    /** Dust on lamps east of an observer pair; the observer watching west puts its pulses out east into the dust. */
    private void buildRow(int z) {
        for (int x = FIRST_DUST_X; x <= LAST_DUST_X; x++) {
            site.place(x, FLOOR, z, Material.REDSTONE_LAMP);
            site.place(x, ON_FLOOR, z, BlockStates.wire(BlockFace.WEST, BlockFace.EAST));
        }
        site.place(WATCHING_EAST_X, ON_FLOOR, z, BlockStates.facing(Material.OBSERVER, BlockFace.EAST));
        site.placeWithUpdates(WATCHING_WEST_X, ON_FLOOR, z, BlockStates.facing(Material.OBSERVER, BlockFace.WEST));
    }

    @Override
    public Map<String, Long> totals() {
        return Map.of();
    }
}
