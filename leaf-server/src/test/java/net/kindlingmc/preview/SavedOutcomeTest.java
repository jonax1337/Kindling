package net.kindlingmc.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which "no saved preview" reasons let the seed preview be cached: the lasting ones only. */
class SavedOutcomeTest {
    @Test
    void aChunkNotGeneratedFarEnoughYetIsOnlyForNow() {
        assertFalse(SavedOutcome.rejected(SavedChunkCheck.Rejection.STATUS).seedPreviewMayBeCached());
    }

    @Test
    void everyOtherRejectionLasts() {
        for (SavedChunkCheck.Rejection rejection : SavedChunkCheck.Rejection.values()) {
            if (rejection != SavedChunkCheck.Rejection.STATUS) {
                assertTrue(SavedOutcome.rejected(rejection).seedPreviewMayBeCached(), rejection.name());
            }
        }
    }

    @Test
    void postponedAndLastingNothingDifferInCaching() {
        assertFalse(SavedOutcome.NONE_FOR_NOW.seedPreviewMayBeCached());
        assertTrue(SavedOutcome.NONE_LASTING.seedPreviewMayBeCached());
        assertTrue(SavedOutcome.NONE_FOR_NOW.packet().isEmpty());
        assertTrue(SavedOutcome.NONE_LASTING.packet().isEmpty());
    }
}
