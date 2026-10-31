/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package aquifer.candidate;

import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import org.jspecify.annotations.Nullable;

/**
 * C2ME's aquifer optimisation (MixinAquiferSamplerImpl, dev/26.3.0 @2c19de87) on Mojang's 26.3 aquifer, with vanilla's
 * semantics wherever the two differ (DESIGN.md section 2):
 * <ul>
 * <li>T1: cell locations filled up front (filledEagerly) or on first visit (filledLazily), as ints;</li>
 * <li>T2: the ints are the coordinates after vanilla's BlockPos round trip, so the Y wrap at extreme heights stays;</li>
 * <li>T3: the nearest four found with a branch-free min/max insertion on unique int keys
 *     {@code distanceSqr << 4 | tieRank}; unlike C2ME the tie rank follows vanilla's visit order and every key is
 *     inserted, so all four slots match vanilla;</li>
 * <li>T4: the barrier noise kept in a field instead of a MutableDouble per call;</li>
 * <li>T5: computeSubstance split into small methods.</li>
 * </ul>
 * C2ME's flood threshold algebra (T6) and its random reuse (T8) are not exact and are not ported.
 */
public class C2meAquifer extends BaseAquifer {
    /**
     * Vanilla visits x1, then y1, then z1 (A:235-237) and inserts with {@code >=} (A:260-284): on equal distance the
     * later cell ranks first, so it gets the smaller rank.
     */
    protected static final int[] VANILLA_TIE_RANKS = {11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
    private static final int SAMPLED_CELLS = 12;
    private static final int RANK_BITS = 4;
    private static final int RANK_MASK = (1 << RANK_BITS) - 1;
    private static final int UNFILLED = Integer.MIN_VALUE;
    private static final int COORDINATES = 3;

    /** x, y, z per cell; UNFILLED in x until the cell's location is known (no real BlockPos x is MIN_VALUE). */
    private final int[] cellLocations;
    /** By visit order: the cell's tie rank, and its index relative to the lowest sampled cell. */
    private final int[] tieRanks;
    private final int[] offsetOfOrder;
    /** By tie rank: the cell's index relative to the lowest sampled cell. */
    private final int[] offsetOfRank;
    protected int nearestBaseIndex;
    protected int nearestKey1;
    protected int nearestKey2;
    protected int nearestKey3;
    protected int nearestKey4;
    private double barrierNoiseValue = Double.NaN;

    protected C2meAquifer(DensitySamplerSet cachingSamplers, Aquifer.Config config, PositionalRandomFactory positionalRandomFactory,
                          DensityVolume volume, Aquifer.FluidPicker globalFluidPicker, int[] tieRanks) {
        super(cachingSamplers, config, positionalRandomFactory, volume, globalFluidPicker);
        this.cellLocations = new int[this.cellCount() * COORDINATES];
        Arrays.fill(this.cellLocations, UNFILLED);
        this.tieRanks = tieRanks.clone();
        this.offsetOfOrder = new int[SAMPLED_CELLS];
        this.offsetOfRank = new int[SAMPLED_CELLS];
        int order = 0;
        for (int x1 = 0; x1 <= 1; x1++) {
            for (int y1 = 0; y1 <= 2; y1++) {
                for (int z1 = 0; z1 <= 1; z1++) {
                    this.offsetOfOrder[order] = (y1 * this.gridSizeZ + z1) * this.gridSizeX + x1;
                    this.offsetOfRank[this.tieRanks[order]] = this.offsetOfOrder[order];
                    order++;
                }
            }
        }
    }

    /** T1 as in C2ME: every reachable location is computed in the constructor. */
    public static C2meAquifer filledEagerly(DensitySamplerSet cachingSamplers, Aquifer.Config config, PositionalRandomFactory positionalRandomFactory,
                                            DensityVolume volume, Aquifer.FluidPicker globalFluidPicker) {
        C2meAquifer aquifer = new C2meAquifer(cachingSamplers, config, positionalRandomFactory, volume, globalFluidPicker, VANILLA_TIE_RANKS);
        aquifer.fillReachableCells();
        return aquifer;
    }

    /** T1-lazy: locations computed on first visit, as vanilla does; cheaper for the one-column aquifers of getBaseHeight. */
    public static C2meAquifer filledLazily(DensitySamplerSet cachingSamplers, Aquifer.Config config, PositionalRandomFactory positionalRandomFactory,
                                           DensityVolume volume, Aquifer.FluidPicker globalFluidPicker) {
        return new C2meAquifer(cachingSamplers, config, positionalRandomFactory, volume, globalFluidPicker, VANILLA_TIE_RANKS);
    }

    @Override
    public @Nullable BlockState computeSubstance(int blockX, int blockY, int blockZ, double density) {
        if (density > 0.0) {
            this.shouldScheduleFluidUpdate = false;
            return null;
        }
        Aquifer.FluidStatus globalFluid = this.globalFluidPicker.computeFluid(blockX, blockY, blockZ);
        if (blockY > this.skipSamplingAboveY) {
            this.shouldScheduleFluidUpdate = false;
            return globalFluid.at(blockY);
        }
        if (globalFluid.at(blockY).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = false;
            return SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? Blocks.AIR.defaultBlockState() : Blocks.LAVA.defaultBlockState();
        }
        this.selectNearest(blockX, blockY, blockZ);
        return this.substanceOfNearest(blockX, blockY, blockZ, density);
    }

    @Override
    public final int locationX(int index) {
        return this.cellLocations[index * COORDINATES];
    }

    @Override
    public final int locationY(int index) {
        return this.cellLocations[index * COORDINATES + 1];
    }

    @Override
    public final int locationZ(int index) {
        return this.cellLocations[index * COORDINATES + 2];
    }

    /**
     * Only rows up to the one above the skip row can be visited (computeSubstance samples only blockY <= skipSamplingAboveY).
     * Locations are pure functions of the grid position and computing them calls no sampler, so filling early is exact.
     */
    protected final void fillReachableCells() {
        int lastGridY = Math.min(this.minGridY + this.gridSizeY - 1, gridY(this.skipSamplingAboveY + 1) + 1);
        for (int gridY = this.minGridY; gridY <= lastGridY; gridY++) {
            for (int gridZ = this.minGridZ; gridZ < this.minGridZ + this.gridSizeZ; gridZ++) {
                for (int gridX = this.minGridX; gridX < this.minGridX + this.gridSizeX; gridX++) {
                    this.fillCell(this.gridIndex(gridX, gridY, gridZ), gridX, gridY, gridZ);
                }
            }
        }
    }

    /** The twelve candidate cells in vanilla's visit order, each inserted into the sorted four smallest keys. */
    protected final void selectNearest(int blockX, int blockY, int blockZ) {
        int xAnchor = gridX(blockX + -5);
        int yAnchor = gridY(blockY + 1);
        int zAnchor = gridZ(blockZ + -5);
        int baseIndex = this.gridIndex(xAnchor, yAnchor - 1, zAnchor);
        int key1 = Integer.MAX_VALUE;
        int key2 = Integer.MAX_VALUE;
        int key3 = Integer.MAX_VALUE;
        int key4 = Integer.MAX_VALUE;
        int order = 0;
        for (int x1 = 0; x1 <= 1; x1++) {
            for (int y1 = -1; y1 <= 1; y1++) {
                for (int z1 = 0; z1 <= 1; z1++) {
                    int index = baseIndex + this.offsetOfOrder[order];
                    this.ensureFilled(index, xAnchor + x1, yAnchor + y1, zAnchor + z1);
                    int key = this.distanceKey(index, blockX, blockY, blockZ, order++);
                    int carry1 = Math.max(key1, key);
                    key1 = Math.min(key1, key);
                    int carry2 = Math.max(key2, carry1);
                    key2 = Math.min(key2, carry1);
                    int carry3 = Math.max(key3, carry2);
                    key3 = Math.min(key3, carry2);
                    key4 = Math.min(key4, carry3);
                }
            }
        }
        this.nearestBaseIndex = baseIndex;
        this.nearestKey1 = key1;
        this.nearestKey2 = key2;
        this.nearestKey3 = key3;
        this.nearestKey4 = key4;
    }

    protected final int offsetOfOrder(int order) {
        return this.offsetOfOrder[order];
    }

    protected final void ensureFilled(int index, int gridX, int gridY, int gridZ) {
        if (this.cellLocations[index * COORDINATES] == UNFILLED) {
            this.fillCell(index, gridX, gridY, gridZ);
        }
    }

    /**
     * Squared distance with the tie rank in the low bits, so keys are unique and order like vanilla's slots. Even with the
     * Y wrap the distance stays below 2^27, so the shifted key cannot overflow.
     */
    protected final int distanceKey(int index, int blockX, int blockY, int blockZ, int order) {
        int location = index * COORDINATES;
        int dx = this.cellLocations[location] - blockX;
        int dy = this.cellLocations[location + 1] - blockY;
        int dz = this.cellLocations[location + 2] - blockZ;
        return (dx * dx + dy * dy + dz * dz) << RANK_BITS | this.tieRanks[order];
    }

    protected static int distanceOf(int key) {
        return key >>> RANK_BITS;
    }

    protected final int indexOf(int key) {
        return this.nearestBaseIndex + this.offsetOfRank[key & RANK_MASK];
    }

    /** A:289-307, on the four keys selectNearest left. */
    protected final @Nullable BlockState substanceOfNearest(int blockX, int blockY, int blockZ, double density) {
        Aquifer.FluidStatus closestStatus1 = this.aquiferStatus(this.indexOf(this.nearestKey1));
        double similarity12 = similarity(distanceOf(this.nearestKey1), distanceOf(this.nearestKey2));
        BlockState fluidState = closestStatus1.at(blockY);
        BlockState actualFluidState = SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? Blocks.AIR.defaultBlockState() : fluidState;
        if (similarity12 <= 0.0) {
            this.shouldScheduleFluidUpdate = similarity12 >= FLOWING_UPDATE_SIMILARITY
                && !closestStatus1.equals(this.aquiferStatus(this.indexOf(this.nearestKey2)));
            return actualFluidState;
        }
        if (fluidState.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(blockX, blockY - 1, blockZ).at(blockY - 1).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = true;
            return actualFluidState;
        }
        this.forgetBarrierNoise();
        return this.substanceBetweenAquifers(blockX, blockY, blockZ, density, closestStatus1, similarity12, actualFluidState);
    }

    /** A:308-350: the barriers between the nearest aquifers, then the update flag. */
    private @Nullable BlockState substanceBetweenAquifers(int blockX, int blockY, int blockZ, double density, Aquifer.FluidStatus closestStatus1,
                                                          double similarity12, BlockState actualFluidState) {
        int distanceSqr1 = distanceOf(this.nearestKey1);
        int distanceSqr2 = distanceOf(this.nearestKey2);
        int distanceSqr3 = distanceOf(this.nearestKey3);
        Aquifer.FluidStatus closestStatus2 = this.aquiferStatus(this.indexOf(this.nearestKey2));
        double barrier12 = similarity12 * this.pressure(blockX, blockY, blockZ, closestStatus1, closestStatus2);
        if (density + barrier12 > 0.0) {
            this.shouldScheduleFluidUpdate = false;
            return null;
        }
        Aquifer.FluidStatus closestStatus3 = this.aquiferStatus(this.indexOf(this.nearestKey3));
        double similarity13 = similarity(distanceSqr1, distanceSqr3);
        if (similarity13 > 0.0) {
            double barrier13 = similarity12 * similarity13 * this.pressure(blockX, blockY, blockZ, closestStatus1, closestStatus3);
            if (density + barrier13 > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }
        }
        double similarity23 = similarity(distanceSqr2, distanceSqr3);
        if (similarity23 > 0.0) {
            double barrier23 = similarity12 * similarity23 * this.pressure(blockX, blockY, blockZ, closestStatus2, closestStatus3);
            if (density + barrier23 > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }
        }
        boolean mayFlow12 = !closestStatus1.equals(closestStatus2);
        boolean mayFlow23 = similarity23 >= FLOWING_UPDATE_SIMILARITY && !closestStatus2.equals(closestStatus3);
        boolean mayFlow13 = similarity13 >= FLOWING_UPDATE_SIMILARITY && !closestStatus1.equals(closestStatus3);
        if (!mayFlow12 && !mayFlow23 && !mayFlow13) {
            this.shouldScheduleFluidUpdate = similarity13 >= FLOWING_UPDATE_SIMILARITY
                && similarity(distanceSqr1, distanceOf(this.nearestKey4)) >= FLOWING_UPDATE_SIMILARITY
                && !closestStatus1.equals(this.aquiferStatus(this.indexOf(this.nearestKey4)));
        } else {
            this.shouldScheduleFluidUpdate = true;
        }
        return actualFluidState;
    }

    /** A:308: one barrier noise sample is shared by the up to three pressures of one call, never across calls. */
    public final void forgetBarrierNoise() {
        this.barrierNoiseValue = Double.NaN;
    }

    /** A:364-426 with the barrier noise in a field (T4). */
    public final double pressure(int blockX, int blockY, int blockZ, Aquifer.FluidStatus statusClosest1, Aquifer.FluidStatus statusClosest2) {
        if (lavaMeetsWater(statusClosest1.at(blockY), statusClosest2.at(blockY))) {
            return 2.0;
        }
        int fluidYDiff = Math.abs(statusClosest1.fluidLevel() - statusClosest2.fluidLevel());
        if (fluidYDiff == 0) {
            return 0.0;
        }
        double gradient = pressureGradient(blockY, statusClosest1.fluidLevel(), statusClosest2.fluidLevel(), fluidYDiff);
        double noiseValue = gradientNeedsBarrierNoise(gradient) ? this.barrierNoise(blockX, blockY, blockZ) : 0.0;
        return 2.0 * (noiseValue + gradient);
    }

    /** NaN means not sampled yet, as in vanilla; a NaN sample is therefore sampled again, as in vanilla. */
    private double barrierNoise(int blockX, int blockY, int blockZ) {
        if (Double.isNaN(this.barrierNoiseValue)) {
            this.barrierNoiseValue = this.barrierNoise.sampleValue(blockX, blockY, blockZ);
        }
        return this.barrierNoiseValue;
    }

    /** A:247-252, keeping the coordinates vanilla reads back from the packed BlockPos. */
    protected void fillCell(int index, int gridX, int gridY, int gridZ) {
        RandomSource random = this.positionalRandomFactory.at(gridX, gridY, gridZ);
        long location = BlockPos.asLong(
            fromGridX(gridX, random.nextInt(10)),
            fromGridY(gridY, random.nextInt(9)),
            fromGridZ(gridZ, random.nextInt(10))
        );
        this.storeLocation(index, BlockPos.getX(location), BlockPos.getY(location), BlockPos.getZ(location));
    }

    protected final void storeLocation(int index, int x, int y, int z) {
        int offset = index * COORDINATES;
        this.cellLocations[offset] = x;
        this.cellLocations[offset + 1] = y;
        this.cellLocations[offset + 2] = z;
    }
}
