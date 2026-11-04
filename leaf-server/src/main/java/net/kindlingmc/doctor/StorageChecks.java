package net.kindlingmc.doctor;

import java.util.Locale;
import java.util.Optional;

/** Checks of the disk the worlds live on. */
final class StorageChecks {

    private static final long LOW_FREE_BYTES = 5 * ByteSize.GIB;
    private static final double LOW_FREE_SHARE = 0.10;
    private static final String DEFLATE = "deflate";
    private static final String LZ4 = "lz4";

    private StorageChecks() {
    }

    static Optional<Finding> worldDisk(final Optional<DiskSpace> disk) {
        if (disk.isEmpty()) {
            return Optional.empty();
        }
        final DiskSpace space = disk.get();
        final String numbers = ByteSize.format(space.usableBytes()) + " free of " + ByteSize.format(space.totalBytes())
            + String.format(Locale.ROOT, " (%.0f %%)", space.freeShare() * 100);
        if (hasRoom(space)) {
            return Optional.of(Finding.note("The disk with the worlds has " + numbers, Basis.GENERAL_ADVICE));
        }
        return Optional.of(Finding.warn(
            "The disk with the worlds has only " + numbers + ": a full disk makes saving fail and can damage chunks",
            "Free disk space or move the server to a bigger disk, and keep in mind that backups and pregeneration fill a disk quickly",
            Basis.GENERAL_ADVICE));
    }

    /**
     * Vanilla saves every chunk a player had loaded again when it unloads, so travel through explored land keeps the chunk
     * workers compressing; LZ4 trades that CPU time for larger region files.
     *
     * @param compression {@code region-file-compression} from server.properties; empty when Leaf's Buffered Linear format
     *                    stores the chunks and the setting does not apply
     */
    static Optional<Finding> regionCompression(final Optional<String> compression, final Optional<DiskSpace> disk) {
        if (compression.isEmpty()) {
            return Optional.empty();
        }
        return switch (compression.get()) {
            case LZ4 -> Optional.of(Finding.ok("Region files are compressed with LZ4: saving chunks costs less CPU time, "
                + "chunk data takes about twice the space of deflate"));
            case DEFLATE -> disk.isPresent() && !hasRoom(disk.get())
                ? Optional.of(Finding.ok("Region files use deflate, vanilla's default, which keeps them small on a disk with little room"))
                : Optional.of(Finding.info(
                    "Region files use deflate, vanilla's default: with lz4, a flight through an already generated world took 10 % "
                        + "less CPU time and the chunk workers 35 % less, with the same tick times, while a chunk saved with lz4 took "
                        + "about twice the space",
                    "Set region-file-compression=lz4 in server.properties and restart when the disk has room; chunks saved before "
                        + "stay readable and are written with lz4 the next time they are saved",
                    Basis.KINDLING_MEASUREMENT));
            default -> Optional.empty();
        };
    }

    private static boolean hasRoom(final DiskSpace space) {
        return space.usableBytes() >= LOW_FREE_BYTES && space.freeShare() >= LOW_FREE_SHARE;
    }
}
