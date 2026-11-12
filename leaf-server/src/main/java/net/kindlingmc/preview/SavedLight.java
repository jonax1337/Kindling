package net.kindlingmc.preview;

import java.util.List;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.SerializableChunkData.SectionData;

/**
 * The light a saved chunk is previewed with: its saved light, as the chunk would send it (the light engine's
 * per-section states decide between "no data", "all dark" and the stored array), or the estimate of {@link SkyLight}
 * when the saved light is not correct or unreadable. Sections below the skin are sent dark, as seed previews do:
 * they are filled with the default block, so their light never shows.
 */
final class SavedLight {
    private static final int LIGHT_ARRAY_BYTES = 2048;
    private static final byte[] DARK = new byte[LIGHT_ARRAY_BYTES];

    /** The light engine's per-section states, as the chunk data stores them (SWMRNibbleArray). */
    private static final int ABSENT = -1;
    private static final int NULL = 0;
    private static final int UNINITIALISED = 1;
    private static final int INITIALISED = 2;
    private static final int HIDDEN = 3;

    private SavedLight() {
    }

    /**
     * @param sections the preview's sections, already skinned (the estimate reads them)
     * @param saved the chunk's sections, all within one section of the world's range (see {@link SavedChunkCheck})
     * @param minSectionY the lowest block section of the world; light sections run one below it to one above the highest
     * @param skinMinSectionY the lowest section the skin keeps
     */
    static SkyLight.Light of(boolean lightCorrect, List<SectionData> saved, LevelChunkSection[] sections, int minSectionY, int skinMinSectionY) {
        if (!lightCorrect || !readable(saved)) {
            return SkyLight.fromAbove(sections);
        }
        byte[][] sky = new byte[sections.length + 2][];
        byte[][] block = new byte[sections.length + 2][];
        int lowestLightSectionY = minSectionY - 1;
        for (int index = 0; index < skinMinSectionY - lowestLightSectionY; index++) {
            sky[index] = DARK;
        }
        for (SectionData section : saved) {
            if (section.y() >= skinMinSectionY) {
                int index = section.y() - lowestLightSectionY;
                sky[index] = layer(section.skyLight(), section.starlight$getSkyLightState());
                block[index] = layer(section.blockLight(), section.starlight$getBlockLightState());
            }
        }
        return new SkyLight.Light(SkyLight.Layers.of(sky), SkyLight.Layers.of(block));
    }

    private static boolean readable(List<SectionData> saved) {
        for (SectionData section : saved) {
            if (!readable(section.skyLight(), section.starlight$getSkyLightState())
                || !readable(section.blockLight(), section.starlight$getBlockLightState())) {
                return false;
            }
        }
        return true;
    }

    private static boolean readable(DataLayer data, int state) {
        return switch (state) {
            case ABSENT, NULL, UNINITIALISED, HIDDEN -> true;
            case INITIALISED -> data != null && data.getData().length == LIGHT_ARRAY_BYTES;
            default -> false;
        };
    }

    /** The array to send for a saved section; null sends nothing. Call only for readable states. */
    private static byte[] layer(DataLayer data, int state) {
        return switch (state) {
            case UNINITIALISED -> DARK;
            case INITIALISED -> data.getData();
            default -> null;
        };
    }
}
