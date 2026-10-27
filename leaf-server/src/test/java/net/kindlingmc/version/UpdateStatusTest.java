package net.kindlingmc.version;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateStatusTest {

    @Test
    void theNewestPublishedBuildIsTheLatest() {
        assertEquals(new UpdateStatus.Latest(), UpdateStatus.compare(3, List.of(3, 2, 1)));
    }

    @Test
    void aBuildNewerThanEveryPublishedOneIsTheLatest() {
        assertEquals(new UpdateStatus.Latest(), UpdateStatus.compare(9, List.of(3, 2, 1)));
    }

    @Test
    void withoutPublishedBuildsTheCheckIsUnavailableWithAReason() {
        final UpdateStatus status = UpdateStatus.compare(3, List.of());

        final UpdateStatus.Unavailable unavailable = assertInstanceOf(UpdateStatus.Unavailable.class, status);
        assertTrue(unavailable.reason().contains("no build is published"), unavailable.reason());
    }

    @Test
    void anOlderBuildCountsOnlyThePublishedNewerBuilds() {
        assertEquals(new UpdateStatus.Behind(7, 2), UpdateStatus.compare(2, List.of(7, 5, 2, 1)));
    }

    @Test
    void theLatestBuildIsFoundWhateverTheOrderOfTheList() {
        assertEquals(new UpdateStatus.Behind(7, 3), UpdateStatus.compare(1, List.of(2, 7, 1, 5)));
    }
}
