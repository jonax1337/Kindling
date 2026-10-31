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

package aquifer.broken;

import aquifer.candidate.C2meAquifer;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import org.jspecify.annotations.Nullable;

/**
 * The C2ME candidate with deliberate deviations, for the self-test only. Some defects are C2ME's own code as written
 * (MixinAquiferSamplerImpl, dev/26.3.0 @2c19de87); together they are C2ME's semantics on 26.3. Speed is irrelevant here,
 * so the defects are plain runtime checks.
 */
public final class BrokenC2meAquifer extends C2meAquifer {
    public enum Defect {
        /** {@code >} instead of vanilla's {@code >=}: the earlier cell wins a distance tie. */
        TIE_OLDER_WINS,
        /** C2ME's tie ranks: visit order y, z, x with the later cell winning (M:305-355). */
        C2ME_TIEBREAK,
        /** C2ME inserts a key only when it is not above the third slot (M:328, M:342), so the fourth can stay stale. */
        C2ME_FOURTH_SLOT,
        /** The barrier noise is not forgotten between calls. */
        STALE_BARRIER,
        /** The first barrier exit (A:311-313) leaves the update flag of the previous call. */
        FLAG_KEPT,
        /** Status 3 is looked up before status 2: same blocks, a different sampler call order. */
        STATUS3_FIRST,
        /** {@code * 10.0} instead of the float multiply {@code * 10.0F} (A:550). */
        SPREAD_DOUBLE,
        /** C2ME's flood thresholds (M:389-392): vanilla's algebra, rounded differently. */
        C2ME_FLOOD_ALGEBRA,
        /** Cell locations stored as computed, without the round trip through the packed BlockPos (it wraps Y outside [-2048, 2047]). */
        NO_BLOCKPOS_WRAP,
        /** {@code * 0.04} instead of vanilla's {@code / 25.0}: a different rounding of the same quotient (A:361). */
        SIMILARITY_MUL,
        /** {@code < -10} instead of vanilla's {@code <= -10} for the level the lava noise is consulted at (A:560). */
        LAVA_LEVEL_LT
    }

    private static final double SIMILARITY_FACTOR = 0.04;
    private static final int LAVA_LEVEL = -10;

    private static final int[] OLDER_WINS_RANKS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    /** By vanilla order x1*6 + (y1+1)*2 + z1: C2ME's rank 11 - ((y1+1)*4 + z1*2 + x1). */
    private static final int[] C2ME_RANKS = {11, 9, 7, 5, 3, 1, 10, 8, 6, 4, 2, 0};

    private final Set<Defect> defects;

    private BrokenC2meAquifer(Set<Defect> defects, DensitySamplerSet cachingSamplers, Aquifer.Config config,
                              PositionalRandomFactory positionalRandomFactory, DensityVolume volume, Aquifer.FluidPicker globalFluidPicker) {
        super(cachingSamplers, config, positionalRandomFactory, volume, globalFluidPicker, ranksFor(defects));
        this.defects = EnumSet.copyOf(defects);
    }

    public static BrokenC2meAquifer create(Set<Defect> defects, DensitySamplerSet cachingSamplers, Aquifer.Config config,
                                           PositionalRandomFactory positionalRandomFactory, DensityVolume volume,
                                           Aquifer.FluidPicker globalFluidPicker) {
        BrokenC2meAquifer aquifer = new BrokenC2meAquifer(defects, cachingSamplers, config, positionalRandomFactory, volume, globalFluidPicker);
        aquifer.fillReachableCells();
        return aquifer;
    }

    /** C2ME's semantics as written, as far as they differ from vanilla 26.3. */
    public static Set<Defect> c2meAsWritten() {
        return EnumSet.of(Defect.C2ME_TIEBREAK, Defect.C2ME_FOURTH_SLOT, Defect.C2ME_FLOOD_ALGEBRA);
    }

    private static int[] ranksFor(Set<Defect> defects) {
        if (defects.contains(Defect.C2ME_TIEBREAK)) {
            return C2ME_RANKS;
        }
        return defects.contains(Defect.TIE_OLDER_WINS) ? OLDER_WINS_RANKS : VANILLA_TIE_RANKS;
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
        if (this.defects.contains(Defect.C2ME_FOURTH_SLOT)) {
            this.selectNearestAsC2me(blockX, blockY, blockZ);
        } else {
            this.selectNearest(blockX, blockY, blockZ);
        }
        return this.substance(blockX, blockY, blockZ, density);
    }

    /** The similarity SIMILARITY_MUL computes; public so the part check can run it against Mojang's. */
    public static double similarityMultiplied(int distanceSqr1, int distanceSqr2) {
        return 1.0 - (distanceSqr2 - distanceSqr1) * SIMILARITY_FACTOR;
    }

