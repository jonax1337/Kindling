package net.kindlingmc.preview;

import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SerializableChunkData.SectionData;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static net.kindlingmc.preview.SavedChunkCheck.Rejection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a saved chunk is previewed as saved and when the seed preview is used instead. */
@Normal
class SavedChunkCheckTest {
    private static final int CURRENT_DATA_VERSION = 4000;
    private static final int MIN_SECTION_Y = -4;
    private static final int MAX_SECTION_Y = 19;
    private static final ChunkPos POS = new ChunkPos(3, -7);

    @Test
    void aFullChunkOfTheCurrentVersionIsUsable() {
        assertTrue(SavedChunkCheck.ofTag(tag(CURRENT_DATA_VERSION, "minecraft:full"), CURRENT_DATA_VERSION).isEmpty());
    }

    @Test
    void aChunkWhoseFeaturesAreDoneIsUsableBeforeItIsLit() {
        assertTrue(SavedChunkCheck.ofTag(tag(CURRENT_DATA_VERSION, "minecraft:features"), CURRENT_DATA_VERSION).isEmpty());
    }

    @Test
    void aChunkOfAnotherDataVersionIsRejectedWhetherOlderOrNewer() {
        assertEquals(Optional.of(Rejection.DATA_VERSION), SavedChunkCheck.ofTag(tag(CURRENT_DATA_VERSION - 1, "minecraft:full"), CURRENT_DATA_VERSION));
        assertEquals(Optional.of(Rejection.DATA_VERSION), SavedChunkCheck.ofTag(tag(CURRENT_DATA_VERSION + 1, "minecraft:full"), CURRENT_DATA_VERSION));
    }

    @Test
    void aChunkWithoutADataVersionIsRejected() {
        CompoundTag withoutVersion = new CompoundTag();
        withoutVersion.putString("Status", "minecraft:full");
        assertEquals(Optional.of(Rejection.DATA_VERSION), SavedChunkCheck.ofTag(withoutVersion, CURRENT_DATA_VERSION));
    }

    @Test
    void aChunkBeforeItsFeaturesAreDoneIsRejected() {
        for (String status : List.of("minecraft:empty", "minecraft:noise", "minecraft:surface", "minecraft:carvers")) {
            assertEquals(Optional.of(Rejection.STATUS), SavedChunkCheck.ofTag(tag(CURRENT_DATA_VERSION, status), CURRENT_DATA_VERSION), status);
        }
    }

    @Test
    void aChunkWithoutAStatusIsRejected() {
        CompoundTag withoutStatus = new CompoundTag();
        withoutStatus.putInt("DataVersion", CURRENT_DATA_VERSION);
        assertEquals(Optional.of(Rejection.STATUS), SavedChunkCheck.ofTag(withoutStatus, CURRENT_DATA_VERSION));
    }

    @Test
    void sectionsOfTheWorldAndItsTwoLightSectionsPass() {
        List<SectionData> sections = List.of(section(MIN_SECTION_Y - 1), section(MIN_SECTION_Y), section(MAX_SECTION_Y), section(MAX_SECTION_Y + 1));
        assertTrue(SavedChunkCheck.ofParsed(POS, POS, sections, MIN_SECTION_Y, MAX_SECTION_Y).isEmpty());
    }

    @Test
    void aSectionBeyondTheWorldsHeightIsRejected() {
        assertEquals(Optional.of(Rejection.HEIGHT),
            SavedChunkCheck.ofParsed(POS, POS, List.of(section(MAX_SECTION_Y + 2)), MIN_SECTION_Y, MAX_SECTION_Y));
        assertEquals(Optional.of(Rejection.HEIGHT),
            SavedChunkCheck.ofParsed(POS, POS, List.of(section(MIN_SECTION_Y - 2)), MIN_SECTION_Y, MAX_SECTION_Y));
    }

    @Test
    void dataOfAnotherChunkIsRejected() {
        assertEquals(Optional.of(Rejection.POSITION),
            SavedChunkCheck.ofParsed(POS, new ChunkPos(3, -6), List.of(section(0)), MIN_SECTION_Y, MAX_SECTION_Y));
    }

    private static CompoundTag tag(int dataVersion, String status) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", dataVersion);
        tag.putString("Status", status);
        return tag;
    }

    private static SectionData section(int y) {
        return new SectionData(y, null, null, null);
    }
}
