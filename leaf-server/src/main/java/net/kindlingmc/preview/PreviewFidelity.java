package net.kindlingmc.preview;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * How faithful a seed preview is: compares the preview of a chunk, built the way the server sends it, with the real
 * chunk column by column. A measurement tool for the bench, not part of the preview path. The saved-chunk preview is
 * never compared; it is the real chunk as saved.
 *
 * <p>Per column it compares the surface height ({@code MOTION_BLOCKING}: leaves and water count, grass does not), the
 * top block and the sky light around the surface. The top block is the block at the {@code WORLD_SURFACE} height,
 * because that is the block a player sees on top, including grass, flowers and snow layers, which
 * {@code MOTION_BLOCKING} skips.
 *
 * <p>Call on the tick thread, with a real chunk whose light is ready.
 */
public final class PreviewFidelity {
    private static final Set<Heightmap.Types> COMPARED_HEIGHTMAPS = EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.WORLD_SURFACE);
    /** The light band runs from this many blocks below the real surface height to {@link #BAND_ABOVE_SURFACE} above it. */
    private static final int BAND_BELOW_SURFACE = 8;
    private static final int BAND_ABOVE_SURFACE = 4;
    private static final int CELLS_PER_LAYER = 256;
    private static final int LIGHT_SECTIONS_BELOW_WORLD = 1;

    private PreviewFidelity() {
    }

    /** The counts of one chunk, or none where the world has no preview generator (such as the Nether). */
    public static Optional<FidelityCounts> compare(final ServerLevel level, final LevelChunk real) {
        final PreviewWorld world = PreviewWorld.of(level);
        if (!world.available()) {
            return Optional.empty();
        }
        final ProtoChunk preview = world.seedChunk(real.getPos());
        Heightmap.primeHeightmaps(preview, COMPARED_HEIGHTMAPS);
        final SkyLight.Light previewLight = SkyLight.fromAbove(preview.getSections());
        return Optional.of(new ChunkComparison(level, real, preview, previewLight).counts());
    }

    /**
     * The sky light a preview is sent with at a block of its chunk. A light section without data is open sky: the
     * estimate leaves out the sections above the highest block, as the light engine stores none for them.
     *
     * @param x the block's x in the chunk, 0 to 15
     * @param y the block's y in the world
     * @param z the block's z in the chunk, 0 to 15
     */
    static int skyLightAt(final SkyLight.Light light, final int minY, final int x, final int y, final int z) {
        final SkyLight.Layers sky = light.sky();
        final int lightSection = ((y - minY) >> 4) + LIGHT_SECTIONS_BELOW_WORLD;
        if (sky.empty().get(lightSection)) {
            return 0;
        }
        if (!sky.present().get(lightSection)) {
            return SkyLight.MAX_LIGHT;
        }
        final byte[] packed = sky.arrays().get(sky.present().get(0, lightSection).cardinality());
        final int cell = (y & 15) * CELLS_PER_LAYER + (z << 4) + x;
        return (packed[cell >> 1] >> ((cell & 1) * 4)) & 15;
    }

    /** One chunk's comparison: the real chunk, its preview and the preview's light. */
    private static final class ChunkComparison {
        private final ServerLevel level;
        private final LevelChunk real;
        private final ProtoChunk preview;
        private final SkyLight.Light previewLight;
        private final FidelityCounts counts = new FidelityCounts();
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        ChunkComparison(final ServerLevel level, final LevelChunk real, final ProtoChunk preview, final SkyLight.Light previewLight) {
            this.level = level;
            this.real = real;
            this.preview = preview;
            this.previewLight = previewLight;
        }

        FidelityCounts counts() {
            this.counts.recordChunk();
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    compareColumn(x, z);
                }
            }
            return this.counts;
        }

        /** {@code ChunkAccess.getHeight} is the y of the top block itself; the first free block is one above. */
        private void compareColumn(final int x, final int z) {
            final int realSurface = this.real.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            this.counts.recordHeightDifference(this.preview.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - realSurface);
            compareTopBlock(x, z);
            compareLight(x, z, realSurface + 1);
        }

        private void compareTopBlock(final int x, final int z) {
            final BlockState realTop = topBlock(this.real, x, z);
            final BlockState previewTop = topBlock(this.preview, x, z);
            if (realTop.is(previewTop.getBlock())) {
                this.counts.recordTopBlockEqual(x, z);
            } else {
                this.counts.recordTopBlockMismatch(TopBlockMismatch.of(realTop, previewTop), x, z);
            }
        }

        private BlockState topBlock(final ChunkAccess chunk, final int x, final int z) {
            final int y = Math.max(chunk.getMinY(), chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
            return chunk.getBlockState(blockPos(x, y, z));
        }

        /** The first free block above the real surface, then every block from {@code BAND_BELOW_SURFACE} below it to {@code BAND_ABOVE_SURFACE} above. */
        private void compareLight(final int x, final int z, final int aboveRealSurface) {
            if (aboveRealSurface <= this.real.getMaxY()) {
                this.counts.recordLightAboveSurface(realLight(x, aboveRealSurface, z), previewLightAt(x, aboveRealSurface, z));
            }
            final int lowest = Math.max(this.real.getMinY(), aboveRealSurface - BAND_BELOW_SURFACE);
            final int highest = Math.min(this.real.getMaxY(), aboveRealSurface + BAND_ABOVE_SURFACE);
            for (int y = lowest; y <= highest; y++) {
                this.counts.recordLightInBand(realLight(x, y, z), previewLightAt(x, y, z));
            }
        }

        private int realLight(final int x, final int y, final int z) {
            return this.level.getBrightness(LightLayer.SKY, blockPos(x, y, z));
        }

        private int previewLightAt(final int x, final int y, final int z) {
            return skyLightAt(this.previewLight, this.preview.getMinY(), x, y, z);
        }

        private BlockPos blockPos(final int x, final int y, final int z) {
            final ChunkPos chunk = this.real.getPos();
            return this.pos.set(chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z);
        }
    }
}