    private double similarityOf(int distanceSqr1, int distanceSqr2) {
        return this.defects.contains(Defect.SIMILARITY_MUL) ? similarityMultiplied(distanceSqr1, distanceSqr2) : similarity(distanceSqr1, distanceSqr2);
    }

    @Override
    protected void fillCell(int index, int gridX, int gridY, int gridZ) {
        if (!this.defects.contains(Defect.NO_BLOCKPOS_WRAP)) {
            super.fillCell(index, gridX, gridY, gridZ);
            return;
        }
        RandomSource random = this.positionalRandomFactory.at(gridX, gridY, gridZ);
        this.storeLocation(index, fromGridX(gridX, random.nextInt(10)), fromGridY(gridY, random.nextInt(9)), fromGridZ(gridZ, random.nextInt(10)));
    }

    @Override
    public BlockState computeFluidType(int x, int y, int z, Aquifer.FluidStatus globalFluid, int fluidSurfaceLevel) {
        if (!this.defects.contains(Defect.LAVA_LEVEL_LT)) {
            return super.computeFluidType(x, y, z, globalFluid, fluidSurfaceLevel);
        }
        BlockState fluidType = globalFluid.fluidType();
        if (fluidSurfaceLevel < LAVA_LEVEL && fluidSurfaceLevel != DimensionType.WAY_BELOW_MIN_Y && globalFluid.fluidType() != Blocks.LAVA.defaultBlockState()
            && Math.abs(this.lavaNoise.sampleValue(Math.floorDiv(x, 64), Math.floorDiv(y, 40), Math.floorDiv(z, 64))) > 0.3) {
            fluidType = Blocks.LAVA.defaultBlockState();
        }
        return fluidType;
    }

    @Override
    protected int computeRandomizedFluidSurfaceLevel(int x, int y, int z, int lowestSurfaceLevel) {
        if (!this.defects.contains(Defect.SPREAD_DOUBLE)) {
            return super.computeRandomizedFluidSurfaceLevel(x, y, z, lowestSurfaceLevel);
        }
        int fluidLevelCellX = Math.floorDiv(x, 16);
        int fluidLevelCellY = Math.floorDiv(y, 40);
        int fluidLevelCellZ = Math.floorDiv(z, 16);
        int fluidCellMiddleY = fluidLevelCellY * 40 + 20;
        double fluidLevelSpread = this.fluidLevelSpreadNoise.sampleValue(fluidLevelCellX, fluidLevelCellY, fluidLevelCellZ) * 10.0;
        int targetFluidSurfaceLevel = fluidCellMiddleY + Mth.quantize(fluidLevelSpread, 3);
        return Math.min(lowestSurfaceLevel, targetFluidSurfaceLevel);
    }

    @Override
    protected int computeSurfaceLevel(int x, int y, int z, Aquifer.FluidStatus globalFluid, int lowestSurfaceLevel,
                                      boolean surfaceAtCenterIsUnderGlobalFluidLevel) {
        if (!this.defects.contains(Defect.C2ME_FLOOD_ALGEBRA)) {
            return super.computeSurfaceLevel(x, y, z, globalFluid, lowestSurfaceLevel, surfaceAtCenterIsUnderGlobalFluidLevel);
        }
        double partiallyFloodedness;
        double fullyFloodidness;
        if (this.exclusion.sampleValue(x, y, z) > 0.0) {
            partiallyFloodedness = -1.0;
            fullyFloodidness = -1.0;
        } else {
            int distanceBelowSurface = adjustSurfaceLevel(lowestSurfaceLevel) - y;
            double f = surfaceAtCenterIsUnderGlobalFluidLevel ? Mth.clampedLerp((double) distanceBelowSurface / 64.0, 1.0, 0.0) : 0.0;
            double g = Mth.clamp(this.fluidLevelFloodednessNoise.sampleValue(x, y, z), -1.0, 1.0);
            partiallyFloodedness = g + 0.8 + (f - 1.0) * 1.2;
            fullyFloodidness = g + 0.3 + (f - 1.0) * 1.1;
        }
        return this.fluidSurfaceLevel(x, y, z, globalFluid, lowestSurfaceLevel, partiallyFloodedness, fullyFloodidness);
    }

