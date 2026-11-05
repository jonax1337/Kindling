package net.kindlingmc.bench;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

/**
 * Clocks that run by themselves, as the fast clocks of farms do, each driving a sticky piston whose extensions are
 * counted ({@link PistonTally}): two observers watching each other, an observer watching the block its own piston
 * moves, and an item passed back and forth between two hoppers with a comparator reading one of them. Nothing but
 * vanilla redstone keeps them going, so under any load they must count exactly what an unloaded server counts; a
 * clock that stalls or skips under load shows up as a smaller count.
 *
 * <p>The clocks are built without block updates and started by placing their last block with updates, the way a
 * player starts one; the hopper clock starts by itself.
 */
final class ClockBoard implements Contraption {

    static final String OBSERVER_CLOCK = "clock_observer_pair_extensions";
    static final String PISTON_OBSERVER_CLOCK = "clock_piston_observer_extensions";
    static final String HOPPER_CLOCK = "clock_hopper_extensions";

    private static final int ON_FLOOR = 1;

    private final ChunkSite observerPair;
    private final ChunkSite pistonObserver;
    private final ChunkSite hopperPair;
    private final PistonTally tally;

    ClockBoard(ChunkSite site, PistonTally tally) {
        this.observerPair = site.corner(0, 0);
        this.pistonObserver = site.corner(6, 0);
        this.hopperPair = site.corner(11, 0);
        this.tally = tally;
    }

    @Override
    public void build() {
        buildObserverPair();
        buildPistonObserver();
        buildHopperPair();
    }

    /**
     * Two observers face each other at (2, 2) and (3, 2): each pulse of one is a change the other sees. Each puts its
     * pulse out of its back into a sticky piston, the west one pushing north, the east one south.
     */
    private void buildObserverPair() {
        ChunkSite site = observerPair;
        buildPiston(site, 1, 2, BlockFace.NORTH, OBSERVER_CLOCK);
        buildPiston(site, 4, 2, BlockFace.SOUTH, OBSERVER_CLOCK);
        site.place(2, ON_FLOOR, 2, BlockStates.facing(Material.OBSERVER, BlockFace.EAST));
        site.placeWithUpdates(3, ON_FLOOR, 2, BlockStates.facing(Material.OBSERVER, BlockFace.WEST));
    }

    /**
     * A sticky piston at (2, 3) pushes a plank north from (2, 2); an observer at (1, 2) watches the plank's spot and
     * powers the piston through three dust around its back. Every move of the plank is a change, so pushing it out
     * and pulling it back each fire the observer again. Placing the plank starts it.
     */
    private void buildPistonObserver() {
        ChunkSite site = pistonObserver;
        site.place(2, ON_FLOOR, 3, BlockStates.facing(Material.STICKY_PISTON, BlockFace.NORTH));
        tally.watch(site.block(2, ON_FLOOR, 3), PISTON_OBSERVER_CLOCK);
        site.place(2, ON_FLOOR, 1, Material.AIR);
        site.place(1, ON_FLOOR, 2, BlockStates.facing(Material.OBSERVER, BlockFace.EAST));
        site.place(0, ON_FLOOR, 2, BlockStates.wire(BlockFace.SOUTH));
        site.place(0, ON_FLOOR, 3, BlockStates.wire(BlockFace.NORTH, BlockFace.EAST));
        site.place(1, ON_FLOOR, 3, BlockStates.wire(BlockFace.WEST, BlockFace.EAST));
        site.placeWithUpdates(2, ON_FLOOR, 2, Material.OAK_PLANKS.createBlockData());
    }

    /**
     * Two hoppers at (1, 1) and (2, 1) face each other and pass one item back and forth. A comparator south of the
     * west hopper reads it and powers a sticky piston whenever the item is in that hopper.
     */
    private void buildHopperPair() {
        ChunkSite site = hopperPair;
        site.place(1, ON_FLOOR, 1, BlockStates.facing(Material.HOPPER, BlockFace.EAST));
        site.place(2, ON_FLOOR, 1, BlockStates.facing(Material.HOPPER, BlockFace.WEST));
        site.put(1, ON_FLOOR, 1, 0, new ItemStack(Material.COBBLESTONE));
        site.place(1, ON_FLOOR, 2, BlockStates.facing(Material.COMPARATOR, BlockFace.NORTH));
        buildPiston(site, 1, 3, BlockFace.SOUTH, HOPPER_CLOCK);
    }

    /** A counted sticky piston at {@code (x, z)} with a plank before it and room to push the plank into. */
    private void buildPiston(ChunkSite site, int x, int z, BlockFace face, String name) {
        site.place(x, ON_FLOOR, z, BlockStates.facing(Material.STICKY_PISTON, face));
        site.place(x + face.getModX(), ON_FLOOR, z + face.getModZ(), Material.OAK_PLANKS);
        site.place(x + 2 * face.getModX(), ON_FLOOR, z + 2 * face.getModZ(), Material.AIR);
        tally.watch(site.block(x, ON_FLOOR, z), name);
    }

    /** The extensions are counted by the {@link PistonTally} the layout reports. */
    @Override
    public Map<String, Long> totals() {
        return Map.of();
    }
}
