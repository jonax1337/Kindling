package net.kindlingmc.preview;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The eight chunks around a preview chunk, as the features decorating it see them. A preview has no neighbours, but a
 * feature's random draws often depend on what it finds there (a seagrass spread asks whether the block is water, a
 * leaf litter decorator whether it is air), and one draw more or less moves every later tree and plant of the
 * feature. So the neighbours are stood in for by the chunk itself:
 * <ul>
 * <li>Below the worldgen surface of the nearest column of the chunk they hold that column's blocks; between its
 * ocean floor and its surface (water) only the column's fluid; above, air. The worldgen heightmaps stay as the terrain
 * left them while features are placed, so the stand-in shows no plant or tree of the chunk itself.
 * <li>Heights are the nearest column's worldgen heights (OCEAN_FLOOR_WG for the ocean floor types, WORLD_SURFACE_WG
 * for the others).
 * <li>What features write into them is kept, so that a feature finds its own blocks again, and thrown away with the
 * region. Writes further away than one chunk fail, as WorldGenRegion's write radius of one chunk makes them fail.
 * <li>A block they hold that has a block entity (a bee nest a tree decorator placed) gives back a throwaway one on
 * request, as the placeholder WorldGenRegion makes in the real neighbour, so that the decorator's random draws happen.
 * </ul>
 * Not thread-safe: one instance serves one decoration on one thread.
 */
final class Surroundings {
    private static final int WRITE_RADIUS = 1;

    private final ProtoChunk chunk;
    private final int chunkX;
    private final int chunkZ;
    private final int minBlockX;
    private final int minBlockZ;
    private final Long2ObjectOpenHashMap<BlockState> written = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<BlockEntity> blockEntities = new Long2ObjectOpenHashMap<>();

    Surroundings(ProtoChunk chunk) {
        this.chunk = chunk;
        this.chunkX = chunk.getPos().x();
        this.chunkZ = chunk.getPos().z();
        this.minBlockX = chunk.getPos().getMinBlockX();
        this.minBlockZ = chunk.getPos().getMinBlockZ();
    }

    BlockState blockState(BlockPos pos) {
        BlockState state = this.written.get(pos.asLong());
        return state != null ? state : standInTerrain(pos);
    }

    /** The height plus one, as WorldGenRegion answers. */
    int height(Heightmap.Types type, int x, int z) {
        Heightmap.Types worldgen = isOceanFloor(type) ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG;
        return this.chunk.getHeight(worldgen, nearestColumnX(x), nearestColumnZ(z)) + 1;
    }

    boolean canWrite(BlockPos pos) {
        return Math.abs(SectionPos.blockToSectionCoord(pos.getX()) - this.chunkX) <= WRITE_RADIUS
            && Math.abs(SectionPos.blockToSectionCoord(pos.getZ()) - this.chunkZ) <= WRITE_RADIUS
            && !this.chunk.isOutsideBuildHeight(pos.getY());
    }

    /** Keeps the block for later reads; false where WorldGenRegion would refuse the write. */
    boolean write(BlockPos pos, BlockState state) {
        if (!canWrite(pos)) {
            return false;
        }
        long key = pos.asLong();
        if (blockState(pos).getBlock() != state.getBlock()) {
            this.blockEntities.remove(key);
        }
        this.written.put(key, state);
        return true;
    }

    /** A throwaway block entity for the block held at the position, the same one on every request; null if the block has none. */
    @Nullable BlockEntity blockEntity(BlockPos pos) {
        BlockState state = blockState(pos);
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }
        long key = pos.asLong();
        BlockEntity blockEntity = this.blockEntities.get(key);
        if (blockEntity == null) {
            blockEntity = entityBlock.newBlockEntity(pos.immutable(), state);
            if (blockEntity != null) {
                this.blockEntities.put(key, blockEntity);
            }
        }
        return blockEntity;
    }

    private BlockState standInTerrain(BlockPos pos) {
        int x = nearestColumnX(pos.getX());
        int z = nearestColumnZ(pos.getZ());
        int y = pos.getY();
        if (y > this.chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z)) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState column = this.chunk.getBlockState(this.minBlockX + x, y, this.minBlockZ + z);
        if (y > this.chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z)) {
            return column.getFluidState().createLegacyBlock();
        }
        return column;
    }

    private static boolean isOceanFloor(Heightmap.Types type) {
        return type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG;
    }

    private int nearestColumnX(int blockX) {
        return Mth.clamp(blockX - this.minBlockX, 0, 15);
    }

    private int nearestColumnZ(int blockZ) {
        return Mth.clamp(blockZ - this.minBlockZ, 0, 15);
    }
}