    /** M:327-353 on vanilla-ranked keys: a key above the third slot is dropped even when it is below the fourth. */
    private void selectNearestAsC2me(int blockX, int blockY, int blockZ) {
        int xAnchor = gridX(blockX + -5);
        int yAnchor = gridY(blockY + 1);
        int zAnchor = gridZ(blockZ + -5);
        int baseIndex = this.gridIndex(xAnchor, yAnchor - 1, zAnchor);
        int a = Integer.MAX_VALUE;
        int b = Integer.MAX_VALUE;
        int c = Integer.MAX_VALUE;
        int d = Integer.MAX_VALUE;
        int order = 0;
        for (int x1 = 0; x1 <= 1; x1++) {
            for (int y1 = -1; y1 <= 1; y1++) {
                for (int z1 = 0; z1 <= 1; z1++) {
                    int index = baseIndex + this.offsetOfOrder(order);
                    this.ensureFilled(index, xAnchor + x1, yAnchor + y1, zAnchor + z1);
                    int p = this.distanceKey(index, blockX, blockY, blockZ, order++);
                    if (p <= c) {
                        int n1 = Math.max(a, p);
                        a = Math.min(a, p);
                        int n2 = Math.max(b, n1);
                        b = Math.min(b, n1);
                        int n3 = Math.max(c, n2);
                        c = Math.min(c, n2);
                        d = Math.min(d, n3);
                    }
                }
            }
        }
        this.nearestBaseIndex = baseIndex;
        this.nearestKey1 = a;
        this.nearestKey2 = b;
        this.nearestKey3 = c;
        this.nearestKey4 = d;
    }

    /** substanceOfNearest with the tail defects switchable. */
    private @Nullable BlockState substance(int blockX, int blockY, int blockZ, double density) {
        int distanceSqr1 = distanceOf(this.nearestKey1);
        int distanceSqr2 = distanceOf(this.nearestKey2);
        int distanceSqr3 = distanceOf(this.nearestKey3);
        Aquifer.FluidStatus closestStatus1 = this.aquiferStatus(this.indexOf(this.nearestKey1));
        double similarity12 = this.similarityOf(distanceSqr1, distanceSqr2);
        BlockState fluidState = closestStatus1.at(blockY);
        if (similarity12 <= 0.0) {
            this.shouldScheduleFluidUpdate = similarity12 >= FLOWING_UPDATE_SIMILARITY
                && !closestStatus1.equals(this.aquiferStatus(this.indexOf(this.nearestKey2)));
            return fluidState;
        }
        if (fluidState.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(blockX, blockY - 1, blockZ).at(blockY - 1).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = true;
            return fluidState;
        }
        if (!this.defects.contains(Defect.STALE_BARRIER)) {
            this.forgetBarrierNoise();
        }
        if (this.defects.contains(Defect.STATUS3_FIRST)) {
            this.aquiferStatus(this.indexOf(this.nearestKey3));
        }
        Aquifer.FluidStatus closestStatus2 = this.aquiferStatus(this.indexOf(this.nearestKey2));
        double barrier12 = similarity12 * this.pressure(blockX, blockY, blockZ, closestStatus1, closestStatus2);
        if (density + barrier12 > 0.0) {
            if (!this.defects.contains(Defect.FLAG_KEPT)) {
                this.shouldScheduleFluidUpdate = false;
            }
            return null;
        }
        Aquifer.FluidStatus closestStatus3 = this.aquiferStatus(this.indexOf(this.nearestKey3));
        double similarity13 = this.similarityOf(distanceSqr1, distanceSqr3);
        if (similarity13 > 0.0 && density + similarity12 * similarity13 * this.pressure(blockX, blockY, blockZ, closestStatus1, closestStatus3) > 0.0) {
            this.shouldScheduleFluidUpdate = false;
            return null;
        }
        double similarity23 = this.similarityOf(distanceSqr2, distanceSqr3);
        if (similarity23 > 0.0 && density + similarity12 * similarity23 * this.pressure(blockX, blockY, blockZ, closestStatus2, closestStatus3) > 0.0) {
            this.shouldScheduleFluidUpdate = false;
            return null;
        }
        boolean mayFlow12 = !closestStatus1.equals(closestStatus2);
        boolean mayFlow23 = similarity23 >= FLOWING_UPDATE_SIMILARITY && !closestStatus2.equals(closestStatus3);
        boolean mayFlow13 = similarity13 >= FLOWING_UPDATE_SIMILARITY && !closestStatus1.equals(closestStatus3);
        if (!mayFlow12 && !mayFlow23 && !mayFlow13) {
            this.shouldScheduleFluidUpdate = similarity13 >= FLOWING_UPDATE_SIMILARITY
                && this.similarityOf(distanceSqr1, distanceOf(this.nearestKey4)) >= FLOWING_UPDATE_SIMILARITY
                && !closestStatus1.equals(this.aquiferStatus(this.indexOf(this.nearestKey4)));
        } else {
            this.shouldScheduleFluidUpdate = true;
        }
        return fluidState;
    }
}
