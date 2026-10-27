package net.kindlingmc.doctor;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static net.kindlingmc.doctor.Scenario.assertSeverity;
import static net.kindlingmc.doctor.Scenario.assertSkipped;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageChecksTest {

    private static Optional<DiskSpace> disk(final long freeGib, final long totalGib) {
        return Optional.of(new DiskSpace(freeGib * ByteSize.GIB, totalGib * ByteSize.GIB));
    }

    @Test
    void anUnreadableDiskIsNotChecked() {
        assertSkipped(StorageChecks.worldDisk(Optional.empty()));
    }

    @Test
    void plentyOfRoomIsAHintWithTheNumbers() {
        final Finding finding = StorageChecks.worldDisk(disk(200, 500)).orElseThrow();

        assertEquals(Severity.INFO, finding.severity());
        assertTrue(finding.what().contains("200.0 GiB free of 500.0 GiB (40 %)"), finding.what());
        assertTrue(finding.fix().isEmpty());
    }

    @Test
    void lessThanFiveGibFreeIsAWarningEvenOnASmallDisk() {
        assertSeverity(Severity.WARN, StorageChecks.worldDisk(disk(4, 20)));
    }

    @Test
    void lessThanTenPercentFreeIsAWarningEvenOnABigDisk() {
        assertSeverity(Severity.WARN, StorageChecks.worldDisk(disk(90, 1000)));
    }

    @Test
    void exactlyFiveGibAndTenPercentIsStillFine() {
        assertSeverity(Severity.INFO, StorageChecks.worldDisk(disk(5, 50)));
    }

    @Test
    void aWarningNamesTheFix() {
        final Finding finding = StorageChecks.worldDisk(disk(1, 100)).orElseThrow();

        assertTrue(finding.fix().contains("Free disk space"), finding.fix());
    }
}
