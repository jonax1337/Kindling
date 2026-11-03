package net.kindlingmc.preview;

import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

/**
 * Whether a saved chunk may be previewed as it is, or the seed preview must be used instead. Anything unusual says no:
 * the saved data is only read when it is exactly what this server version writes and complete enough to show.
 */
final class SavedChunkCheck {
    /** Why a saved chunk is not previewed. */
    enum Rejection {
        /** Saved by another version: reading it would need the data fixers, which previews never run. */
        DATA_VERSION,
        /** Saved before its features were placed, so it lacks trees, ores and structures. */
        STATUS,
        /** The region file holds the data of another chunk. */
        POSITION,
        /** It has sections outside the world's height, which the world would not load either. */
        HEIGHT
    }

    private SavedChunkCheck() {
    }

    /** Checks the raw data, before it is parsed: parsing a newer version's chunk would stop the server. */
    static Optional<Rejection> ofTag(CompoundTag chunk, int currentDataVersion) {
        if (NbtUtils.getDataVersion(chunk) != currentDataVersion) {
            return Optional.of(Rejection.DATA_VERSION);
        }
        if (!SerializableChunkData.getChunkStatusFromTag(chunk).isOrAfter(ChunkStatus.FEATURES)) {
            return Optional.of(Rejection.STATUS);
        }
        return Optional.empty();
    }

    /** Checks the parsed data against the chunk asked for and the world's section range. */
    static Optional<Rejection> ofParsed(ChunkPos expected, ChunkPos saved, List<SerializableChunkData.SectionData> sections,
        int minSectionY, int maxSectionY) {
        if (!expected.equals(saved)) {
            return Optional.of(Rejection.POSITION);
        }
        for (SerializableChunkData.SectionData section : sections) {
            if (section.y() < minSectionY - 1 || section.y() > maxSectionY + 1) {
                return Optional.of(Rejection.HEIGHT);
            }
        }
        return Optional.empty();
    }
}
