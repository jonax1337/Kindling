package net.kindlingmc.doctor;

/** The space on the file store that holds the worlds. */
record DiskSpace(long usableBytes, long totalBytes) {

    double freeShare() {
        return this.totalBytes <= 0 ? 1.0 : (double) this.usableBytes / this.totalBytes;
    }
}
