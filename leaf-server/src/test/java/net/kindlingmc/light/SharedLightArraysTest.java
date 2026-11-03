package net.kindlingmc.light;

import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedLightArraysTest {

    private static final int LEVELS = 4096;

    @Test
    void uniformArraysShareOneInstancePerValue() {
        final byte[] full = SharedLightArrays.shareIfUniform(filled(-1));
        final byte[] dark = SharedLightArrays.shareIfUniform(filled(0));

        assertSame(full, SharedLightArrays.shareIfUniform(filled(-1)));
        assertSame(dark, SharedLightArrays.shareIfUniform(filled(0)));
        assertNotSame(full, dark);
        assertTrue(SharedLightArrays.isShared(full));
        assertTrue(SharedLightArrays.isShared(dark));
    }

    @Test
    void anArrayWithOneDifferentLevelIsKept() {
        final byte[] nearlyFull = filled(-1);
        nearlyFull[SharedLightArrays.SIZE - 1] = 0x0F;
        final byte[] otherUniformValue = filled(0x77);

        assertSame(nearlyFull, SharedLightArrays.shareIfUniform(nearlyFull));
        assertSame(otherUniformValue, SharedLightArrays.shareIfUniform(otherUniformValue));
        assertFalse(SharedLightArrays.isShared(nearlyFull));
    }

    @Test
    void aFullSectionReadsFifteenEverywhereAfterPublishing() {
        final SWMRNibbleArray section = publishedFullSection();

        for (int index = 0; index < LEVELS; index++) {
            assertEquals(15, section.getVisible(index));
            assertEquals(15, section.getUpdating(index));
        }
    }

    @Test
    void writingOneSectionLeavesTheOthersAndTheSharedArrayUntouched() {
        final SWMRNibbleArray written = publishedFullSection();
        final SWMRNibbleArray untouched = publishedFullSection();

        written.set(5, 3);

        assertEquals(15, written.getVisible(5), "a write is visible only after updateVisible");
        assertEquals(3, written.getUpdating(5));
        written.updateVisible();
        assertEquals(3, written.getVisible(5));
        assertEquals(15, written.getVisible(4));
        assertArrayEquals(filled(-1), untouched.toVanillaNibble().getData());
    }

    @Test
    void arraysReusedFromThePoolNeverAliasTheSharedArray() {
        final SWMRNibbleArray[] sections = new SWMRNibbleArray[32];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = publishedFullSection();
        }
        for (int i = 0; i < sections.length; i++) {
            sections[i].set(i, i % 15);
            sections[i].updateVisible();
            sections[i].setFull();
            sections[i].set(LEVELS - 1 - i, 1);
            sections[i].updateVisible();
        }

        assertArrayEquals(filled(-1), publishedFullSection().toVanillaNibble().getData());
        for (int i = 0; i < sections.length; i++) {
            assertEquals(1, sections[i].getVisible(LEVELS - 1 - i));
        }
    }

    @Test
    void aLoadedUniformSectionKeepsItsLevels() {
        final SWMRNibbleArray loaded = new SWMRNibbleArray(filled(-1), initialisedState());

        loaded.set(0, 7);
        loaded.updateVisible();

        assertEquals(7, loaded.getVisible(0));
        assertArrayEquals(filled(-1), publishedFullSection().toVanillaNibble().getData());
    }

    @Test
    void aDarkSectionStillSavesWithoutData() {
        final SWMRNibbleArray dark = new SWMRNibbleArray(null, true);
        dark.setNonNull();
        dark.setZero();
        dark.updateVisible();

        final SWMRNibbleArray.SaveState saved = dark.getSaveState();

        assertEquals(0, dark.getVisible(123));
        assertEquals(null, saved.data, "an all-dark section is saved as uninitialised, as before");
    }

    @Test
    void aSavedFullSectionIsACopy() {
        final SWMRNibbleArray full = publishedFullSection();

        final byte[] saved = full.getSaveState().data;
        saved[0] = 0;

        assertFalse(SharedLightArrays.isShared(saved));
        assertEquals(15, publishedFullSection().getVisible(0));
    }

    @Test
    void extrudingFromASharedSectionWritesOnlyTheLowerOne() {
        final SWMRNibbleArray upper = publishedFullSection();
        final SWMRNibbleArray lower = new SWMRNibbleArray(null, true);
        lower.setNonNull();

        lower.extrudeLower(upper);
        lower.updateVisible();
        lower.set(0, 2);
        lower.updateVisible();

        assertEquals(2, lower.getVisible(0));
        assertEquals(15, lower.getVisible(1));
        assertArrayEquals(filled(-1), upper.toVanillaNibble().getData());
    }

    @Test
    void aStateOnlyChangeKeepsTheSharedLevels() {
        final SWMRNibbleArray section = publishedFullSection();

        section.setHidden();
        section.updateVisible();
        section.setNonNull();
        section.updateVisible();

        assertArrayEquals(filled(-1), section.toVanillaNibble().getData());
    }

    private static SWMRNibbleArray publishedFullSection() {
        final SWMRNibbleArray section = new SWMRNibbleArray(null, true);
        section.setNonNull();
        section.setFull();
        section.updateVisible();
        return section;
    }

    /** The state SaveUtil passes for a section that was saved with light data (INIT_STATE_INIT, private in Starlight). */
    private static int initialisedState() {
        return 2;
    }

    private static byte[] filled(final int value) {
        final byte[] nibbles = new byte[SharedLightArrays.SIZE];
        Arrays.fill(nibbles, (byte) value);
        return nibbles;
    }
}
