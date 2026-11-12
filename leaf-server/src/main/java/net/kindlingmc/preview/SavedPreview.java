package net.kindlingmc.preview;

import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

/**
 * The preview of a chunk that is already saved: its blocks and biomes from a little below each column's surface
 * upward, as saved (the default block below that, see {@link Skin#skinBelowSurfaces}), and its saved light. So trees,
 * structures and player builds show as they are. Block entities and entities are not part of it (chests, signs,
 * banners and item frames are missing), and neither are the vegetation and snow passes of seed previews.
 *
 * <p>Thread safety: works on the parsed data it is given, which only the calling thread owns, and on the immutable
 * {@link Terrain}.
 */
final class SavedPreview {
    private SavedPreview() {
    }

    /** The data must have passed {@link SavedChunkCheck}, and its sections are taken over: the caller must not use them again. */
    static PreviewPacket of(SerializableChunkData data, Terrain terrain) {
        ProtoChunk chunk = terrain.emptyChunk(data.chunkPos());
        LevelChunkSection[] sections = chunk.getSections();
        for (SerializableChunkData.SectionData saved : data.sectionData()) {
            if (saved.chunkSection() != null) {
                sections[chunk.getSectionIndexFromSectionY(saved.y())] = saved.chunkSection();
            }
        }
        Band band = Skin.skinBelowSurfaces(chunk, terrain.settings().defaultBlock(), terrain.containerFactory());
        SkyLight.Light light = SavedLight.of(data.lightCorrect(), data.sectionData(), sections, chunk.getMinSectionY(), band.minSectionY());
        return PreviewPacket.of(chunk, light);
    }
}
