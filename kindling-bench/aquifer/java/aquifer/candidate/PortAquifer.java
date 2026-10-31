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
import org.apache.commons.lang3.mutable.MutableDouble;
import org.jspecify.annotations.Nullable;

/**
 * Mojang's computeSubstance (A:205-352) line by line on top of BaseAquifer: the lazy long location cache, the
 * branchy nearest-four insertion and a MutableDouble per call. It proves the base class is exact and is the
 * baseline every technique is measured against, so it must stay a plain copy.
 */
public final class PortAquifer extends BaseAquifer {
    private final long[] aquiferLocationCache;

    public PortAquifer(DensitySamplerSet cachingSamplers, Aquifer.Config config, PositionalRandomFactory positionalRandomFactory,
                       DensityVolume volume, Aquifer.FluidPicker globalFluidPicker) {
        super(cachingSamplers, config, positionalRandomFactory, volume, globalFluidPicker);
        this.aquiferLocationCache = new long[this.cellCount()];
        Arrays.fill(this.aquiferLocationCache, Long.MAX_VALUE);
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

        int xAnchor = gridX(blockX + -5);
        int yAnchor = gridY(blockY + 1);
        int zAnchor = gridZ(blockZ + -5);
        int distanceSqr1 = Integer.MAX_VALUE;
        int distanceSqr2 = Integer.MAX_VALUE;
        int distanceSqr3 = Integer.MAX_VALUE;
        int distanceSqr4 = Integer.MAX_VALUE;
        int closestIndex1 = 0;
        int closestIndex2 = 0;
        int closestIndex3 = 0;
        int closestIndex4 = 0;
        for (int x1 = 0; x1 <= 1; x1++) {
            for (int y1 = -1; y1 <= 1; y1++) {
                for (int z1 = 0; z1 <= 1; z1++) {
                    int spacedGridX = xAnchor + x1;
                    int spacedGridY = yAnchor + y1;
                    int spacedGridZ = zAnchor + z1;
                    int index = this.gridIndex(spacedGridX, spacedGridY, spacedGridZ);
                    long existingLocation = this.aquiferLocationCache[index];
                    long location;
                    if (existingLocation != Long.MAX_VALUE) {
                        location = existingLocation;
                    } else {
                        RandomSource random = this.positionalRandomFactory.at(spacedGridX, spacedGridY, spacedGridZ);
                        location = BlockPos.asLong(
                            fromGridX(spacedGridX, random.nextInt(10)),
                            fromGridY(spacedGridY, random.nextInt(9)),
                            fromGridZ(spacedGridZ, random.nextInt(10))
                        );
                        this.aquiferLocationCache[index] = location;
                    }
                    int dx = BlockPos.getX(location) - blockX;
                    int dy = BlockPos.getY(location) - blockY;
                    int dz = BlockPos.getZ(location) - blockZ;
                    int newDistance = dx * dx + dy * dy + dz * dz;
                    if (distanceSqr1 >= newDistance) {
                        closestIndex4 = closestIndex3;
                        closestIndex3 = closestIndex2;
                        closestIndex2 = closestIndex1;
                        closestIndex1 = index;
                        distanceSqr4 = distanceSqr3;
                        distanceSqr3 = distanceSqr2;
                        distanceSqr2 = distanceSqr1;
                        distanceSqr1 = newDistance;
                    } else if (distanceSqr2 >= newDistance) {
                        closestIndex4 = closestIndex3;
                        closestIndex3 = closestIndex2;
                        closestIndex2 = index;
                        distanceSqr4 = distanceSqr3;
                        distanceSqr3 = distanceSqr2;
                        distanceSqr2 = newDistance;
                    } else if (distanceSqr3 >= newDistance) {
                        closestIndex4 = closestIndex3;
                        closestIndex3 = index;
                        distanceSqr4 = distanceSqr3;
                        distanceSqr3 = newDistance;
                    } else if (distanceSqr4 >= newDistance) {
                        closestIndex4 = index;
                        distanceSqr4 = newDistance;
                    }
                }
            }
        }

        Aquifer.FluidStatus closestStatus1 = this.aquiferStatus(closestIndex1);
        double similarity12 = similarity(distanceSqr1, distanceSqr2);
        BlockState fluidState = closestStatus1.at(blockY);
        BlockState actualFluidState = SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? Blocks.AIR.defaultBlockState() : fluidState;
        if (similarity12 <= 0.0) {
            if (similarity12 >= FLOWING_UPDATE_SIMILARITY) {
                Aquifer.FluidStatus closestStatus2 = this.aquiferStatus(closestIndex2);
                this.shouldScheduleFluidUpdate = !closestStatus1.equals(closestStatus2);
            } else {
                this.shouldScheduleFluidUpdate = false;
            }
            return actualFluidState;
        }
        if (fluidState.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(blockX, blockY - 1, blockZ).at(blockY - 1).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = true;
            return actualFluidState;
        }

        MutableDouble barrierNoiseValue = new MutableDouble(Double.NaN);
        Aquifer.FluidStatus closestStatus2 = this.aquiferStatus(closestIndex2);
        double barrier12 = similarity12 * this.calculatePressure(blockX, blockY, blockZ, barrierNoiseValue, closestStatus1, closestStatus2);
        if (density + barrier12 > 0.0) {
            this.shouldScheduleFluidUpdate = false;
            return null;
        }

        Aquifer.FluidStatus closestStatus3 = this.aquiferStatus(closestIndex3);
        double similarity13 = similarity(distanceSqr1, distanceSqr3);
        if (similarity13 > 0.0) {
            double barrier13 = similarity12
                * similarity13
                * this.calculatePressure(blockX, blockY, blockZ, barrierNoiseValue, closestStatus1, closestStatus3);
            if (density + barrier13 > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }
        }

        double similarity23 = similarity(distanceSqr2, distanceSqr3);
        if (similarity23 > 0.0) {
            double barrier23 = similarity12
                * similarity23
                * this.calculatePressure(blockX, blockY, blockZ, barrierNoiseValue, closestStatus2, closestStatus3);
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
                && similarity(distanceSqr1, distanceSqr4) >= FLOWING_UPDATE_SIMILARITY
                && !closestStatus1.equals(this.aquiferStatus(closestIndex4));
        } else {
            this.shouldScheduleFluidUpdate = true;
        }
        return actualFluidState;
    }

