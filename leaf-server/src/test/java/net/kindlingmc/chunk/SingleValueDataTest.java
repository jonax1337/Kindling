package net.kindlingmc.chunk;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.bukkit.support.environment.VanillaFeature;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@VanillaFeature
class SingleValueDataTest {

    private static final int BLOCKS = 4096;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

    private final Strategy<BlockState> strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);

    @Test
    void containersOfOneValueShareTheirData() {
        final PalettedContainer<BlockState> first = new PalettedContainer<>(AIR, this.strategy, null);
        final PalettedContainer<BlockState> second = new PalettedContainer<>(AIR, this.strategy, null);

        assertSame(first.data, second.data);
        assertTrue(this.strategy.kindling$singleValueData.isShared(first.data));
        assertNotSame(first.data, new PalettedContainer<>(STONE, this.strategy, null).data);
    }

    @Test
    void settingAnotherValueMovesOnlyThatContainerToDataOfItsOwn() {
        final PalettedContainer<BlockState> written = new PalettedContainer<>(AIR, this.strategy, null);
        final PalettedContainer<BlockState> untouched = new PalettedContainer<>(AIR, this.strategy, null);

        written.set(0, 0, 0, STONE);

        assertFalse(this.strategy.kindling$singleValueData.isShared(written.data));
        assertSame(STONE, written.get(0, 0, 0));
        assertSame(AIR, written.get(1, 0, 0));
        assertAll(untouched, AIR);
        assertAll(new PalettedContainer<>(AIR, this.strategy, null), AIR);
    }

    @Test
    void settingTheSameValueKeepsTheSharedData() {
        final PalettedContainer<BlockState> container = new PalettedContainer<>(AIR, this.strategy, null);
        final PalettedContainer.Data<BlockState> shared = container.data;

        assertSame(AIR, container.getAndSet(3, 4, 5, AIR));

        assertSame(shared, container.data);
    }

    @Test
    void readingIntoASharedContainerNeverChangesTheSharedData() {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        new PalettedContainer<>(STONE, this.strategy, null).write(buffer);
        final PalettedContainer<BlockState> target = new PalettedContainer<>(AIR, this.strategy, null);

        target.read(buffer);

        assertAll(target, STONE);
        assertAll(new PalettedContainer<>(AIR, this.strategy, null), AIR);
    }

    @Test
    void copiesReadTheSameValues() {
        final PalettedContainer<BlockState> copy = new PalettedContainer<>(AIR, this.strategy, null).copy();

        copy.set(7, 7, 7, STONE);

        assertSame(STONE, copy.get(7, 7, 7));
        assertAll(new PalettedContainer<>(AIR, this.strategy, null), AIR);
    }

    @Test
    void copiesAndRecreationsKeepTheSharedData() {
        final PalettedContainer<BlockState> container = new PalettedContainer<>(AIR, this.strategy, null);

        assertSame(container.data, container.copy().data);
        assertSame(container.data, container.recreate().data);
    }

    @Test
    void getAndSetOfAnotherValueReturnsTheOldValueAndLeavesTheOthers() {
        final PalettedContainer<BlockState> written = new PalettedContainer<>(AIR, this.strategy, null);

        assertSame(AIR, written.getAndSet(2, 2, 2, STONE));

        assertSame(STONE, written.get(2, 2, 2));
        assertAll(new PalettedContainer<>(AIR, this.strategy, null), AIR);
    }

    @Test
    void aLoadedSingleValuePaletteIsShared() {
        final PalettedContainer<BlockState> loaded = unpack(List.of(STONE), null);

        assertSame(new PalettedContainer<>(STONE, this.strategy, null).data, loaded.data);
        assertAll(loaded, STONE);
    }

    @Test
    void aLoadedEmptyPaletteTakesTheOriginalPath() {
        final PalettedContainer<BlockState> loaded = unpack(List.of(), null);

        assertFalse(this.strategy.kindling$singleValueData.isShared(loaded.data));
        assertEquals(0, loaded.data.palette().getSize());
    }

    @Test
    void containersWithAntiXrayPresetsNeverShare() {
        final BlockState[] presets = {Blocks.DEEPSLATE.defaultBlockState(), Blocks.DIAMOND_ORE.defaultBlockState()};

        final PalettedContainer<BlockState> created = new PalettedContainer<>(AIR, this.strategy, presets);
        final PalettedContainer<BlockState> loaded = unpack(List.of(AIR), presets);

        for (final PalettedContainer<BlockState> container : List.of(created, loaded)) {
            assertFalse(this.strategy.kindling$singleValueData.isShared(container.data));
            container.set(0, 0, 0, STONE);
            assertTrue(container.data.palette().maybeHas(presets[0]::equals), "the first resize adds the presets");
            assertTrue(container.data.palette().maybeHas(presets[1]::equals), "the first resize adds the presets");
            assertSame(AIR, container.get(1));
        }
    }

    private PalettedContainer<BlockState> unpack(final List<BlockState> palette, final BlockState @Nullable [] presets) {
        final PalettedContainerRO.PackedData<BlockState> packed =
            new PalettedContainerRO.PackedData<>(palette, Optional.empty(), PalettedContainerRO.PackedData.UNKNOWN_BITS_PER_ENTRY);
        return PalettedContainer.unpack(this.strategy, packed, AIR, presets).getOrThrow();
    }

    private static void assertAll(final PalettedContainer<BlockState> container, final BlockState expected) {
        for (int index = 0; index < BLOCKS; index++) {
            assertSame(expected, container.get(index), "block " + index);
        }
        assertEquals(1, container.data.palette().getSize());
    }
}
