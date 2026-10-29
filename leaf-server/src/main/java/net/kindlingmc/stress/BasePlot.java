package net.kindlingmc.stress;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

/**
 * A piece of a survival base in three chunks: a hopper room, a furnace room and a redstone board. Everything in it is
 * vanilla and keeps running by itself, except the redstone clock, which the test steps every {@value #CLOCK_TICKS}
 * ticks. The supplies in the furnace room last for hours, far beyond the longest test.
 */
final class BasePlot {

    static final int CHUNKS = 3;
    static final int CLOCK_TICKS = 10;

    private static final int[] RING_INSETS = {1, 3, 5};
    private static final int IDLE_FIRST = 6;
    private static final int IDLE_LAST = 9;
    private static final int[] FURNACE_ROWS = {4, 11};
    private static final int FIRST_X = 1;
    private static final int LAST_X = 14;
    private static final int[] REDSTONE_ROWS = {3, 8, 13};
    private static final int CLOCK_X = 0;
    private static final int ON_FLOOR = 1;

    private final PlotChunk hoppers;
    private final PlotChunk furnaces;
    private final PlotChunk redstone;
    private boolean clockOn;

    BasePlot(final World world, final KeptChunks kept, final int firstChunkX, final int chunkZ) {
        this.hoppers = new PlotChunk(world, kept, firstChunkX, chunkZ);
        this.furnaces = new PlotChunk(world, kept, firstChunkX + 1, chunkZ);
        this.redstone = new PlotChunk(world, kept, firstChunkX + 2, chunkZ);
    }

    /** Asks for the plot's chunks to be loaded; it can be built once {@link #isReady()}. */
    void reserve() {
        for (final PlotChunk chunk : this.chunks()) {
            chunk.keepLoaded();
        }
    }

    boolean isReady() {
        for (final PlotChunk chunk : this.chunks()) {
            if (!chunk.isLoaded()) {
                return false;
            }
        }
        return true;
    }

    void build() {
        for (final PlotChunk chunk : this.chunks()) {
            chunk.layFloor();
        }
        this.buildHopperRings();
        this.buildIdleHoppers();
        for (final int row : FURNACE_ROWS) {
            this.buildFurnaceRow(row);
        }
        for (final int row : REDSTONE_ROWS) {
            this.buildRepeaterLine(row);
        }
    }

    void clear() {
        for (final PlotChunk chunk : this.chunks()) {
            chunk.clear();
        }
    }

    private List<PlotChunk> chunks() {
        return List.of(this.hoppers, this.furnaces, this.redstone);
    }

    /** Switches the redstone block at the start of every repeater line on or off. */
    void clockStep() {
        this.clockOn = !this.clockOn;
        for (final int row : REDSTONE_ROWS) {
            this.redstone.block(CLOCK_X, ON_FLOOR, row).setType(this.clockOn ? Material.REDSTONE_BLOCK : Material.AIR, true);
        }
    }

    /**
     * Rings of hoppers that pass items around forever, each hopper holding one: every hopper works at every transfer,
     * as in item elevators and sorting systems.
     */
    private void buildHopperRings() {
        for (final int inset : RING_INSETS) {
            final int low = inset;
            final int high = 15 - inset;
            for (int i = low; i < high; i++) {
                this.ringHopper(i, low, BlockFace.EAST);
                this.ringHopper(high, i, BlockFace.SOUTH);
                this.ringHopper(high + low - i, high, BlockFace.WEST);
                this.ringHopper(low, high + low - i, BlockFace.NORTH);
            }
        }
    }

    private void ringHopper(final int x, final int z, final BlockFace face) {
        this.hoppers.place(x, ON_FLOOR, z, PlotChunk.facing(Material.HOPPER, face));
        this.hoppers.stock(x, ON_FLOOR, z, Material.COBBLESTONE, 1);
    }

    /** Hoppers between empty barrels, which can do nothing: what most hoppers on a real server do most of the time. */
    private void buildIdleHoppers() {
        for (int x = IDLE_FIRST; x <= IDLE_LAST; x++) {
            for (int z = IDLE_FIRST; z <= IDLE_LAST; z++) {
                this.hoppers.place(x, ON_FLOOR, z, Material.BARREL);
                this.hoppers.place(x, ON_FLOOR + 1, z, PlotChunk.facing(Material.HOPPER, BlockFace.DOWN));
                this.hoppers.place(x, ON_FLOOR + 2, z, Material.BARREL);
            }
        }
    }

    /**
     * Furnaces fed from barrels: cobblestone from above, coal from the side, the stone taken out below into a barrel.
     */
    private void buildFurnaceRow(final int z) {
        for (int x = FIRST_X; x <= LAST_X; x++) {
            this.furnaces.place(x, 1, z, Material.BARREL);
            this.furnaces.place(x, 2, z, PlotChunk.facing(Material.HOPPER, BlockFace.DOWN));
            this.furnaces.place(x, 3, z, PlotChunk.facing(Material.FURNACE, BlockFace.SOUTH));
            this.furnaces.place(x, 4, z, PlotChunk.facing(Material.HOPPER, BlockFace.DOWN));
            this.furnaces.place(x, 5, z, Material.BARREL);
            this.furnaces.fill(x, 5, z, Material.COBBLESTONE);
            this.furnaces.place(x, 3, z - 1, PlotChunk.facing(Material.HOPPER, BlockFace.SOUTH));
            this.furnaces.place(x, 4, z - 1, Material.BARREL);
            this.furnaces.fill(x, 4, z - 1, Material.COAL);
        }
    }

    /**
     * A line of repeaters and stone blocks, started by the clock. Each powered stone block pushes a sticky piston, whose
     * plank an observer watches to light a lamp, and lights a lamp beside it.
     */
    private void buildRepeaterLine(final int z) {
        for (int x = FIRST_X; x <= LAST_X; x++) {
            if (x % 2 == 1) {
                this.redstone.place(x, ON_FLOOR, z, PlotChunk.facing(Material.REPEATER, BlockFace.WEST));
            } else {
                this.buildPistonCell(x, z);
            }
        }
    }

    private void buildPistonCell(final int x, final int z) {
        this.redstone.place(x, ON_FLOOR, z, Material.STONE);
        this.redstone.place(x, ON_FLOOR, z + 1, Material.REDSTONE_LAMP);
        this.redstone.place(x, ON_FLOOR, z - 1, PlotChunk.facing(Material.STICKY_PISTON, BlockFace.NORTH));
        this.redstone.place(x, ON_FLOOR, z - 2, Material.OAK_PLANKS);
        this.redstone.place(x, ON_FLOOR + 1, z - 2, PlotChunk.facing(Material.OBSERVER, BlockFace.DOWN));
        this.redstone.place(x, ON_FLOOR + 2, z - 2, Material.REDSTONE_LAMP);
    }
}