    @Override
    protected int locationX(int index) {
        return BlockPos.getX(this.aquiferLocationCache[index]);
    }

    @Override
    protected int locationY(int index) {
        return BlockPos.getY(this.aquiferLocationCache[index]);
    }

    @Override
    protected int locationZ(int index) {
        return BlockPos.getZ(this.aquiferLocationCache[index]);
    }

    /** A:364-426. */
    private double calculatePressure(int blockX, int blockY, int blockZ, MutableDouble barrierNoiseValue,
                                     Aquifer.FluidStatus statusClosest1, Aquifer.FluidStatus statusClosest2) {
        if (lavaMeetsWater(statusClosest1.at(blockY), statusClosest2.at(blockY))) {
            return 2.0;
        }
        int fluidYDiff = Math.abs(statusClosest1.fluidLevel() - statusClosest2.fluidLevel());
        if (fluidYDiff == 0) {
            return 0.0;
        }
        double gradient = pressureGradient(blockY, statusClosest1.fluidLevel(), statusClosest2.fluidLevel(), fluidYDiff);
        double noiseValue;
        if (gradientNeedsBarrierNoise(gradient)) {
            double currentNoiseValue = barrierNoiseValue.doubleValue();
            if (Double.isNaN(currentNoiseValue)) {
                double barrierNoise = this.barrierNoise.sampleValue(blockX, blockY, blockZ);
                barrierNoiseValue.setValue(barrierNoise);
                noiseValue = barrierNoise;
            } else {
                noiseValue = currentNoiseValue;
            }
        } else {
            noiseValue = 0.0;
        }
        return 2.0 * (noiseValue + gradient);
    }
}
