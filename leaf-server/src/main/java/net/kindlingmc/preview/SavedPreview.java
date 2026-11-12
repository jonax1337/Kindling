package net.kindlingmc.preview;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

/**
 * The preview of a chunk that is already saved: its blocks and biomes as saved, and its saved light. In overworld-like
 * worlds only from a little below each column's surface upward, with the default block below that (see
 * {@link Skin#skinBelowSurfaces}); in other worlds (see {@link PreviewShape}) the whole column, with every block hidden
 * from all six sides turned into the default block (see {@link Skin#hideEnclosed}). So trees,
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
        int skinMinSectionY = skin(chunk, terrain);
        SkyLight.Light light = SavedLight.of(data.lightCorrect(), data.sectionData(), sections.length, chunk.getMinSectionY(), skinMinSectionY)
            .orElseGet(() -> terrain.shape().estimatedLight(sections));
        return PreviewPacket.of(chunk, light);
    }

    /** Skins the chunk as its world's previews are shaped; returns the lowest section the skin keeps. */
    private static int skin(ProtoChunk chunk, Terrain terrain) {
        BlockState defaultBlock = terrain.settings().defaultBlock();
        if (terrain.shape().surfaceBand()) {
            return Skin.skinBelowSurfaces(chunk, defaultBlock, terrain.containerFactory()).minSectionY();
        }
        Skin.hideEnclosed(chunk, defaultBlock);
        return chunk.getMinSectionY();
    }
}
