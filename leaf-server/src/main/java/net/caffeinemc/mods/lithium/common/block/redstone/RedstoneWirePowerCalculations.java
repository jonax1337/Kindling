/*
 * This file is part of Lithium
 *
 * Lithium is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Lithium is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Lithium. If not, see <https://www.gnu.org/licenses/>.
 */

package net.caffeinemc.mods.lithium.common.block.redstone;

import net.caffeinemc.mods.lithium.common.util.DirectionConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Redstone;

/**
 * Power calculation of a redstone wire that gives the same result as vanilla's
 * {@code DefaultRedstoneWireEvaluator.calculateTargetStrength}, but looks at every
 * neighbouring block once, never asks a wire for its signal (vanilla silences wires with a
 * flag while it asks) and stops as soon as the maximum is reached.
 * <p>
 * Ported from Lithium's {@code RedstoneWirePowerCalculations} (Space Walker), reduced to the
 * combined calculation of the default evaluator; every block access goes through the given
 * getter, so Paper's and Leaf's block state lookups stay in effect.
 * <p>
 * A wire is evaluated again for every update that reaches it, so the neighbours are addressed
 * through two mutable positions instead of a new {@link BlockPos} per look: {@code side} for the
 * block next to the wire, {@code around} for the blocks next to that one. Both are only passed
 * to block state lookups and signal queries, which read the position and do not keep it.
 */
public final class RedstoneWirePowerCalculations {

    private RedstoneWirePowerCalculations() {
    }

    /**
     * The power a wire at the given position receives from the blocks around it, from other
     * components as well as from other wires.
     */
    public static int getTargetStrength(final RedstoneWireBlock wireBlock, final BlockGetter level, final BlockPos pos) {
        final BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos around = new BlockPos.MutableBlockPos();
        int signal = Redstone.SIGNAL_MIN;

        for (final Direction direction : DirectionConstants.VERTICAL) {
            side.setWithOffset(pos, direction);
            signal = Math.max(signal, getSignalFromVertical(wireBlock, level, side, direction, around));
            if (signal >= Redstone.SIGNAL_MAX) {
                return Redstone.SIGNAL_MAX;
            }
        }

        side.setWithOffset(pos, Direction.UP);
        final boolean wiresAboveConnect = !level.getBlockState(side).isRedstoneConductor(level, side);

        for (final Direction direction : DirectionConstants.HORIZONTAL) {
            side.setWithOffset(pos, direction);
            signal = Math.max(signal, getSignalFromSide(wireBlock, level, side, direction, wiresAboveConnect, around));
            if (signal >= Redstone.SIGNAL_MAX) {
                return Redstone.SIGNAL_MAX;
            }
        }

        return signal;
    }

    /**
     * Wires do not connect to wires directly above or below them, and air gives no power.
     */
    private static int getSignalFromVertical(
        final RedstoneWireBlock wireBlock, final BlockGetter level, final BlockPos pos, final Direction toDirection,
        final BlockPos.MutableBlockPos around
    ) {
        final BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(wireBlock)) {
            return Redstone.SIGNAL_MIN;
        }

        final int signal = state.getSignal(level, pos, toDirection);
        if (signal >= Redstone.SIGNAL_MAX || !state.isRedstoneConductor(level, pos)) {
            return signal;
        }

        return Math.max(signal, getDirectSignalTo(wireBlock, level, pos, toDirection.getOpposite(), around));
    }

    private static int getSignalFromSide(
        final RedstoneWireBlock wireBlock, final BlockGetter level, final BlockPos pos, final Direction toDirection, final boolean wiresAboveConnect,
        final BlockPos.MutableBlockPos around
    ) {
        final BlockState state = level.getBlockState(pos);
        if (state.is(wireBlock)) {
            return Math.max(getWireSignal(state), getWireSignalAt(wireBlock, level, around.setWithOffset(pos, Direction.DOWN)));
        }

        final int signal = state.getSignal(level, pos, toDirection);
        if (signal >= Redstone.SIGNAL_MAX) {
            return Redstone.SIGNAL_MAX;
        }

        if (!state.isRedstoneConductor(level, pos)) {
            return Math.max(signal, getWireSignalAt(wireBlock, level, around.setWithOffset(pos, Direction.DOWN)));
        }

        final int poweredSignal = Math.max(signal, getDirectSignalTo(wireBlock, level, pos, toDirection.getOpposite(), around));
        if (poweredSignal >= Redstone.SIGNAL_MAX || !wiresAboveConnect) {
            return poweredSignal;
        }

        return Math.max(poweredSignal, getWireSignalAt(wireBlock, level, around.setWithOffset(pos, Direction.UP)));
    }

    private static int getWireSignalAt(final RedstoneWireBlock wireBlock, final BlockGetter level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        return state.is(wireBlock) ? getWireSignal(state) : Redstone.SIGNAL_MIN;
    }

    /**
     * What a wire passes on to the next wire: its own power minus one.
     */
    private static int getWireSignal(final BlockState wireState) {
        return Math.max(Redstone.SIGNAL_MIN, wireState.getValue(RedstoneWireBlock.POWER) - 1);
    }

    /**
     * The strong power a block receives from the blocks around it, except from the direction
     * the wire asking is in.
     */
    private static int getDirectSignalTo(
        final RedstoneWireBlock wireBlock, final BlockGetter level, final BlockPos pos, final Direction ignored,
        final BlockPos.MutableBlockPos around
    ) {
        int signal = Redstone.SIGNAL_MIN;

        for (final Direction direction : DirectionConstants.ALL) {
            if (direction == ignored) {
                continue;
            }

            around.setWithOffset(pos, direction);
            final BlockState neighbor = level.getBlockState(around);
            if (neighbor.isAir() || neighbor.is(wireBlock)) {
                continue;
            }

            signal = Math.max(signal, neighbor.getDirectSignal(level, around, direction));
            if (signal >= Redstone.SIGNAL_MAX) {
                return Redstone.SIGNAL_MAX;
            }
        }

        return signal;
    }
}
