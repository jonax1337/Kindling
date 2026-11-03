package net.kindlingmc.preview;

import java.util.EnumSet;
import java.util.Set;
import net.kindlingmc.world.FreezableWater;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Ice and snow on a finished preview, as vanilla's freeze_top_layer feature ({@code SnowAndFreezeFeature}) lays them
 * after the trees: per column, ice on the water below the top and a snow layer on the top, where the biome is cold
 * enough. The top is the MOTION_BLOCKING height, so snow lands on leaves, too.
 *
 * <p>Deviations from vanilla, none of which a player sees at a glance:
 * <ul>
 * <li>The biome is looked up with the biome manager's fuzzy zoom, as vanilla does, but its neighbouring quarts come
 * from the chunk itself (the nearest quart inside it), as a preview has no neighbour chunks. Columns within a few
 * blocks of a chunk edge may pick another biome than in the full chunk, at biome borders only.
 * <li>The biome is read at the height of the terrain surface, not at the top: the preview only has biomes inside its
 * band, and the biomes of the overworld hardly change over the height of a tree.
 * <li>Snow on blue ice (a Purpur setting) is not looked at: previews have no blue ice.
 * </ul>
 *
 * <p>The block light that vanilla's checks read is 0 at this stage: lighting has not run when features are placed, and
 * {@code WorldGenRegion.getBrightness} answers 0 for a chunk whose light is not correct yet. So the "less than 10" checks
 * always hold and are left out.
 */
final class PreviewSnow {
    private static final Set<Heightmap.Types> MOTION_BLOCKING = EnumSet.of(Heightmap.Types.MOTION_BLOCKING);

    private PreviewSnow() {
    }

    /** Freezes the water and snows the top of every column of the chunk; the chunk's MOTION_BLOCKING heightmap is primed. */
    static void freeze(ProtoChunk chunk, long worldSeed, int seaLevel) {
        Heightmap.primeHeightmaps(chunk, MOTION_BLOCKING);
        new Frost(chunk, worldSeed, seaLevel).coverChunk();
    }

    /** The state of one freezing pass over one chunk; coordinates x and z are inside the chunk (0 to 15). */
    private static final class Frost {
        private final ProtoChunk chunk;
        private final BiomeManager biomes;
        private final int seaLevel;
        private final int minBlockX;
        private final int minBlockZ;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Frost(ProtoChunk chunk, long worldSeed, int seaLevel) {
            this.chunk = chunk;
            this.biomes = new BiomeManager(this::biomeInsideChunk, BiomeManager.obfuscateSeed(worldSeed));
            this.seaLevel = seaLevel;
            this.minBlockX = chunk.getPos().getMinBlockX();
            this.minBlockZ = chunk.getPos().getMinBlockZ();
        }

        void coverChunk() {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    coverColumn(x, z);
                }
            }
        }

        private void coverColumn(int x, int z) {
            int topY = this.chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1;
            int belowY = topY - 1;
            Biome biome = biomeAt(x, this.chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z), z);
            if (shouldFreeze(biome, x, belowY, z)) {
                setBlock(x, belowY, z, Blocks.ICE.defaultBlockState());
            }
            if (shouldSnow(biome, x, topY, z)) {
                setBlock(x, topY, z, Blocks.SNOW.defaultBlockState());
                markSnowy(x, belowY, z);
            }
        }

        private Biome biomeAt(int x, int y, int z) {
            return this.biomes.getBiome(this.minBlockX + x, y, this.minBlockZ + z).value();
        }

        /** Biome.shouldFreeze without the neighbour check: a cold position inside the world holding a water block. */
        private boolean shouldFreeze(Biome biome, int x, int y, int z) {
            return isInsideWorld(y)
                && !biome.warmEnoughToRain(worldPos(x, y, z), this.seaLevel)
                && FreezableWater.isFreezableWater(blockAt(x, y, z));
        }

        /** Biome.shouldSnow: snowfall at the position, air (or snow) there, and a block below that can carry a snow layer. */
        private boolean shouldSnow(Biome biome, int x, int y, int z) {
            if (!isInsideWorld(y) || biome.getPrecipitationAt(worldPos(x, y, z), this.seaLevel) != Biome.Precipitation.SNOW) {
                return false;
            }
            BlockState state = blockAt(x, y, z);
            return (state.isAir() || state.is(Blocks.SNOW)) && canCarrySnowLayer(blockAt(x, y - 1, z));
        }

        /** SnowLayerBlock.canSurvive: the block below must not refuse snow and must offer a full top face, or be a full snow block. */
        private static boolean canCarrySnowLayer(BlockState below) {
            if (below.is(BlockTags.CANNOT_SUPPORT_SNOW_LAYER)) {
                return false;
            }
            return below.is(BlockTags.SUPPORT_OVERRIDE_SNOW_LAYER)
                || Block.isFaceFull(below.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), Direction.UP)
                || below.is(Blocks.SNOW) && below.getValue(SnowLayerBlock.LAYERS) == SnowLayerBlock.MAX_HEIGHT;
        }

        private void markSnowy(int x, int y, int z) {
            BlockState below = blockAt(x, y, z);
            if (below.hasProperty(SnowyBlock.SNOWY)) {
                setBlock(x, y, z, below.setValue(SnowyBlock.SNOWY, true));
            }
        }

        private BlockPos worldPos(int x, int y, int z) {
            return this.pos.set(this.minBlockX + x, y, this.minBlockZ + z);
        }

        /** The chunk's own biome at a quart; quarts outside the chunk's columns read the nearest quart inside. */
        private Holder<Biome> biomeInsideChunk(int quartX, int quartY, int quartZ) {
            int minQuartX = this.minBlockX >> 2;
            int minQuartZ = this.minBlockZ >> 2;
            return this.chunk.getNoiseBiome(Mth.clamp(quartX, minQuartX, minQuartX + 3), quartY, Mth.clamp(quartZ, minQuartZ, minQuartZ + 3));
        }

        private boolean isInsideWorld(int y) {
            return y >= this.chunk.getMinY() && y <= this.chunk.getMaxY();
        }

        /** The block at a column and height of the chunk; air above and below the world. */
        private BlockState blockAt(int x, int y, int z) {
            if (!isInsideWorld(y)) {
                return Blocks.AIR.defaultBlockState();
            }
            return this.chunk.getSection(this.chunk.getSectionIndex(y)).getBlockState(x, y & 15, z);
        }

        private void setBlock(int x, int y, int z, BlockState state) {
            this.chunk.getSection(this.chunk.getSectionIndex(y)).setBlockState(x, y & 15, z, state, false);
        }
    }
}
