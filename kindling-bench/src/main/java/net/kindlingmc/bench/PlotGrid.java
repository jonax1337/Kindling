package net.kindlingmc.bench;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Where each player's plots lie: in two columns around the spot the player hovers at, with a free chunk between the
 * columns and between the rows, all within a few chunks of the player and so ticking like a base its owner stands in.
 * The players' groups lie {@value #GROUP_SPACING} chunks apart, away from the spawn chunks.
 */
final class PlotGrid {

    private static final int GROUP_SPACING = 10;
    private static final int COLUMNS = 2;
    private static final int ROW_SPACING = 2;
    /** More plots would reach into the next player's group. */
    static final int MAX_PLOTS_PER_PLAYER = GROUP_SPACING / ROW_SPACING * COLUMNS;

    private static final int FIRST_CHUNK = 16;
    private static final int GROUPS_PER_ROW = 4;
    private static final int COLUMN_SPACING = Plot.CHUNKS + 1;
    private static final double CHUNK_CENTRE = ChunkSite.SIZE / 2.0;

    private final World world;
    private final int plotsPerPlayer;

    PlotGrid(World world, int plotsPerPlayer) {
        this.world = world;
        this.plotsPerPlayer = plotsPerPlayer;
    }

    List<Plot> plotsOf(int player) {
        List<Plot> plots = new ArrayList<>();
        for (int plot = 0; plot < plotsPerPlayer; plot++) {
            plots.add(new Plot(world, groupChunkX(player) + plot % COLUMNS * COLUMN_SPACING,
                    groupChunkZ(player) + plot / COLUMNS * ROW_SPACING));
        }
        return plots;
    }

    /** In the free chunk between the two columns, level with the floors, beside the middle row. */
    Location standingSpot(int player) {
        int chunkX = groupChunkX(player) + Plot.CHUNKS;
        int chunkZ = groupChunkZ(player) + rows() - 1;
        return new Location(world, chunkX * ChunkSite.SIZE + CHUNK_CENTRE, Plot.FLOOR_Y + 1,
                chunkZ * ChunkSite.SIZE + CHUNK_CENTRE);
    }

    private int rows() {
        return (plotsPerPlayer + COLUMNS - 1) / COLUMNS;
    }

    private static int groupChunkX(int player) {
        return FIRST_CHUNK + player % GROUPS_PER_ROW * GROUP_SPACING;
    }

    private static int groupChunkZ(int player) {
        return FIRST_CHUNK + player / GROUPS_PER_ROW * GROUP_SPACING;
    }
}
