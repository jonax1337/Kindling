package previewchunks;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Packet sizes. For the workload: the TERRAIN chunk against the preview skin of the same chunk, both with the same
 * estimated sky light (the TERRAIN chunk still lacks ores, carvers and features, so a real chunk is bigger). With
 * --world (a region directory) and --world-seed: saved full chunks with their stored light against the preview of
 * the same position, plus how often the preview's top matches the saved chunk's.
 */
final class Packets {
    private static final String[] DELTAS = {"same height", "1 off", "2-3 off", "4-7 off", "8+ off"};

    /** Sizes and encode times of one kind of packet. */
    private record Tally(String name, DoubleArrayList raw, DoubleArrayList compressed, DoubleArrayList millis) {
        Tally(String name) {
            this(name, new DoubleArrayList(), new DoubleArrayList(), new DoubleArrayList());
        }

        void add(ChunkPacket.Size size, double encodeMillis) {
            this.raw.add(size.raw());
            this.compressed.add(size.compressed());
            this.millis.add(encodeMillis);
        }

        void print() {
            System.out.printf("  %-24s raw median %,8.0f B (mean %,8.0f), zlib median %,7.0f B (mean %,7.0f), encode+zlib median %.3f ms%n", this.name,
                Stats.median(this.raw.toDoubleArray()), Stats.mean(this.raw.toDoubleArray()), Stats.median(this.compressed.toDoubleArray()),
                Stats.mean(this.compressed.toDoubleArray()), Stats.median(this.millis.toDoubleArray()));
        }
    }

    private Packets() {
    }

    static int run(Workload workload, Vanilla vanilla, Options options) throws IOException {
        compareGenerated(workload);
        if (options.world().isPresent()) {
            compareSaved(options.world().get(), vanilla, options);
        }
        return 0;
    }

    private static void compareGenerated(Workload workload) {
        Tally terrain = new Tally("TERRAIN chunk");
        Tally preview = new Tally("preview skin");
        for (Workload.Task task : workload.tasks()) {
            measure(terrain, task.terrain().generateFull(task.pos()));
            measure(preview, task.preview().generateSkin(task.pos()));
        }
        System.out.printf("generated chunks: %d, estimated sky light on both%n", workload.tasks().size());
        terrain.print();
        preview.print();
    }

    /** The three client heightmaps are stood in for by worldgen heightmaps of the same size. */
    private static void measure(Tally tally, ProtoChunk chunk) {
        long[] worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).getRawData();
        long[] oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG).getRawData();
        long start = System.nanoTime();
        ChunkPacket.Size size = ChunkPacket.measure(chunk.getPos(), chunk.getSections(), List.of(oceanFloor, oceanFloor, worldSurface),
            ChunkPacket.skyFromAbove(chunk.getSections()));
        tally.add(size, (System.nanoTime() - start) / 1e6);
    }

    private static void compareSaved(Path regionDir, Vanilla vanilla, Options options) throws IOException {
        Terrain terrain = new Terrain(World.create(vanilla, options.worldSeed()), options.possibleBiomes());
        Preview previews = new Preview(terrain, options.margins());
        RegionReader.Sample sample = RegionReader.fullChunks(regionDir, options.limit());
        Tally real = new Tally("saved full chunk");
        Tally preview = new Tally("preview skin");
        long[] topBlock = new long[2];
        long[] terrainDelta = new long[DELTAS.length];
        for (RegionReader.SavedChunk chunk : sample.chunks()) {
            ProtoChunk previewChunk = previews.generateSkin(chunk.pos());
            LevelChunkSection[] realSections = measureSaved(real, chunk, previewChunk, vanilla);
            measure(preview, previewChunk);
            compareTops(realSections, previewChunk, topBlock, terrainDelta);
        }
        System.out.printf("saved full chunks from %s: %d sampled uniformly, seed %d, skipped on the way: %s%n", regionDir,
            sample.chunks().size(), options.worldSeed(), sample.skipped());
        real.print();
        preview.print();
        System.out.printf("  highest non-air block, same height and state: %.1f %% of %d columns%n", 100.0 * topBlock[0] / Math.max(1, topBlock[1]), topBlock[1]);
        for (int i = 0; i < DELTAS.length; i++) {
            System.out.printf("  terrain top (no leaves, logs or passable blocks) vs preview top: %-12s %.1f %%%n", DELTAS[i],
                100.0 * terrainDelta[i] / Math.max(1, topBlock[1]));
        }
    }

    /** Decodes the saved chunk, then times only the packet encoding, as for the previews. */
    private static LevelChunkSection[] measureSaved(Tally tally, RegionReader.SavedChunk chunk, ProtoChunk shape, Vanilla vanilla) {
        int minSectionY = shape.getMinSectionY();
        int count = shape.getSectionsCount();
        LevelChunkSection[] sections = chunk.sections(vanilla.containerFactory(), minSectionY, count);
        ChunkPacket.Light light = new ChunkPacket.Light(ChunkPacket.Layers.of(chunk.lightLayers("SkyLight", minSectionY, count)),
            ChunkPacket.Layers.of(chunk.lightLayers("BlockLight", minSectionY, count)));
        List<long[]> heightmaps = chunk.heightmaps();
        long start = System.nanoTime();
        tally.add(ChunkPacket.measure(chunk.pos(), sections, heightmaps, light), (System.nanoTime() - start) / 1e6);
        return sections;
    }

    private static void compareTops(LevelChunkSection[] real, ProtoChunk preview, long[] topBlock, long[] terrainDelta) {
        LevelChunkSection[] previewSections = preview.getSections();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int realTop = highest(real, x, z, false);
                int previewTop = highest(previewSections, x, z, false);
                topBlock[1]++;
                if (realTop == previewTop && realTop >= 0 && state(real, x, realTop, z) == state(previewSections, x, previewTop, z)) {
                    topBlock[0]++;
                }
                int delta = Math.abs(highest(real, x, z, true) - highest(previewSections, x, z, true));
                terrainDelta[deltaBucket(delta)]++;
            }
        }
    }

    /** The index (from the bottom section) of the highest non-air block, or of the highest terrain block; -1 when none. */
    private static int highest(LevelChunkSection[] sections, int x, int z, boolean terrainOnly) {
        for (int y = sections.length * 16 - 1; y >= 0; y--) {
            BlockState state = state(sections, x, y, z);
            if (terrainOnly ? isTerrain(state) : !state.isAir()) {
                return y;
            }
        }
        return -1;
    }

    private static boolean isTerrain(BlockState state) {
        return Preview.isTerrain(state) && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS);
    }

    private static BlockState state(LevelChunkSection[] sections, int x, int y, int z) {
        return sections[y >> 4].getBlockState(x, y & 15, z);
    }

    private static int deltaBucket(int delta) {
        if (delta <= 1) {
            return delta;
        }
        if (delta <= 3) {
            return 2;
        }
        return delta <= 7 ? 3 : 4;
    }
}
