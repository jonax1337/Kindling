package net.kindlingmc.biome;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.SplittableRandom;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import org.dreeam.leaf.config.modules.opt.BiomeZoomShortcut;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Leaf's pruned biome zoom, with and without Kindling's shortcut, against a copy of vanilla's: the same cell for every
 * (seed, position) pair.
 */
class BiomeZoomParityTest {

    private static final int PAIRS = 10_000_000;
    private static final int MAX_HORIZONTAL = 30_000_000;
    private static final int MAX_VERTICAL = 2_048;

    /** One biome per corner: in every 2x2x2 block of cells each one appears exactly once. */
    private static final Holder<Biome>[] CORNER_BIOMES = newBiomes(8);
    private static final Holder<Biome>[] SECTION_BIOMES = newBiomes(2);

    /** Any eight neighbouring cells hold eight different biomes, so the biome names the corner. */
    private static final BiomeResolver CORNER_CELLS = (quartX, quartY, quartZ) -> CORNER_BIOMES[cornerOf(quartX, quartY, quartZ)];

    private final boolean shortcutDefault = BiomeZoomShortcut.enabled;

    @AfterEach
    void restoreShortcut() {
        BiomeZoomShortcut.enabled = this.shortcutDefault;
    }

    @Test
    void knownCounterexamplePicksVanillasCorner() {
        final long seed = -8055847622726226809L;
        final int x = -20104644;
        final int y = 265;
        final int z = -14313980;
        assertEquals(6, VanillaBiomeZoom.nearestCorner(seed, x, y, z));

        final Holder<Biome> biome = new BiomeManager(CORNER_CELLS, seed).getBiome(x, y, z);

        assertSame(VanillaBiomeZoom.getBiome(seed, CORNER_CELLS, x, y, z), biome);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void picksVanillasCornerWhenAllCellsDiffer(final boolean shortcut) {
        BiomeZoomShortcut.enabled = shortcut;

        assertMatchesVanilla(CORNER_CELLS, 1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void matchesVanillaInUniformAndMixedSections(final boolean shortcut) {
        BiomeZoomShortcut.enabled = shortcut;
        final SectionWorld world = new SectionWorld();

        assertMatchesVanilla(world, 2L);

        if (shortcut) {
            assertTrue(world.uniformAnswers > PAIRS / 10, "the shortcut answered " + world.uniformAnswers + " times");
        } else {
            assertEquals(0, world.uniformAnswers);
        }
    }

    private static void assertMatchesVanilla(final BiomeResolver cells, final long randomSeed) {
        final SplittableRandom random = new SplittableRandom(randomSeed);
        for (int pair = 0; pair < PAIRS; pair++) {
            final long seed = random.nextLong();
            final int x = random.nextInt(-MAX_HORIZONTAL, MAX_HORIZONTAL);
            final int y = random.nextInt(-MAX_VERTICAL, MAX_VERTICAL);
            final int z = random.nextInt(-MAX_HORIZONTAL, MAX_HORIZONTAL);
            final Holder<Biome> expected = VanillaBiomeZoom.getBiome(seed, cells, x, y, z);
            final Holder<Biome> actual = new BiomeManager(cells, seed).getBiome(x, y, z);
            if (expected != actual) {
                throw new AssertionError("zoom seed " + seed + " at " + x + ", " + y + ", " + z + ": vanilla corner "
                    + VanillaBiomeZoom.nearestCorner(seed, x, y, z));
            }
        }
    }

    private static int cornerOf(final int quartX, final int quartY, final int quartZ) {
        return (quartX & 1) << 2 | (quartY & 1) << 1 | quartZ & 1;
    }

    @SuppressWarnings("unchecked")
    private static Holder<Biome>[] newBiomes(final int count) {
        final Holder<Biome>[] biomes = new Holder[count];
        for (int i = 0; i < count; i++) {
            biomes[i] = Holder.direct(null);
        }
        return biomes;
    }

    /**
     * Sections of 4x4x4 cells, as in a chunk: two in three store one of two biomes, the others hold a corner biome in each
     * cell. It answers like a level: through the chunk column a lookup finds.
     */
    private static final class SectionWorld implements BiomeResolver {
        private static final int SECTION_KINDS = 3;

        private int uniformAnswers;

        @Override
        public Holder<Biome> getNoiseBiome(final int quartX, final int quartY, final int quartZ) {
            final Holder<Biome> single = this.singleBiome(quartX >> 2, quartY >> 2, quartZ >> 2);
            return single != null ? single : CORNER_BIOMES[cornerOf(quartX, quartY, quartZ)];
        }

        @Override
        public BiomeResolver chunkResolver(final int quartX, final int quartZ) {
            return new ChunkColumn(this, quartX >> 2, quartZ >> 2);
        }

        private Holder<Biome> singleBiome(final int sectionX, final int sectionY, final int sectionZ) {
            final long hash = HashCommon.mix(sectionX * 0x9E3779B97F4A7C15L ^ sectionY * 0xC2B2AE3D27D4EB4FL ^ sectionZ * 0x165667B19E3779F9L);
            final int kind = (int) Long.remainderUnsigned(hash, SECTION_KINDS);
            return kind < SECTION_BIOMES.length ? SECTION_BIOMES[kind] : null;
        }
    }

    /**
     * One chunk column of a {@link SectionWorld}; fails when asked for a cell outside it.
     */
    private record ChunkColumn(SectionWorld world, int chunkX, int chunkZ) implements BiomeResolver {

        @Override
        public Holder<Biome> getNoiseBiome(final int quartX, final int quartY, final int quartZ) {
            if (quartX >> 2 != this.chunkX || quartZ >> 2 != this.chunkZ) {
                throw new AssertionError("cell " + quartX + ", " + quartZ + " read from chunk " + this.chunkX + ", " + this.chunkZ);
            }
            return this.world.getNoiseBiome(quartX, quartY, quartZ);
        }

        @Override
        public Holder<Biome> getUniformNoiseBiome(final int quartX, final int quartY, final int quartZ) {
            if (!UniformBiomeCells.shareChunkColumn(quartX, quartZ)) {
                return null;
            }
            final Holder<Biome> lower = this.world.singleBiome(this.chunkX, quartY >> 2, this.chunkZ);
            if (lower == null || lower != this.world.singleBiome(this.chunkX, (quartY + 1) >> 2, this.chunkZ)) {
                return null;
            }
            this.world.uniformAnswers++;
            return lower;
        }
    }
}
