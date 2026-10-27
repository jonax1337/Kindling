package net.kindlingmc.bench;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Orientation;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Crafter;
import org.bukkit.block.data.type.RedstoneWire;

/** Block states the contraptions place that a plain {@link Material} does not describe. */
final class BlockStates {

    private BlockStates() {
    }

    /**
     * A block turned to {@code face}, as its {@code facing} property names it: for a repeater or comparator the side it
     * reads from, for a wall torch the side away from its wall.
     */
    static BlockData facing(Material material, BlockFace face) {
        Directional data = (Directional) material.createBlockData();
        data.setFacing(face);
        return data;
    }

    /** A crafter turned to {@code orientation}: first the side it puts out what it crafts, then its top. */
    static BlockData crafter(Orientation orientation) {
        Crafter crafter = (Crafter) Material.CRAFTER.createBlockData();
        crafter.setOrientation(orientation);
        return crafter;
    }

    /** Redstone dust shaped towards the given sides, the shape vanilla gives it next to its neighbours. */
    static BlockData wire(BlockFace... connected) {
        RedstoneWire wire = (RedstoneWire) Material.REDSTONE_WIRE.createBlockData();
        for (BlockFace face : connected) {
            wire.setFace(face, RedstoneWire.Connection.SIDE);
        }
        return wire;
    }
}
