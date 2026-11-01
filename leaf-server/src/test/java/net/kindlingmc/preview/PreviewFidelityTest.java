package net.kindlingmc.preview;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Reading the preview's estimated sky light back at a block, which is how the real chunk's light is compared with it. */
@Normal
class PreviewFidelityTest {
    private static final int MIN_Y = -64;
    private static final int LIGHT_ARRAY_BYTES = 2048;
    private static final int CELLS_PER_LAYER = 256;

    @Test
    void aSectionWithoutLightDataIsOpenSky() {
        SkyLight.Light light = lightOf(new byte[4][]);

        assertEquals(15, PreviewFidelity.skyLightAt(light, MIN_Y, 3, MIN_Y + 20, 4));
    }

    @Test
    void anAllZeroSectionIsDark() {
        byte[][] sky = new byte[4][];
        sky[2] = new byte[LIGHT_ARRAY_BYTES];

        assertEquals(0, PreviewFidelity.skyLightAt(lightOf(sky), MIN_Y, 3, MIN_Y + 20, 4));
    }

    @Test
    void aCellIsReadFromTheNibbleOfItsIndexInTheArrayOfItsLightSection() {
        byte[][] sky = new byte[4][];
        sky[1] = new byte[LIGHT_ARRAY_BYTES];
        sky[2] = new byte[LIGHT_ARRAY_BYTES];
        sky[1][0] = 1;
        setNibble(sky[2], cellOf(3, 4, 4), 9);
        setNibble(sky[2], cellOf(3, 4, 5), 6);
        SkyLight.Light light = lightOf(sky);

        assertEquals(9, PreviewFidelity.skyLightAt(light, MIN_Y, 3, MIN_Y + 16 + 4, 4));
        assertEquals(6, PreviewFidelity.skyLightAt(light, MIN_Y, 3, MIN_Y + 16 + 4, 5));
        assertEquals(1, PreviewFidelity.skyLightAt(light, MIN_Y, 0, MIN_Y, 0));
    }

    @Test
    void theEstimateOfAGroundedChunkReadsFifteenAboveAndZeroInsideTheGround() {
        ProtoChunk chunk = new PreviewTestWorld(1L).emptyChunk(new ChunkPos(0, 0));
        int groundTopY = 63;
        for (int y = chunk.getMinY(); y <= groundTopY; y++) {
            chunk.getSection(chunk.getSectionIndex(y)).setBlockState(8, y & 15, 8, Blocks.STONE.defaultBlockState(), false);
        }
        SkyLight.Light light = SkyLight.fromAbove(chunk.getSections());

        assertEquals(15, PreviewFidelity.skyLightAt(light, chunk.getMinY(), 8, groundTopY + 1, 8));
        assertEquals(0, PreviewFidelity.skyLightAt(light, chunk.getMinY(), 8, groundTopY, 8));
        assertEquals(15, PreviewFidelity.skyLightAt(light, chunk.getMinY(), 8, chunk.getMaxY(), 8));
    }

    private static SkyLight.Light lightOf(byte[][] sky) {
        return new SkyLight.Light(SkyLight.Layers.of(sky), SkyLight.Layers.of(new byte[sky.length][]));
    }

    private static int cellOf(int x, int yInSection, int z) {
        return yInSection * CELLS_PER_LAYER + z * 16 + x;
    }

    private static void setNibble(byte[] packed, int cell, int level) {
        packed[cell >> 1] = (byte) (packed[cell >> 1] | level << ((cell & 1) * 4));
    }
}
