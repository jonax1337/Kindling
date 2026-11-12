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
 * <p>{@link #compareRealChunks} runs the same comparison between two real chunks of one position, generated in two
 * worlds of one seed. That is the noise floor of the preview: vanilla itself depends on the order its chunks are
 * generated in, so no preview can match a real chunk better than another real generation does.
 *
 * <p>Per column it compares the surface height ({@code MOTION_BLOCKING}: leaves and water count, grass does not), the
 * top block and the sky light around the surface. The top block is the block at the {@code WORLD_SURFACE} height,
 * because that is the block a player sees on top, including grass, flowers and snow layers, which
 * {@code MOTION_BLOCKING} skips.
 *
 * <p>Call on the tick thread, with real chunks whose light is ready.
 */
public final class PreviewFidelity {
    private static final Set<Heightmap.Types> COMPARED_HEIGHTMAPS = EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.WORLD_SURFACE);
    /** The light band runs from this many blocks below the reference surface height to {@link #BAND_ABOVE_SURFACE} above it. */
    private static final int BAND_BELOW_SURFACE = 8;
    private static final int BAND_ABOVE_SURFACE = 4;
    private static final int CELLS_PER_LAYER = 256;
    private static final int LIGHT_SECTIONS_BELOW_WORLD = 1;

    private PreviewFidelity() {
    }

    /** The counts of one chunk, or none where the world has no preview generator or no sky light to compare (such as the Nether). */
    public static Optional<FidelityCounts> compare(final ServerLevel level, final LevelChunk real) {
        final PreviewWorld world = PreviewWorld.of(level);
        if (!world.hasSkyLight()) {
            return Optional.empty();
        }
        final ProtoChunk preview = world.seedChunk(real.getPos());
        Heightmap.primeHeightmaps(preview, COMPARED_HEIGHTMAPS);
        final SkyLight.Light previewLight = SkyLight.fromAbove(preview.getSections());
        final SkyLightReader previewReader = (x, y, z) -> skyLightAt(previewLight, preview.getMinY(), x, y, z);
        return Optional.of(compareSides(new Side(real, new EngineLight(level, real)), new Side(preview, previewReader)));
    }

    /**
     * The counts of one chunk position generated twice: {@code reference} plays the real chunk and {@code other} the
     * preview, so a height difference is {@code other} minus {@code reference} and a tree {@code other} lacks is a
     * missing tree. The light of each chunk comes from its own world's light engine.
     *
     * @throws IllegalArgumentException if the chunks are not at the same position
     */
    public static FidelityCounts compareRealChunks(final ServerLevel referenceLevel, final LevelChunk reference,
        final ServerLevel otherLevel, final LevelChunk other) {
        if (!reference.getPos().equals(other.getPos())) {
            throw new IllegalArgumentException("chunks at " + reference.getPos() + " and " + other.getPos() + " are not the same chunk");
        }
        return compareSides(new Side(reference, new EngineLight(referenceLevel, reference)), new Side(other, new EngineLight(otherLevel, other)));
    }

    static FidelityCounts compareSides(final Side reference, final Side other) {
        return new ChunkComparison(reference, other).counts();
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

    /** The sky light at a block of the compared chunk. */
    @FunctionalInterface
    interface SkyLightReader {
        /**
         * @param x the block's x in the chunk, 0 to 15
         * @param y the block's y in the world
         * @param z the block's z in the chunk, 0 to 15
         */
        int skyLightAt(int x, int y, int z);
    }

    /** One side of a comparison: a chunk whose heightmaps are ready, and the sky light it has. */
    record Side(ChunkAccess chunk, SkyLightReader light) {
    }

    /** The sky light a world's light engine holds for a chunk. */
    private static final class EngineLight implements SkyLightReader {
        private final ServerLevel level;
        private final ChunkPos chunk;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        EngineLight(final ServerLevel level, final LevelChunk chunk) {
            this.level = level;
            this.chunk = chunk.getPos();
        }

        @Override
        public int skyLightAt(final int x, final int y, final int z) {
            return this.level.getBrightness(LightLayer.SKY, this.pos.set(this.chunk.getMinBlockX() + x, y, this.chunk.getMinBlockZ() + z));
        }
    }

    /** One chunk's comparison of an {@code other} side against the {@code reference} side. */
    private static final class ChunkComparison {
        private final Side reference;
        private final Side other;
        private final FidelityCounts counts = new FidelityCounts();
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        ChunkComparison(final Side reference, final Side other) {
            this.reference = reference;
            this.other = other;
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
            final int referenceSurface = this.reference.chunk().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            this.counts.recordHeightDifference(this.other.chunk().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - referenceSurface);
            compareTopBlock(x, z);
            compareLight(x, z, referenceSurface + 1);
        }

        private void compareTopBlock(final int x, final int z) {
            final BlockState referenceTop = topBlock(this.reference.chunk(), x, z);
            final BlockState otherTop = topBlock(this.other.chunk(), x, z);
            if (referenceTop.is(otherTop.getBlock())) {
                this.counts.recordTopBlockEqual(x, z);
            } else {
                this.counts.recordTopBlockMismatch(TopBlockMismatch.of(referenceTop, otherTop), x, z);
            }
        }

        private BlockState topBlock(final ChunkAccess chunk, final int x, final int z) {
            final int y = Math.max(chunk.getMinY(), chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
            final ChunkPos chunkPos = chunk.getPos();
            return chunk.getBlockState(this.pos.set(chunkPos.getMinBlockX() + x, y, chunkPos.getMinBlockZ() + z));
        }

        /** The first free block above the reference surface, then every block from {@code BAND_BELOW_SURFACE} below it to {@code BAND_ABOVE_SURFACE} above. */
        private void compareLight(final int x, final int z, final int aboveSurface) {
            final ChunkAccess chunk = this.reference.chunk();
            if (aboveSurface <= chunk.getMaxY()) {
                this.counts.recordLightAboveSurface(referenceLight(x, aboveSurface, z), otherLight(x, aboveSurface, z));
            }
            final int lowest = Math.max(chunk.getMinY(), aboveSurface - BAND_BELOW_SURFACE);
            final int highest = Math.min(chunk.getMaxY(), aboveSurface + BAND_ABOVE_SURFACE);
            for (int y = lowest; y <= highest; y++) {
                this.counts.recordLightInBand(referenceLight(x, y, z), otherLight(x, y, z));
            }
        }

        private int referenceLight(final int x, final int y, final int z) {
            return this.reference.light().skyLightAt(x, y, z);
        }

        private int otherLight(final int x, final int y, final int z) {
            return this.other.light().skyLightAt(x, y, z);
        }
    }
}
