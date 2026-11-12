package net.kindlingmc.preview;

import java.util.BitSet;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;

/**
 * Turns a preview into what would be sent: the default block below the band, so that a cliff reaching below a chunk's
 * band shows rock instead of a hole. Whole sections below the band become single-value sections. A whole column gets
 * the default block in place of every block nobody can see ({@link #hideEnclosed}).
 */
final class Skin {
    /**
     * Blocks a saved chunk's column keeps below its own surface: what is visible from the sky plus a few blocks, enough
     * for cliff faces and shore lines. Everything deeper in the column becomes the default block.
     */
    static final int KEPT_BELOW_SURFACE = 8;

    private static final int COLUMNS = 16 * 16;
    private static final int SECTION_CELLS = 16 * COLUMNS;
    private static final int SECTION_SHIFT = 12;

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

    /**
     * Turns every block that is hidden on all six sides into the default block, so that a whole column shows what can
     * be seen, not the ores and rooms inside the rock. A side is hidden by a block that renders as a solid full cube;
     * beyond the chunk's edges and the world's top and bottom counts as hidden, so a block seen only from a
     * neighbouring chunk becomes the default block, too. Decided on the blocks as given, before any is changed.
     */
    static void hideEnclosed(ProtoChunk chunk, BlockState block) {
        LevelChunkSection[] sections = chunk.getSections();
        BitSet enclosed = new BitSet(sections.length * SECTION_CELLS);
        for (int index = 0; index < sections.length; index++) {
            if (!sections[index].hasOnlyAir()) {
                markEnclosed(sections, index, block, enclosed);
            }
        }
        for (int cell = enclosed.nextSetBit(0); cell >= 0; cell = enclosed.nextSetBit(cell + 1)) {
            sections[cell >> SECTION_SHIFT].setBlockState(cell & 15, cell >> 8 & 15, cell >> 4 & 15, block, false);
        }
    }

    /** Cells are numbered y << 8 | z << 4 | x with y counted from the bottom of the world. */
    private static void markEnclosed(LevelChunkSection[] sections, int sectionIndex, BlockState block, BitSet enclosed) {
        LevelChunkSection section = sections[sectionIndex];
        for (int y = 0; y < 16; y++) {
            int worldY = sectionIndex << 4 | y;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (!state.isAir() && state != block && isHiddenOnAllSides(sections, x, worldY, z)) {
                        enclosed.set(worldY << 8 | z << 4 | x);
                    }
                }
            }
        }
    }

    private static boolean isHiddenOnAllSides(LevelChunkSection[] sections, int x, int y, int z) {
        return hides(sections, x - 1, y, z) && hides(sections, x + 1, y, z)
            && hides(sections, x, y, z - 1) && hides(sections, x, y, z + 1)
            && hides(sections, x, y - 1, z) && hides(sections, x, y + 1, z);
    }

    /** Whether the block at a position hides its neighbours' faces; outside the chunk, it does. */
    private static boolean hides(LevelChunkSection[] sections, int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y >= sections.length << 4) {
            return true;
        }
        return sections[y >> 4].getBlockState(x, y & 15, z).isSolidRender();
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
