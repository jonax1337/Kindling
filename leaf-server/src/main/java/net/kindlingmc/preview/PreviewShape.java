package net.kindlingmc.preview;

import java.util.Optional;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseSettings;

/**
 * How the previews of one world are built. Every preview has the world's height, as the client expects it, and the
 * terrain inside the noise settings' range.
 * <ul>
 * <li>Overworld-like worlds (sky light, no ceiling, noise over the whole world height) get a band around the surface
 * with the default block below it, see {@link PreviewGenerator}.
 * <li>Every other world, such as the Nether and the End, gets the whole column: there, the default block below a
 * surface would fill caves, or the void under floating islands.
 * <li>A world without sky light, such as the Nether, gets the block light of the preview's own blocks instead.
 * </ul>
 *
 * @param minY the world's lowest block
 * @param height the world's height in blocks
 */
record PreviewShape(int minY, int height, boolean skyLight, boolean surfaceBand) {
    /** Empty when the noise reaches outside the world (no valid chunk packet could carry such terrain). */
    static Optional<PreviewShape> of(DimensionType dimension, NoiseSettings noise, LevelHeightAccessor world) {
        int noiseMaxY = noise.minY() + noise.height();
        int worldMaxY = world.getMinY() + world.getHeight();
        if (noise.minY() < world.getMinY() || noiseMaxY > worldMaxY) {
            return Optional.empty();
        }
        boolean noiseSpansWorld = noise.minY() == world.getMinY() && noiseMaxY == worldMaxY;
        boolean surfaceBand = dimension.hasSkyLight() && !dimension.hasCeiling() && noiseSpansWorld;
        return Optional.of(new PreviewShape(world.getMinY(), world.getHeight(), dimension.hasSkyLight(), surfaceBand));
    }

    LevelHeightAccessor worldHeight() {
        return LevelHeightAccessor.create(this.minY, this.height);
    }

    /** The light a preview is sent with when no saved light is used: from the sky where the world has one, else from its blocks. */
    SkyLight.Light estimatedLight(LevelChunkSection[] sections) {
        return this.skyLight ? SkyLight.fromAbove(sections) : SkyLight.fromBlocks(sections);
    }
}
