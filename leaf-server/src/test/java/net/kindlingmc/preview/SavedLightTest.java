package net.kindlingmc.preview;

import java.util.Arrays;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.SerializableChunkData.SectionData;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which light a saved chunk is previewed with: its saved light when that is correct and readable, else the estimate. */
@Normal
class SavedLightTest {
    private static final long SEED = 5L;
    private static final int MIN_SECTION_Y = -4;
    private static final int SKIN_MIN_SECTION_Y = 0;
    private static final int SECTION_Y = 10;
    private static final int LIGHT_SECTION = SECTION_Y - (MIN_SECTION_Y - 1);
    private static final int LIGHT_ARRAY_BYTES = 2048;
    private static final int ABSENT = -1;
    private static final int NULL = 0;
    private static final int UNINITIALISED = 1;
    private static final int INITIALISED = 2;

    private final LevelChunkSection[] sections = new PreviewTestWorld(SEED).emptyChunk(new ChunkPos(0, 0)).getSections();

    @Test
    void lightThatIsNotCorrectIsEstimatedFromAbove() {
        SkyLight.Light estimate = SkyLight.fromAbove(this.sections);
        SkyLight.Light light = lightOf(false, saved(INITIALISED, filled(7), INITIALISED, filled(3)));
        assertEquals(estimate.sky().present(), light.sky().present());
        assertEquals(estimate.sky().empty(), light.sky().empty());
        assertEquals(estimate.block().present(), light.block().present());
    }

    @Test
    void correctSavedLightIsSentAsSaved() {
        DataLayer sky = filled(7);
        DataLayer block = filled(3);
        SkyLight.Light light = lightOf(true, saved(INITIALISED, sky, INITIALISED, block));
        assertTrue(light.sky().present().get(LIGHT_SECTION));
        assertSame(sky.getData(), light.sky().arrays().get(0));
        assertTrue(light.block().present().get(LIGHT_SECTION));
        assertSame(block.getData(), light.block().arrays().get(0));
    }

    @Test
    void sectionsBelowTheSkinAreDarkAndCarryNoBlockLight() {
        SkyLight.Light light = lightOf(true, saved(INITIALISED, filled(7), INITIALISED, filled(3)));
        int belowSkin = SKIN_MIN_SECTION_Y - (MIN_SECTION_Y - 1);
        for (int index = 0; index < belowSkin; index++) {
            assertTrue(light.sky().empty().get(index), "light section " + index);
        }
        assertFalse(light.sky().empty().get(belowSkin));
        assertTrue(light.block().empty().isEmpty());
    }

    @Test
    void anUninitialisedSectionIsSentDarkAndANullOrAbsentOneNotAtAll() {
        SkyLight.Light dark = lightOf(true, saved(UNINITIALISED, null, UNINITIALISED, null));
        assertTrue(dark.sky().empty().get(LIGHT_SECTION));
        assertTrue(dark.block().empty().get(LIGHT_SECTION));
        SkyLight.Light none = lightOf(true, saved(NULL, null, ABSENT, null));
        assertFalse(none.sky().empty().get(LIGHT_SECTION) || none.sky().present().get(LIGHT_SECTION));
        assertFalse(none.block().empty().get(LIGHT_SECTION) || none.block().present().get(LIGHT_SECTION));
    }

    @Test
    void anInitialisedSectionWithoutDataFallsBackToTheEstimate() {
        SkyLight.Light estimate = SkyLight.fromAbove(this.sections);
        SkyLight.Light light = lightOf(true, saved(INITIALISED, null, NULL, null));
        assertEquals(estimate.sky().present(), light.sky().present());
        assertEquals(estimate.sky().empty(), light.sky().empty());
    }

    private SkyLight.Light lightOf(boolean lightCorrect, SectionData saved) {
        return SavedLight.of(lightCorrect, List.of(saved), this.sections, MIN_SECTION_Y, SKIN_MIN_SECTION_Y);
    }

    private static SectionData saved(int skyState, DataLayer sky, int blockState, DataLayer block) {
        SectionData section = new SectionData(SECTION_Y, null, block, sky);
        section.starlight$setSkyLightState(skyState);
        section.starlight$setBlockLightState(blockState);
        return section;
    }

    private static DataLayer filled(int level) {
        byte[] data = new byte[LIGHT_ARRAY_BYTES];
        Arrays.fill(data, (byte) (level | level << 4));
        return new DataLayer(data);
    }
}
