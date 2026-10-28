package net.kindlingmc.biome;

import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@VanillaFeature
class UniformBiomeCellsTest {

    private static final int MIN_SECTION = -4;
    private static final int BOTTOM_QUART_Y = MIN_SECTION * 4;

    private final Holder<Biome> plains = Holder.direct(null);
    private final Holder<Biome> desert = Holder.direct(null);
    private final Strategy<Holder<Biome>> biomeStrategy = Strategy.createForBiomes(this.biomeIds());

    @Test
    void cellsInOneSingleBiomeSectionShareItsBiome() {
        final LevelChunkSection[] sections = {this.single(this.plains), this.single(this.desert)};

        assertSame(this.plains, UniformBiomeCells.inChunk(sections, MIN_SECTION, 0, BOTTOM_QUART_Y + 2, 2));
    }

    @Test
    void cellsAcrossTwoSectionsShareTheirBiomeWhenBothStoreIt() {
        final LevelChunkSection[] sections = {this.single(this.plains), this.single(this.plains)};

        assertSame(this.plains, UniformBiomeCells.inChunk(sections, MIN_SECTION, 1, BOTTOM_QUART_Y + 3, 1));
    }

    @Test
    void cellsAcrossTwoSectionsWithDifferentBiomesAreNotUniform() {
        final LevelChunkSection[] sections = {this.single(this.plains), this.single(this.desert)};

        assertNull(UniformBiomeCells.inChunk(sections, MIN_SECTION, 1, BOTTOM_QUART_Y + 3, 1));
    }

    @Test
    void aSectionHoldingTwoBiomesIsNotUniform() {
        final LevelChunkSection mixed = this.single(this.plains);
        mixed.setNoiseBiome(3, 3, 3, this.desert);
        final LevelChunkSection[] sections = {mixed};

        assertNull(UniformBiomeCells.inChunk(sections, MIN_SECTION, 0, BOTTOM_QUART_Y, 0));
    }

    @Test
    void cellsReachingIntoTheNextChunkAreNotJudged() {
        final LevelChunkSection[] sections = {this.single(this.plains)};

        assertNull(UniformBiomeCells.inChunk(sections, MIN_SECTION, 3, BOTTOM_QUART_Y, 0));
        assertNull(UniformBiomeCells.inChunk(sections, MIN_SECTION, 0, BOTTOM_QUART_Y, -1));
    }

    @Test
    void rowsOutsideTheChunkReadItsBottomAndTopSection() {
        final LevelChunkSection[] sections = {this.single(this.plains), this.single(this.desert)};
        final int topQuartY = BOTTOM_QUART_Y + sections.length * 4;

        assertSame(this.plains, UniformBiomeCells.inChunk(sections, MIN_SECTION, 0, BOTTOM_QUART_Y - 40, 0));
        assertSame(this.desert, UniformBiomeCells.inChunk(sections, MIN_SECTION, 0, topQuartY + 40, 0));
        assertSame(this.desert, UniformBiomeCells.inChunk(sections, MIN_SECTION, 0, topQuartY - 1, 0));
    }

    private IdMapper<Holder<Biome>> biomeIds() {
        final IdMapper<Holder<Biome>> ids = new IdMapper<>();
        ids.add(this.plains);
        ids.add(this.desert);
        return ids;
    }

    private LevelChunkSection single(final Holder<Biome> biome) {
        final PalettedContainer<BlockState> states = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), null);
        return new LevelChunkSection(states, new PalettedContainer<>(biome, this.biomeStrategy, null));
    }
}
