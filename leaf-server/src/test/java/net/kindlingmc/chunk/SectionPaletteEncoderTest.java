package net.kindlingmc.chunk;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@AllFeatures
class SectionPaletteEncoderTest {

    private static final int BLOCKS_PER_SECTION = 16 * 16 * 16;
    private static final int BIOMES_PER_SECTION = 4 * 4 * 4;

    private final PalettedContainerFactory factory = PalettedContainerFactory.create(RegistryHelper.registryAccess());

    @Test
    void anEmptySectionIsWrittenLikeTheCodec() {
        assertBlockStatesWrittenLikeTheCodec(this.factory.createForBlockStates());
    }

    @Test
    void aSectionWithAFewStatesIsWrittenLikeTheCodec() {
        final List<BlockState> states = allBlockStates();
        assertBlockStatesWrittenLikeTheCodec(blockStates(states.subList(0, 3)));
        assertBlockStatesWrittenLikeTheCodec(blockStates(states.subList(1000, 1040)));
    }

    @Test
    void everyBlockStateIsWrittenLikeTheCodec() {
        final List<BlockState> states = allBlockStates();
        for (int from = 0; from < states.size(); from += BLOCKS_PER_SECTION) {
            assertBlockStatesWrittenLikeTheCodec(blockStates(states.subList(from, Math.min(from + BLOCKS_PER_SECTION, states.size()))));
        }
    }

    @Test
    void everyBiomeIsWrittenLikeTheCodec() {
        final List<Holder<Biome>> biomes = new ArrayList<>(RegistryHelper.registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList());
        for (int from = 0; from < biomes.size(); from += BIOMES_PER_SECTION) {
            assertBiomesWrittenLikeTheCodec(biomes(biomes.subList(from, Math.min(from + BIOMES_PER_SECTION, biomes.size()))));
        }
    }

    private void assertBlockStatesWrittenLikeTheCodec(final PalettedContainer<BlockState> container) {
        final Tag expected = this.factory.blockStatesContainerCodec().encodeStart(NbtOps.INSTANCE, container).getOrThrow();
        assertSameOnDisk(expected, SectionPaletteEncoder.writeBlockStates(container, this.factory.blockStatesStrategy(), SectionPaletteEncoderTest::fellBackToTheCodec));
    }

    private void assertBiomesWrittenLikeTheCodec(final PalettedContainer<Holder<Biome>> container) {
        final Tag expected = this.factory.biomeContainerCodec().encodeStart(NbtOps.INSTANCE, container).getOrThrow();
        assertSameOnDisk(expected, SectionPaletteEncoder.writeBiomes(container, this.factory.biomeStrategy(), SectionPaletteEncoderTest::fellBackToTheCodec));
    }

    /** Equal tags and the same bytes in the region file, key order included. */
    private static void assertSameOnDisk(final Tag expected, final Tag actual) {
        assertEquals(expected, actual);
        assertArrayEquals(bytes(expected), bytes(actual));
    }

    private static byte[] bytes(final Tag tag) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            NbtIo.writeAnyTag(tag, new DataOutputStream(out));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static Tag fellBackToTheCodec() {
        return fail("the encoder fell back to the codec");
    }

    private static List<BlockState> allBlockStates() {
        final List<BlockState> states = new ArrayList<>();
        Block.BLOCK_STATE_REGISTRY.forEach(states::add);
        return states;
    }

    private PalettedContainer<BlockState> blockStates(final List<BlockState> states) {
        final PalettedContainer<BlockState> container = this.factory.createForBlockStates();
        for (int i = 0; i < BLOCKS_PER_SECTION; i++) {
            container.getAndSetUnchecked(i & 15, i >> 8, (i >> 4) & 15, states.get(i % states.size()));
        }
        return container;
    }

    private PalettedContainer<Holder<Biome>> biomes(final List<Holder<Biome>> biomes) {
        final PalettedContainer<Holder<Biome>> container = this.factory.createForBiomes();
        for (int i = 0; i < BIOMES_PER_SECTION; i++) {
            container.getAndSetUnchecked(i & 3, i >> 4, (i >> 2) & 3, biomes.get(i % biomes.size()));
        }
        return container;
    }
}
