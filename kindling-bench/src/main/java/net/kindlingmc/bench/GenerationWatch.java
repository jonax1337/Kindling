package net.kindlingmc.bench;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/** Remembers when the server last generated a chunk, so a run can wait until world generation has calmed down. */
final class GenerationWatch implements Listener {

    private int lastNewChunkTick = Bukkit.getCurrentTick();

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) {
            lastNewChunkTick = Bukkit.getCurrentTick();
        }
    }

    int quietTicks() {
        return Bukkit.getCurrentTick() - lastNewChunkTick;
    }
}
