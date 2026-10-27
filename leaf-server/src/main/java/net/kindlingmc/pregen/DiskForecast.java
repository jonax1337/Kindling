package net.kindlingmc.pregen;

import java.util.Locale;
import net.kindlingmc.doctor.ByteSize;

/** What a run still needs on disk against what the disk has left; see {@link DiskGuard}. */
record DiskForecast(long freeBytes, long neededBytes, long bytesPerChunk, boolean measured, long reserveBytes) {

    private static final long KIB = 1024L;

    /** Whether finishing the run would leave less than the reserve; a disk of unknown size never runs out. */
    boolean runsOut() {
        return this.freeBytes != PregenWorld.UNKNOWN_FREE_BYTES && this.freeBytes - this.neededBytes < this.reserveBytes;
    }

    String describe() {
        final String free = this.freeBytes == PregenWorld.UNKNOWN_FREE_BYTES ? "unknown" : ByteSize.format(this.freeBytes);
        return String.format(Locale.ROOT, "%s free, about %s still needed (%d KiB per chunk, %s), reserve %s",
            free, ByteSize.format(this.neededBytes), this.bytesPerChunk / KIB,
            this.measured ? "measured" : "assumed", ByteSize.format(this.reserveBytes));
    }
}
