package net.kindlingmc.preview;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;

/**
 * Turns a preview into what would be sent: the default block below the band, so that a cliff reaching below a chunk's
 * band shows rock instead of a hole. Whole sections below the band become single-value sections.
 */
final class Skin {
    /**
     * Blocks a saved chunk's column keeps below its own surface: what is visible from the sky plus a few blocks, enough
     * for cliff faces and shore lines. Everything deeper in the column becomes the default block.
     */
    static final int KEPT_BELOW_SURFACE = 8;

    private static final int COLUMNS = 16 * 16;

    private Skin() {
    }

    /**
     * Skins a chunk that is already built, column by column: each column keeps its blocks from
     * {@link #KEPT_BELOW_SURFACE} below its surface upward and gets the default block below that. The surface is the
     * highest block of the heightmaps' "blocks motion" kind that is no leaves and no log, so tree crowns and trunks are
     * skipped, and water and plants do not count.
     *
     * @return the band from the lowest kept block of any column to the top of the world
     */
    static Band skinBelowSurfaces(ProtoChunk chunk, BlockState block, PalettedContainerFactory factory) {
        int[] keptFromY = new int[COLUMNS];
        int lowestKeptY = chunk.getMaxY();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int keptFrom = Math.max(chunk.getMinY(), surfaceY(chunk, x, z) - KEPT_BELOW_SURFACE);
                keptFromY[columnIndex(x, z)] = keptFrom;
                lowestKeptY = Math.min(lowestKeptY, keptFrom);
            }
        }
        Band band = new Band(lowestKeptY, chunk.getMaxY());
        fillBelowBand(chunk, band, block, factory);
        fillColumnsBelowKept(chunk, band, keptFromY, block);
        return band;
    }

    static void fillBelowBand(ProtoChunk chunk, Band band, BlockState block, PalettedContainerFactory factory) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            int sectionY = chunk.getSectionYFromSectionIndex(index);
            if (sectionY < band.minSectionY()) {
                sections[index] = new LevelChunkSection(new PalettedContainer<>(block, factory.blockStatesStrategy(), null), factory.createForBiomes());
            } else if (sectionY == band.minSectionY()) {
                fillBelow(sections[index], band.minY() & 15, block);
            }
        }
    }

    /** The y of the column's highest ground block, or one below the world when it has none. */
    private static int surfaceY(ProtoChunk chunk, int x, int z) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = sections.length - 1; index >= 0; index--) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir()) {
                continue;
            }
            for (int y = 15; y >= 0; y--) {
                if (isGround(section.getBlockState(x, y, z))) {
                    return chunk.getSectionYFromSectionIndex(index) * 16 + y;
                }
            }
        }
        return chunk.getMinY() - 1;
    }

    private static boolean isGround(BlockState state) {
        return state.is(BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP) && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS);
    }

    private static void fillColumnsBelowKept(ProtoChunk chunk, Band band, int[] keptFromY, BlockState block) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                for (int y = band.minY(); y < keptFromY[columnIndex(x, z)]; y++) {
                    chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x, y & 15, z, block, false);
                }
            }
        }
    }

    private static int columnIndex(int x, int z) {
        return z << 4 | x;
    }

    private static void fillBelow(LevelChunkSection section, int topExclusive, BlockState block) {
        for (int y = 0; y < topExclusive; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    section.setBlockState(x, y, z, block, false);
                }
            }
        }
    }
}
