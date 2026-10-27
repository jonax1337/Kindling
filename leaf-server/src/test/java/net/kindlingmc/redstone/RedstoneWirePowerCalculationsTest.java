package net.kindlingmc.redstone;

import java.util.List;
import java.util.Random;
import net.caffeinemc.mods.lithium.common.block.redstone.RedstoneWirePowerCalculations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.bukkit.support.environment.VanillaFeature;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@VanillaFeature
class RedstoneWirePowerCalculationsTest {

    private static final RedstoneWireBlock WIRE = (RedstoneWireBlock) Blocks.REDSTONE_WIRE;
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static final int REACH = 2;
    private static final int LEVELS = 4000;

    private static final List<Block> SOURCES_AND_OBSTACLES = List.of(
        Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.GLASS, Blocks.OAK_SLAB,
        Blocks.REDSTONE_BLOCK, Blocks.REDSTONE_WIRE, Blocks.REDSTONE_WIRE, Blocks.REDSTONE_WIRE, Blocks.REDSTONE_WIRE,
        Blocks.REDSTONE_WIRE, Blocks.REPEATER, Blocks.COMPARATOR, Blocks.LEVER, Blocks.STONE_BUTTON, Blocks.REDSTONE_TORCH,
        Blocks.REDSTONE_WALL_TORCH, Blocks.OBSERVER, Blocks.TARGET, Blocks.STONE_PRESSURE_PLATE, Blocks.DETECTOR_RAIL,
        Blocks.DAYLIGHT_DETECTOR, Blocks.TRIPWIRE_HOOK, Blocks.SCULK_SENSOR, Blocks.WATER
    );

    @Test
    void givesTheSamePowerAsVanillaInRandomSurroundings() {
        final Random random = new Random(263);

        for (int i = 0; i < LEVELS; i++) {
            final FakeLevel level = FakeLevel.random(random);

            assertEquals(vanillaTargetStrength(level), RedstoneWirePowerCalculations.getTargetStrength(WIRE, level, CENTER), "level " + i);
        }
    }

    @Test
    void aWireNextToAWireStackedOnAnotherWireStillSeesTheOneBelow() {
        final FakeLevel level = new FakeLevel();
        level.set(CENTER.east(), WIRE.defaultBlockState().setValue(RedstoneWireBlock.POWER, 3));
        level.set(CENTER.east().below(), WIRE.defaultBlockState().setValue(RedstoneWireBlock.POWER, 9));

        assertEquals(vanillaTargetStrength(level), RedstoneWirePowerCalculations.getTargetStrength(WIRE, level, CENTER));
        assertEquals(8, RedstoneWirePowerCalculations.getTargetStrength(WIRE, level, CENTER));
    }

    /**
     * What vanilla's default evaluator does: ask the neighbours with the wires silenced, then look at the wires.
     */
    private static int vanillaTargetStrength(final FakeLevel level) {
        final int blockSignal = vanillaBlockSignal(level);
        return blockSignal == 15 ? blockSignal : Math.max(blockSignal, vanillaIncomingWireSignal(level));
    }

    private static int vanillaBlockSignal(final FakeLevel level) {
        WIRE.shouldSignal = false;
        try {
            return level.getBestNeighborSignal(CENTER);
        } finally {
            WIRE.shouldSignal = true;
        }
    }

    private static int vanillaIncomingWireSignal(final BlockGetter level) {
        int wireSignal = 0;

        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            final BlockPos neighborPos = CENTER.relative(direction);
            final BlockState neighborState = level.getBlockState(neighborPos);
            wireSignal = Math.max(wireSignal, wirePower(neighborState));
            final BlockPos abovePos = CENTER.above();
            if (neighborState.isRedstoneConductor(level, neighborPos) && !level.getBlockState(abovePos).isRedstoneConductor(level, abovePos)) {
                wireSignal = Math.max(wireSignal, wirePower(level.getBlockState(neighborPos.above())));
            } else if (!neighborState.isRedstoneConductor(level, neighborPos)) {
                wireSignal = Math.max(wireSignal, wirePower(level.getBlockState(neighborPos.below())));
            }
        }

        return Math.max(0, wireSignal - 1);
    }

    private static int wirePower(final BlockState state) {
        return state.is(WIRE) ? state.getValue(RedstoneWireBlock.POWER) : 0;
    }

    private static final class FakeLevel implements SignalGetter {

        private final java.util.Map<BlockPos, BlockState> states = new java.util.HashMap<>();

        static FakeLevel random(final Random random) {
            final FakeLevel level = new FakeLevel();
            for (int x = -REACH; x <= REACH; x++) {
                for (int y = -REACH; y <= REACH; y++) {
                    for (int z = -REACH; z <= REACH; z++) {
                        level.set(CENTER.offset(x, y, z), randomState(random));
                    }
                }
            }
            level.set(CENTER, WIRE.defaultBlockState());
            return level;
        }

        private static BlockState randomState(final Random random) {
            final Block block = SOURCES_AND_OBSTACLES.get(random.nextInt(SOURCES_AND_OBSTACLES.size()));
            final List<BlockState> possible = block.getStateDefinition().getPossibleStates();
            return possible.get(random.nextInt(possible.size()));
        }

        void set(final BlockPos pos, final BlockState state) {
            this.states.put(pos.immutable(), state);
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(final BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(final BlockPos pos) {
            return this.states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public @Nullable BlockState getBlockStateIfLoaded(final BlockPos pos) {
            return this.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(final BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public @Nullable FluidState getFluidIfLoaded(final BlockPos pos) {
            return this.getFluidState(pos);
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    }
}
