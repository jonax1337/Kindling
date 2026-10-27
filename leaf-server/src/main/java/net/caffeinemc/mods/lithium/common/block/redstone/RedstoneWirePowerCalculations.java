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
 */
public final class RedstoneWirePowerCalculations {

    private RedstoneWirePowerCalculations() {
    }

    /**
     * The power a wire at the given position receives from the blocks around it, from other
     * components as well as from other wires.
     */
    public static int getTargetStrength(final RedstoneWireBlock wireBlock, final BlockGetter level, final BlockPos pos) {
        int signal = Redstone.SIGNAL_MIN;

        for (final Direction direction : DirectionConstants.VERTICAL) {
            signal = Math.max(signal, getSignalFromVertical(wireBlock, level, pos.relative(direction), direction));
            if (signal >= Redstone.SIGNAL_MAX) {
                return Redstone.SIGNAL_MAX;
            }
        }

        final BlockPos above = pos.above();
        final boolean wiresAboveConnect = !level.getBlockState(above).isRedstoneConductor(level, above);

        for (final Direction direction : DirectionConstants.HORIZONTAL) {
            signal = Math.max(signal, getSignalFromSide(wireBlock, level, pos.relative(direction), direction, wiresAboveConnect));
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
        final RedstoneWireBlock wireBlock, final BlockGetter level, final BlockPos pos, final Direction toDirection
    ) {
        final BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(wireBlock)) {
            return Redstone.SIGNAL_MIN;
        }

        final int signal = state.getSignal(level, pos, toDirection);
        if (signal >= Redstone.SIGNAL_MAX || !state.isRedstoneConductor(level, pos)) {
            return signal;
        }

        return Math.max(signal, getDirectSignalTo(wireBlock, level, pos, toDirection.getOpposite()));
    }

    private static int getSignalFromSide(
        final RedstoneWireBlock wireBlock, final BlockGetter level, final BlockPos pos, final Direction toDirection, final boolean wiresAboveConnect
    ) {
        final BlockState state = level.getBlockState(pos);
        if (state.is(wireBlock)) {
            return Math.max(getWireSignal(state), getWireSignalAt(wireBlock, level, pos.below()));
        }

        final int signal = state.getSignal(level, pos, toDirection);
        if (signal >= Redstone.SIGNAL_MAX) {
            return Redstone.SIGNAL_MAX;
        }

        if (!state.isRedstoneConductor(level, pos)) {
            return Math.max(signal, getWireSignalAt(wireBlock, level, pos.below()));
        }

        final int poweredSignal = Math.max(signal, getDirectSignalTo(wireBlock, level, pos, toDirection.getOpposite()));
        if (poweredSignal >= Redstone.SIGNAL_MAX || !wiresAboveConnect) {
            return poweredSignal;
        }

        return Math.max(poweredSignal, getWireSignalAt(wireBlock, level, pos.above()));
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
        final RedstoneWireBlock wireBlock, final BlockGetter level, final BlockPos pos, final Direction ignored
    ) {
        int signal = Redstone.SIGNAL_MIN;

        for (final Direction direction : DirectionConstants.ALL) {
            if (direction == ignored) {
                continue;
            }

            final BlockPos side = pos.relative(direction);
            final BlockState neighbor = level.getBlockState(side);
            if (neighbor.isAir() || neighbor.is(wireBlock)) {
                continue;
            }

            signal = Math.max(signal, neighbor.getDirectSignal(level, side, direction));
            if (signal >= Redstone.SIGNAL_MAX) {
                return Redstone.SIGNAL_MAX;
            }
        }

        return signal;
    }
}
