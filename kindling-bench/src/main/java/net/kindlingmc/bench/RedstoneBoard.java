package net.kindlingmc.bench;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/**
 * Redstone as it runs in farms and doors: a clock feeds a trunk of dust that branches into three lines. Along each line
 * sticky pistons push blocks back and forth, observers watch those blocks and light lamps, and at its end a dropper
 * passes one item per pulse into a barrel. The run drives only the clock, a redstone block it switches every
 * {@value #HALF_PERIOD_TICKS} ticks ({@link #toggleClock()}); everything after it is vanilla redstone.
 */
final class RedstoneBoard implements Contraption {

    static final int HALF_PERIOD_TICKS = 10;

    private static final int TRUNK_X = 1;
    private static final int CLOCK_Z = 0;
    private static final int LAST_TRUNK_Z = 14;
    private static final int[] BRANCH_ROWS = {4, 9, LAST_TRUNK_Z};
    private static final int REPEATER_X = 2;
    private static final int FIRST_LINE_X = 3;
    private static final int LAST_LINE_X = 13;
    private static final int DROPPER_X = 14;
    private static final int[] PISTON_COLUMNS = {4, 6, 8, 10, 12};
    private static final int FLOOR = 0;
    private static final int ON_FLOOR = 1;
    private static final int ABOVE = 2;
    /** A full dropper, one item per clock period: enough for warm-up and measurement of together about nine minutes. */
    private static final int DROPPER_SUPPLY = 9 * 64;

    private final ChunkSite site;
    private boolean clockOn;

    RedstoneBoard(ChunkSite site) {
        this.site = site;
    }

    @Override
    public void build() {
        for (int z = CLOCK_Z + 1; z <= LAST_TRUNK_Z; z++) {
            site.place(TRUNK_X, ON_FLOOR, z, BlockStates.wire(trunkConnections(z)));
        }
        for (int row : BRANCH_ROWS) {
            buildBranch(row);
        }
    }

    /** One step of the clock: switches its redstone block on or off, with block updates. */
    void toggleClock() {
        clockOn = !clockOn;
        site.block(TRUNK_X, ON_FLOOR, CLOCK_Z).setType(clockOn ? Material.REDSTONE_BLOCK : Material.AIR, true);
    }

    private static BlockFace[] trunkConnections(int z) {
        List<BlockFace> connections = new ArrayList<>(List.of(BlockFace.NORTH));
        if (z < LAST_TRUNK_Z) {
            connections.add(BlockFace.SOUTH);
        }
        if (isBranchRow(z)) {
            connections.add(BlockFace.EAST);
        }
        return connections.toArray(BlockFace[]::new);
    }

    private static boolean isBranchRow(int z) {
        return Arrays.stream(BRANCH_ROWS).anyMatch(row -> row == z);
    }

    private void buildBranch(int z) {
        site.place(REPEATER_X, ON_FLOOR, z, BlockStates.facing(Material.REPEATER, BlockFace.WEST));
        for (int x = FIRST_LINE_X; x <= LAST_LINE_X; x++) {
            site.place(x, ON_FLOOR, z, BlockStates.wire(BlockFace.WEST, BlockFace.EAST));
        }
        site.place(DROPPER_X, ON_FLOOR, z, BlockStates.facing(Material.DROPPER, BlockFace.SOUTH));
        site.stock(DROPPER_X, ON_FLOOR, z, Material.COBBLESTONE, DROPPER_SUPPLY);
        site.place(DROPPER_X, ON_FLOOR, z + 1, Material.BARREL);
        for (int x : PISTON_COLUMNS) {
            buildPiston(x, z);
        }
    }

    /**
     * A sticky piston set into the floor next to the line, which powers it through the floor block under the dust; it
     * pushes a plank north and pulls it back, an observer above the plank lights a lamp at every move.
     */
    private void buildPiston(int x, int lineZ) {
        int plankZ = lineZ - 2;
        site.place(x, FLOOR, lineZ - 1, BlockStates.facing(Material.STICKY_PISTON, BlockFace.NORTH));
        site.place(x, FLOOR, plankZ, Material.OAK_PLANKS);
        site.place(x, FLOOR, plankZ - 1, Material.AIR);
        site.place(x, ON_FLOOR, plankZ, BlockStates.facing(Material.OBSERVER, BlockFace.DOWN));
        site.place(x, ABOVE, plankZ, Material.REDSTONE_LAMP);
    }

    /** Clock pulses that reached the end of each line. */
    @Override
    public Map<String, Long> totals() {
        long pulses = 0;
        for (int row : BRANCH_ROWS) {
            pulses += site.items(DROPPER_X, ON_FLOOR, row + 1);
        }
        return Map.of("redstone_pulses", pulses);
    }
}
