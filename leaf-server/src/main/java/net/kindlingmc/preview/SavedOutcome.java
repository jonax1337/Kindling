package net.kindlingmc.preview;

import java.util.Optional;

/**
 * What the saved chunks gave for one chunk: the preview packet, or none and whether the seed preview that replaces it
 * may be cached. A reason that lasts (nothing saved, another data version, an unreadable chunk) lets the seed preview be
 * cached like any other. A reason that is only for now (busy I/O, a read that timed out, a chunk not generated far
 * enough yet) must not, or the chunk would keep its seed preview after the saved one becomes available.
 */
record SavedOutcome(Optional<PreviewPacket> packet, boolean seedPreviewMayBeCached) {
    /** No saved preview, and none to expect soon: saved chunks are off, the world is gone, or the chunk is unusable. */
    static final SavedOutcome NONE_LASTING = new SavedOutcome(Optional.empty(), true);
    /** No saved preview for now, but a later try may find one. */
    static final SavedOutcome NONE_FOR_NOW = new SavedOutcome(Optional.empty(), false);

    static SavedOutcome of(PreviewPacket packet) {
        return new SavedOutcome(Optional.of(packet), false);
    }

    /** A chunk below the status FEATURES is completed later; every other rejection stays as it is. */
    static SavedOutcome rejected(SavedChunkCheck.Rejection rejection) {
        return rejection == SavedChunkCheck.Rejection.STATUS ? NONE_FOR_NOW : NONE_LASTING;
    }
}
