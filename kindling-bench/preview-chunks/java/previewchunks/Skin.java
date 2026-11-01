package previewchunks;

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
    private Skin() {
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
