package net.kindlingmc.version;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateStatusTest {

    @Test
    void theNewestPublishedBuildIsTheLatest() {
        assertEquals(new UpdateStatus.Latest(), UpdateStatus.compare(3, List.of(3, 2, 1)));
    }

    @Test
    void withoutPublishedBuildsTheRunningBuildIsTheLatest() {
        assertEquals(new UpdateStatus.Latest(), UpdateStatus.compare(3, List.of()));
    }

    @Test
    void anOlderBuildCountsOnlyThePublishedNewerBuilds() {
        assertEquals(new UpdateStatus.Behind(7, 2), UpdateStatus.compare(2, List.of(7, 5, 2, 1)));
    }
}
