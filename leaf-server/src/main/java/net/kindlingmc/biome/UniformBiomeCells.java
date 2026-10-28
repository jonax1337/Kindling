package net.kindlingmc.biome;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.jspecify.annotations.Nullable;

/**
 * Finds the biome of the eight quart cells the biome zoom picks from (quart x to x + 1, y to y + 1, z to z + 1) when it
 * can be read off the chunk sections without looking at a single cell: every cell lies in sections that store only one
 * biome (no index bits), and it is the same biome. If all eight cells hold one biome, the zoom's answer is that biome,
 * wherever the fiddled distances point. Reads only, like {@code ChunkAccess.getNoiseBiome}, so it runs wherever that may
 * run.
 */
public final class UniformBiomeCells {

    private UniformBiomeCells() {
    }

    /**
     * Whether quart columns x to x + 1 and z to z + 1 lie in one chunk, so the zoom reads only that chunk.
     */
    public static boolean shareChunkColumn(final int quartX, final int quartZ) {
        return (quartX & QuartPos.MASK) != QuartPos.MASK && (quartZ & QuartPos.MASK) != QuartPos.MASK;
    }

    /**
     * The biome of the eight cells from (quartX, quartY, quartZ) in a chunk with these sections, or null when the cells
     * reach into another chunk or the sections holding them do not store the same single biome. Rows below or above the
     * chunk count as its bottom or top section, as {@code ChunkAccess.getNoiseBiome} reads them.
     */
    public static @Nullable Holder<Biome> inChunk(
        final LevelChunkSection[] sections, final int minSection, final int quartX, final int quartY, final int quartZ
    ) {
        if (!shareChunkColumn(quartX, quartZ)) {
            return null;
        }
        final int lowerIndex = sectionIndex(quartY, minSection, sections.length);
        final Holder<Biome> lower = singleValue(sections[lowerIndex].getBiomes());
        final int upperIndex = sectionIndex(quartY + 1, minSection, sections.length);
        if (lower == null || upperIndex == lowerIndex) {
            return lower;
        }
        return singleValue(sections[upperIndex].getBiomes()) == lower ? lower : null;
    }

    /**
     * The index of the section that {@code ChunkAccess.getNoiseBiome} reads for quart row quartY.
     */
    public static int sectionIndex(final int quartY, final int minSection, final int sectionCount) {
        return Math.clamp((quartY >> QuartPos.BITS) - minSection, 0, sectionCount - 1);
    }

    /**
     * The value every position of the container holds when it stores no index bits (each position reads palette entry
     * 0), else null. The value is read the way {@code PalettedContainer.get} reads it, from the same snapshot.
     */
    public static <T> @Nullable T singleValue(final PalettedContainerRO<T> container) {
        if (!(container instanceof PalettedContainer<T> palettedContainer)) {
            return null;
        }
        final PalettedContainer.Data<T> data = palettedContainer.leaf$getDataAcquire();
        return data.storage().getBits() == 0 ? palettedContainer.leaf$getFromData(data, 0) : null;
    }
}
