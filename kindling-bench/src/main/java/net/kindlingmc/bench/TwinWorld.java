package net.kindlingmc.bench;

import java.util.Optional;
import java.util.function.Consumer;
import net.kindlingmc.preview.FidelityCounts;
import net.kindlingmc.preview.PreviewFidelity;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.Plugin;
import org.dreeam.leaf.config.modules.misc.SecureSeed;

/**
 * A second overworld with the main world's seed and generator, in which every sampled chunk is decorated in the
 * opposite order to the main world. Two real generations of one chunk differ because vanilla decorates a chunk onto
 * whatever its neighbours' decoration already placed: in 26.3 the {@code FEATURES} step of a chunk needs its
 * neighbours at {@code TERRAIN} only, and writes up to one chunk around itself (see {@code ChunkPyramid}). So the
 * chunk whose decoration runs first wins the blocks on the border between the two: a crown that spills across, a
 * tree spot that is taken.
 *
 * <p>In the main world the sampled chunk alone is brought to {@code FEATURES} first, so it is decorated before any of
 * its neighbours. Here the eight neighbours are brought to {@code FEATURES} first, so it is decorated after all of
 * them. Both are held there by a ticket until the real load takes over. The comparison measures how much the two
 * extreme orders change a chunk, which bounds what the order alone can do; a preview, built without any neighbour
 * decoration, cannot match the real chunk better than that. With {@link Order#SAME} the twin decorates the sampled
 * chunk first as well, which is the control: the two worlds should then match.
 */
final class TwinWorld implements FidelityCounterpart {

    private static final String NAME = "fidelity_twin";
    private static final int FEATURES_TICKET_LEVEL = ChunkLevel.byStatus(ChunkStatus.FEATURES);

    private final Plugin plugin;
    private final Order order;
    private final ServerLevel mainLevel;
    private final World world;
    private final ServerLevel level;

    private TwinWorld(Plugin plugin, Order order, World main, World twin) {
        this.plugin = plugin;
        this.order = order;
        this.mainLevel = ((CraftWorld) main).getHandle();
        this.world = twin;
        this.level = ((CraftWorld) twin).getHandle();
    }

    /**
     * Creates the twin of {@code main} and checks that it generates the same terrain ({@link TwinCheck}).
     *
     * @throws Unavailable if the twin cannot be made the same
     */
    static TwinWorld create(Plugin plugin, World main, Order order) throws Unavailable {
        if (SecureSeed.enabled) {
            throw new Unavailable("secure-seed is on, which derives the decoration from the world's index too, so a twin would decorate differently");
        }
        World twin = new WorldCreator(NAME).copy(main).createWorld();
        if (twin == null) {
            throw new Unavailable("the twin world " + NAME + " could not be created");
        }
        Optional<String> difference = TwinCheck.firstDifference(((CraftWorld) main).getHandle(), ((CraftWorld) twin).getHandle());
        if (difference.isPresent()) {
            throw new Unavailable("the twin world is not the same as the main world: " + difference.get());
        }
        return new TwinWorld(plugin, order, main, twin);
    }

    /** The sampled chunk of the main world to {@code FEATURES} alone, before any neighbour is decorated. */
    @Override
    public void prepare(int chunkX, int chunkZ) {
        holdAtFeatures(mainLevel, new ChunkPos(chunkX, chunkZ));
    }

    /** The chunk or its neighbours (see {@link Order}) to {@code FEATURES} and held there, then the chunk to full status through a plugin ticket. */
    @Override
    public void load(int chunkX, int chunkZ) {
        switch (order) {
            case OPPOSITE -> forEachNeighbour(chunkX, chunkZ, neighbour -> holdAtFeatures(level, neighbour));
            case SAME -> holdAtFeatures(level, new ChunkPos(chunkX, chunkZ));
        }
        world.addPluginChunkTicket(chunkX, chunkZ, plugin);
        world.getChunkAt(chunkX, chunkZ);
    }

    /** Settled when the chunk and its neighbours are at full status, its light is correct and the twin's light engine has nothing queued. */
    @Override
    public boolean isSettled(int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        return Neighbourhood.isFull(level, chunkX, chunkZ) && chunk != null && chunk.isLightCorrect() && !level.getLightEngine().hasLightWork();
    }

    @Override
    public Optional<FidelityCounts> compare(ServerLevel main, LevelChunk real) {
        ChunkPos pos = real.getPos();
        LevelChunk twinChunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        if (twinChunk == null) {
            throw new IllegalStateException("the twin chunk " + pos + " is not loaded");
        }
        return Optional.of(PreviewFidelity.compareRealChunks(main, real, level, twinChunk));
    }

    @Override
    public void release(int chunkX, int chunkZ) {
        mainLevel.getChunkSource().removeTicketAtLevel(TicketType.CHUNK_LOAD, new ChunkPos(chunkX, chunkZ), FEATURES_TICKET_LEVEL);
        world.removePluginChunkTicket(chunkX, chunkZ, plugin);
        switch (order) {
            case OPPOSITE -> forEachNeighbour(chunkX, chunkZ, neighbour ->
                level.getChunkSource().removeTicketAtLevel(TicketType.CHUNK_LOAD, neighbour, FEATURES_TICKET_LEVEL));
            case SAME -> level.getChunkSource().removeTicketAtLevel(TicketType.CHUNK_LOAD, new ChunkPos(chunkX, chunkZ), FEATURES_TICKET_LEVEL);
        }
    }

    private static void holdAtFeatures(ServerLevel level, ChunkPos pos) {
        level.getChunkSource().addTicketAtLevel(TicketType.CHUNK_LOAD, pos, FEATURES_TICKET_LEVEL);
        level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FEATURES, true);
    }

    private static void forEachNeighbour(int chunkX, int chunkZ, Consumer<ChunkPos> action) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    action.accept(new ChunkPos(chunkX + dx, chunkZ + dz));
                }
            }
        }
    }

    /** When the twin decorates the sampled chunk, relative to its eight neighbours. */
    enum Order {
        /** After all of them, the opposite of the main world. */
        OPPOSITE,
        /** Before all of them, as the main world does. */
        SAME
    }

    /** The twin world cannot be made, or is not the same as the main world; the message says why. */
    static final class Unavailable extends Exception {
        Unavailable(String reason) {
            super(reason);
        }
    }
}
