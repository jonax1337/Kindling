package net.kindlingmc.bench;

import org.bukkit.Material;
import org.bukkit.block.Block;

/** A redstone block switched on at one step of a clock and off at the next, with block updates like a real clock. */
final class RedstoneSwitch {

    private final Block block;
    private boolean on;

    RedstoneSwitch(Block block) {
        this.block = block;
    }

    void toggle() {
        on = !on;
        block.setType(on ? Material.REDSTONE_BLOCK : Material.AIR, true);
    }
}
