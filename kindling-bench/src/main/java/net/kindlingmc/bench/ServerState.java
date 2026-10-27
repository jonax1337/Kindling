package net.kindlingmc.bench;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;

/** What the server holds at a moment of a run: loaded chunks, entities and memory. */
final class ServerState {

    static final int BYTES_PER_MB = 1 << 20;

    private ServerState() {
    }

    static int loadedChunks() {
        return Bukkit.getWorlds().stream().mapToInt(world -> world.getLoadedChunks().length).sum();
    }

    static int entities() {
        return Bukkit.getWorlds().stream().mapToInt(world -> world.getEntities().size()).sum();
    }

    /** How many entities of each type the worlds hold, by type key. */
    static Map<String, Long> entityTypes() {
        return Bukkit.getWorlds().stream().flatMap(world -> world.getEntities().stream()).collect(
                Collectors.groupingBy(entity -> entity.getType().key().asString(), TreeMap::new, Collectors.counting()));
    }

    /**
     * The heap and the garbage collector at the end of a loaded phase; collects twice to read off the live heap.
     * {@code gc_count} and {@code gc_millis} add up every collector bean, including the cycles of ZGC and Shenandoah, which
     * overlap the pauses; {@code gc_pause_millis} counts the pauses only.
     */
    static Map<String, Object> memoryUnderLoad() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("heap_used_before_gc_mb", memory.getHeapMemoryUsage().getUsed() / BYTES_PER_MB);
        figures.put("gc_count", gcTotal(GarbageCollectorMXBean::getCollectionCount));
        figures.put("gc_millis", gcTotal(GarbageCollectorMXBean::getCollectionTime));
        figures.put("gc_pause_millis", GcTotals.ofPauses().millis());
        System.gc();
        System.gc();
        figures.put("heap_used_after_gc_mb", memory.getHeapMemoryUsage().getUsed() / BYTES_PER_MB);
        figures.put("heap_committed_mb", memory.getHeapMemoryUsage().getCommitted() / BYTES_PER_MB);
        figures.put("non_heap_used_mb", memory.getNonHeapMemoryUsage().getUsed() / BYTES_PER_MB);
        return figures;
    }

    private static long gcTotal(ToLongFunction<GarbageCollectorMXBean> figure) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(figure).filter(n -> n > 0).sum();
    }
}
