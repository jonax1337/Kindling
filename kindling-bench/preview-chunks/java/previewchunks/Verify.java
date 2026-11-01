package previewchunks;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Checks every preview against the TERRAIN work on the whole chunk: the density inside the band bit for bit, the noise
 * biomes of the band's sections, every block from the band's bottom to the top of the world (counted by depth below the
 * column's terrain top), and the two worldgen heightmaps. It also reports how far the terrain top lies from the
 * preliminary surface the band starts from, and how big the bands were. Exit code 1 when anything differs.
 */
final class Verify {
    private static final String[] DEPTHS = {"above the top", "top block", "1-3 below", "4-7 below", "8-15 below", "16-31 below", "32+ below"};
    private static final int EXAMPLES = 8;

    private final long[] blocksCompared = new long[DEPTHS.length];
    private final long[] blocksDiffering = new long[DEPTHS.length];
    private final List<String> examples = new ArrayList<>();
    private final DoubleArrayList bandHeights = new DoubleArrayList();
    private final DoubleArrayList topAbovePreliminary = new DoubleArrayList();
    private final DoubleArrayList highestTopAbovePreliminary = new DoubleArrayList();
    private final DoubleArrayList lowestTopAbovePreliminary = new DoubleArrayList();
    private long densityPoints;
    private long densityMismatches;
    private long biomeQuarts;
    private long biomeMismatches;
    private long heightmapColumns;
    private long heightmapMismatches;
    private long grownChunks;
    private int mostGrowths;
    private int worldHeight;

    static int run(Workload workload) {
        Verify verify = new Verify();
        for (Workload.Task task : workload.tasks()) {
            verify.check(task);
        }
        verify.print(workload.tasks().size());
        return verify.differences() == 0 ? 0 : 1;
    }

    private long differences() {
        return this.densityMismatches + this.biomeMismatches + this.heightmapMismatches + Arrays.stream(this.blocksDiffering).sum();
    }

    /** Both generations read the same neighbour biomes (see ChunkBiomes.Outside), so differences come from the band. */
    private void check(Workload.Task task) {
        ChunkBiomes.Outside neighbours = new ChunkBiomes.Outside(task.terrain().world());
        ProtoChunk full = task.terrain().generateFull(task.pos(), neighbours);
        Preview.Result preview = task.preview().generate(task.pos(), neighbours);
        this.worldHeight = task.terrain().noiseSettings().height();
        compareDensity(task, preview.band());
        compareBiomes(full, preview);
        compareBlocks(task, full, preview);
        compareHeightmaps(full, preview.chunk());
        recordBand(full, preview);
    }

    private void compareDensity(Workload.Task task, Band band) {
        DensityVolume fullVolume = task.terrain().chunkVolume(task.pos());
        DensityVolume bandVolume = band.volume(task.pos());
        float[] full = finalDensity(task.terrain(), fullVolume);
        float[] banded = finalDensity(task.terrain(), bandVolume);
        int offsetY = band.minY() - fullVolume.minBlockY();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < bandVolume.sizeY(); y++) {
                    float expected = full[fullVolume.indexUnchecked(x, y + offsetY, z)];
                    float actual = banded[bandVolume.indexUnchecked(x, y, z)];
                    this.densityPoints++;
                    if (Float.floatToRawIntBits(expected) != Float.floatToRawIntBits(actual)) {
                        this.densityMismatches++;
                        example(task + " density at y " + (band.minY() + y) + ": " + expected + " vs " + actual);
                    }
                }
            }
        }
    }

    private static float[] finalDensity(Terrain terrain, DensityVolume volume) {
        DensityFunction finalDensity = terrain.world().vanilla().settings().noiseRouter().finalDensity();
        try (NoiseChunk noiseChunk = terrain.noiseChunk(volume);
             ScopedDensityBuffer values = noiseChunk.cachingSamplers().get(finalDensity).sampleVolume(volume)) {
            return Arrays.copyOf(values.values, volume.size());
        }
    }

    private void compareBiomes(ProtoChunk full, Preview.Result preview) {
        ChunkPos pos = full.getPos();
        int minQuartX = QuartPos.fromBlock(pos.getMinBlockX());
        int minQuartZ = QuartPos.fromBlock(pos.getMinBlockZ());
        int minQuartY = QuartPos.fromSection(preview.band().minSectionY());
        int maxQuartY = QuartPos.fromSection(preview.band().maxSectionY() + 1) - 1;
        for (int quartY = minQuartY; quartY <= maxQuartY; quartY++) {
            for (int quartZ = minQuartZ; quartZ < minQuartZ + 4; quartZ++) {
                for (int quartX = minQuartX; quartX < minQuartX + 4; quartX++) {
                    this.biomeQuarts++;
                    if (!full.getNoiseBiome(quartX, quartY, quartZ).equals(preview.chunk().getNoiseBiome(quartX, quartY, quartZ))) {
                        this.biomeMismatches++;
                    }
                }
            }
        }
    }

    private void compareBlocks(Workload.Task task, ProtoChunk full, Preview.Result preview) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        ChunkPos chunkPos = full.getPos();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int top = Preview.topTerrainY(full, x, z);
                for (int y = preview.band().minY(); y <= full.getMaxY(); y++) {
                    pos.set(chunkPos.getMinBlockX() + x, y, chunkPos.getMinBlockZ() + z);
                    compareBlock(task, pos, full.getBlockState(pos), preview.chunk().getBlockState(pos), depthBucket(top - y));
                }
            }
        }
    }

    private void compareBlock(Workload.Task task, BlockPos pos, BlockState expected, BlockState actual, int bucket) {
        this.blocksCompared[bucket]++;
        if (expected != actual) {
            this.blocksDiffering[bucket]++;
            example(task + " block " + pos.toShortString() + " (" + DEPTHS[bucket] + "): " + expected + " vs " + actual);
        }
    }

    private static int depthBucket(int depth) {
        if (depth < 0) {
            return 0;
        }
        if (depth == 0) {
            return 1;
        }
        int bucket = 2;
        for (int limit = 3; depth > limit && bucket < DEPTHS.length - 1; limit = limit * 2 + 1) {
            bucket++;
        }
        return bucket;
    }

    private void compareHeightmaps(ProtoChunk full, ProtoChunk preview) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                this.heightmapColumns++;
                if (full.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) != preview.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z)
                    || full.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) != preview.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z)) {
                    this.heightmapMismatches++;
                }
            }
        }
    }

    private void recordBand(ProtoChunk full, Preview.Result preview) {
        this.bandHeights.add(preview.band().height());
        if (preview.growths() > 0) {
            this.grownChunks++;
            this.mostGrowths = Math.max(this.mostGrowths, preview.growths());
        }
        float[] preliminary = preview.preliminarySurface();
        double highestTop = Double.NEGATIVE_INFINITY;
        double lowestTop = Double.POSITIVE_INFINITY;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int top = Preview.topTerrainY(full, x, z);
                this.topAbovePreliminary.add(top - preliminary[Preview.column(x, z)]);
                highestTop = Math.max(highestTop, top);
                lowestTop = Math.min(lowestTop, top);
            }
        }
        this.highestTopAbovePreliminary.add(highestTop - max(preliminary));
        this.lowestTopAbovePreliminary.add(lowestTop - min(preliminary));
    }

    private void example(String line) {
        if (this.examples.size() < EXAMPLES) {
            this.examples.add(line);
        }
    }

    private void print(int chunks) {
        System.out.printf("chunks: %d%n", chunks);
        System.out.printf("density in the bands: %d points, %d differ from the full chunk (bit compare)%n", this.densityPoints, this.densityMismatches);
        System.out.printf("noise biomes in the bands' sections: %d quarts, %d differ%n", this.biomeQuarts, this.biomeMismatches);
        System.out.println("blocks from the band's bottom to the top of the world, by depth below the column's terrain top:");
        for (int i = 0; i < DEPTHS.length; i++) {
            System.out.printf("  %-14s %,14d compared, %,10d differ (%.4f %%)%n", DEPTHS[i], this.blocksCompared[i], this.blocksDiffering[i],
                100.0 * this.blocksDiffering[i] / Math.max(1, this.blocksCompared[i]));
        }
        System.out.printf("worldgen heightmaps: %d columns, %d differ%n", this.heightmapColumns, this.heightmapMismatches);
        double[] heights = this.bandHeights.toDoubleArray();
        System.out.printf("band height in blocks: %s; mean %.1f = %.1f %% of the world height%n", Stats.spread(heights), Stats.mean(heights),
            100 * Stats.mean(heights) / this.worldHeight);
        System.out.printf("chunks whose band grew: %d (%.1f %%), at most %d times%n", this.grownChunks, 100.0 * this.grownChunks / chunks, this.mostGrowths);
        System.out.printf("terrain top minus preliminary surface, per column: %s%n", Stats.spread(this.topAbovePreliminary.toDoubleArray()));
        System.out.printf("per chunk, highest top minus highest preliminary: %s%n", Stats.spread(this.highestTopAbovePreliminary.toDoubleArray()));
        System.out.printf("per chunk, lowest top minus lowest preliminary: %s%n", Stats.spread(this.lowestTopAbovePreliminary.toDoubleArray()));
        this.examples.forEach(line -> System.out.println("  example: " + line));
        System.out.println(differences() == 0
            ? "VERDICT identical: density, biomes, blocks and heightmaps"
            : String.format("VERDICT DIFFERS: density %d, biomes %d, blocks %d, heightmaps %d", this.densityMismatches, this.biomeMismatches,
                Arrays.stream(this.blocksDiffering).sum(), this.heightmapMismatches));
    }

    private static double max(float[] values) {
        double max = Double.NEGATIVE_INFINITY;
        for (float value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private static double min(float[] values) {
        double min = Double.POSITIVE_INFINITY;
        for (float value : values) {
            min = Math.min(min, value);
        }
        return min;
    }
}
