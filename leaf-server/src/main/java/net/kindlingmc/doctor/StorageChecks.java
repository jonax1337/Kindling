package net.kindlingmc.doctor;

import java.util.Locale;
import java.util.Optional;

/** Checks of the disk the worlds live on. */
final class StorageChecks {

    private static final long LOW_FREE_BYTES = 5 * ByteSize.GIB;
    private static final double LOW_FREE_SHARE = 0.10;

    private StorageChecks() {
    }

    static Optional<Finding> worldDisk(final Optional<DiskSpace> disk) {
        if (disk.isEmpty()) {
            return Optional.empty();
        }
        final DiskSpace space = disk.get();
        final String numbers = ByteSize.format(space.usableBytes()) + " free of " + ByteSize.format(space.totalBytes())
            + String.format(Locale.ROOT, " (%.0f %%)", space.freeShare() * 100);
        if (space.usableBytes() >= LOW_FREE_BYTES && space.freeShare() >= LOW_FREE_SHARE) {
            return Optional.of(Finding.note("The disk with the worlds has " + numbers, Basis.GENERAL_ADVICE));
        }
        return Optional.of(Finding.warn(
            "The disk with the worlds has only " + numbers + ": a full disk makes saving fail and can damage chunks",
            "Free disk space or move the server to a bigger disk, and keep in mind that backups and pregeneration fill a disk quickly",
            Basis.GENERAL_ADVICE));
    }
}
