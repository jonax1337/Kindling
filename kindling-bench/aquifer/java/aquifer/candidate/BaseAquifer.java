package aquifer.candidate;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import org.jspecify.annotations.Nullable;

/**
 * What every candidate shares with Mojang's Aquifer.NoiseBasedAquifer (26.3, A = Aquifer.java): the constructor
 * (A:130-163), the per-cell fluid status (A:452-571) and the pressure gradient (A:375-405), copied expression by
 * expression. Subclasses add how aquifer cell locations are stored and found, and the hot computeSubstance.
 * The status path is protected only so the self-test's broken variants can change it; nothing in the bench overrides it.
 */
public abstract class BaseAquifer implements Aquifer {
    protected static final double FLOWING_UPDATE_SIMILARITY = similarity(Mth.square(10), Mth.square(12));
    private static final int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS = new int[][]{
        {0, 0}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {-3, 0}, {-2, 0}, {-1, 0}, {1, 0}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}
    };

    protected final DensitySampler.Bound barrierNoise;
    protected final DensitySampler.Bound fluidLevelFloodednessNoise;
    protected final DensitySampler.Bound fluidLevelSpreadNoise;
    protected final DensitySampler.Bound lavaNoise;
    protected final DensitySampler.Bound exclusion;
    protected final DensitySampler.Bound surfaceLevel;
    protected final PositionalRandomFactory positionalRandomFactory;
    protected final Aquifer.FluidPicker globalFluidPicker;
    protected final int minGridX;
    protected final int minGridY;
    protected final int minGridZ;
    protected final int gridSizeX;
    protected final int gridSizeY;
    protected final int gridSizeZ;
    protected final int skipSamplingAboveY;
    protected boolean shouldScheduleFluidUpdate;
    private final Aquifer.@Nullable FluidStatus[] aquiferCache;
    private final Long2IntMap surfaceLevelCache = new Long2IntOpenHashMap();

    protected BaseAquifer(DensitySamplerSet cachingSamplers, Aquifer.Config config, PositionalRandomFactory positionalRandomFactory,
                          DensityVolume volume, Aquifer.FluidPicker globalFluidPicker) {
        this.barrierNoise = cachingSamplers.get(config.barrierNoise());
        this.fluidLevelFloodednessNoise = cachingSamplers.get(config.fluidLevelFloodednessNoise());
        this.fluidLevelSpreadNoise = cachingSamplers.get(config.fluidLevelSpreadNoise());
        this.lavaNoise = cachingSamplers.get(config.lavaNoise());
        this.exclusion = cachingSamplers.get(config.exclusion());
        this.surfaceLevel = cachingSamplers.get(config.surfaceLevel());
        this.positionalRandomFactory = positionalRandomFactory;
        this.minGridX = gridX(volume.minBlockX() + -5) + 0;
        this.globalFluidPicker = globalFluidPicker;
        int maxGridX = gridX(volume.maxBlockX() + -5) + 1;
        this.gridSizeX = maxGridX - this.minGridX + 1;
        this.minGridY = gridY(volume.minBlockY() + 1) + -1;
        int maxGridY = gridY(volume.maxBlockY() + 1) + 1;
        this.gridSizeY = maxGridY - this.minGridY + 1;
        this.minGridZ = gridZ(volume.minBlockZ() + -5) + 0;
        int maxGridZ = gridZ(volume.maxBlockZ() + -5) + 1;
        this.gridSizeZ = maxGridZ - this.minGridZ + 1;
        this.aquiferCache = new Aquifer.FluidStatus[this.cellCount()];
        int maxAdjustedSurfaceLevel = adjustSurfaceLevel(
            this.maxSurfaceLevel(fromGridX(this.minGridX, 0), fromGridZ(this.minGridZ, 0), fromGridX(maxGridX, 9), fromGridZ(maxGridZ, 9))
        );
        int skipSamplingAboveGridY = gridY(maxAdjustedSurfaceLevel + 12) - -1;
        this.skipSamplingAboveY = fromGridY(skipSamplingAboveGridY, 11) - 1;
    }

    @Override
    public boolean shouldScheduleFluidUpdate() {
        return this.shouldScheduleFluidUpdate;
    }

    protected final int cellCount() {
        return this.gridSizeX * this.gridSizeY * this.gridSizeZ;
    }

    protected final int gridIndex(int gridX, int gridY, int gridZ) {
        int x = gridX - this.minGridX;
        int y = gridY - this.minGridY;
        int z = gridZ - this.minGridZ;
        return (y * this.gridSizeZ + z) * this.gridSizeX + x;
    }

    /** The cached status of an aquifer cell whose location is already known (A:452-462). */
    protected final Aquifer.FluidStatus aquiferStatus(int index) {
        Aquifer.FluidStatus oldStatus = this.aquiferCache[index];
        if (oldStatus != null) {
            return oldStatus;
        }
        Aquifer.FluidStatus status = this.computeFluid(this.locationX(index), this.locationY(index), this.locationZ(index));
        this.aquiferCache[index] = status;
        return status;
    }

    protected abstract int locationX(int index);

    protected abstract int locationY(int index);

    protected abstract int locationZ(int index);

    public static double similarity(int distanceSqr1, int distanceSqr2) {
        return 1.0 - (distanceSqr2 - distanceSqr1) / 25.0;
    }

    /** A:374: the pair where a pressure of 2.0 always separates the two fluids. */
    public static boolean lavaMeetsWater(BlockState type1, BlockState type2) {
        return type1.is(Blocks.LAVA) && type2.is(Blocks.WATER) || type1.is(Blocks.WATER) && type2.is(Blocks.LAVA);
    }

    /** A:380-405, for two fluid levels fluidYDiff apart (not 0). */
    public static double pressureGradient(int blockY, int fluidLevel1, int fluidLevel2, int fluidYDiff) {
        double averageFluidY = 0.5 * (fluidLevel1 + fluidLevel2);
        double howFarAboveAverageFluidPoint = blockY + 0.5 - averageFluidY;
        double baseValue = fluidYDiff / 2.0;
        double distanceFromBarrierEdgeTowardsMiddle = baseValue - Math.abs(howFarAboveAverageFluidPoint);
        if (howFarAboveAverageFluidPoint > 0.0) {
            double centerPoint = 0.0 + distanceFromBarrierEdgeTowardsMiddle;
            return centerPoint > 0.0 ? centerPoint / 1.5 : centerPoint / 2.5;
        }
        double centerPoint = 3.0 + distanceFromBarrierEdgeTowardsMiddle;
        return centerPoint > 0.0 ? centerPoint / 3.0 : centerPoint / 10.0;
    }

    /** A:409: outside [-2, 2] the barrier noise is not sampled. */
    public static boolean gradientNeedsBarrierNoise(double gradient) {
        return !(gradient < -2.0) && !(gradient > 2.0);
    }

    protected static int adjustSurfaceLevel(int preliminarySurfaceLevel) {
        return preliminarySurfaceLevel + 8;
    }

    protected static int gridX(int blockCoord) {
        return blockCoord >> 4;
    }

    protected static int fromGridX(int gridCoord, int blockOffset) {
        return (gridCoord << 4) + blockOffset;
    }

    protected static int gridY(int blockCoord) {
        return Math.floorDiv(blockCoord, 12);
    }

    protected static int fromGridY(int gridCoord, int blockOffset) {
        return gridCoord * 12 + blockOffset;
    }

    protected static int gridZ(int blockCoord) {
        return blockCoord >> 4;
    }

    protected static int fromGridZ(int gridCoord, int blockOffset) {
        return (gridCoord << 4) + blockOffset;
    }

    /** A:542-554. */
    protected int computeRandomizedFluidSurfaceLevel(int x, int y, int z, int lowestSurfaceLevel) {
        int fluidLevelCellX = Math.floorDiv(x, 16);
        int fluidLevelCellY = Math.floorDiv(y, 40);
        int fluidLevelCellZ = Math.floorDiv(z, 16);
        int fluidCellMiddleY = fluidLevelCellY * 40 + 20;
        double fluidLevelSpread = this.fluidLevelSpreadNoise.sampleValue(fluidLevelCellX, fluidLevelCellY, fluidLevelCellZ) * 10.0F;
        int fluidLevelSpreadQuantized = Mth.quantize(fluidLevelSpread, 3);
        int targetFluidSurfaceLevel = fluidCellMiddleY + fluidLevelSpreadQuantized;
        return Math.min(lowestSurfaceLevel, targetFluidSurfaceLevel);
    }

    /** A:506-540. */
    protected int computeSurfaceLevel(int x, int y, int z, Aquifer.FluidStatus globalFluid, int lowestSurfaceLevel,
                                      boolean surfaceAtCenterIsUnderGlobalFluidLevel) {
        double partiallyFloodedness;
        double fullyFloodidness;
        if (this.exclusion.sampleValue(x, y, z) > 0.0) {
            partiallyFloodedness = -1.0;
            fullyFloodidness = -1.0;
        } else {
            int distanceBelowSurface = adjustSurfaceLevel(lowestSurfaceLevel) - y;
            double floodednessFactor = surfaceAtCenterIsUnderGlobalFluidLevel ? Mth.clampedMap(distanceBelowSurface, 0.0, 64.0, 1.0, 0.0) : 0.0;
            double floodednessNoiseValue = Mth.clamp(this.fluidLevelFloodednessNoise.sampleValue(x, y, z), -1.0, 1.0);
            double fullyFloodedThreshold = Mth.map(floodednessFactor, 1.0, 0.0, -0.3, 0.8);
            double partiallyFloodedThreshold = Mth.map(floodednessFactor, 1.0, 0.0, -0.8, 0.4);
            partiallyFloodedness = floodednessNoiseValue - partiallyFloodedThreshold;
            fullyFloodidness = floodednessNoiseValue - fullyFloodedThreshold;
        }
        return this.fluidSurfaceLevel(x, y, z, globalFluid, lowestSurfaceLevel, partiallyFloodedness, fullyFloodidness);
    }

    /** A:530-539. */
    protected final int fluidSurfaceLevel(int x, int y, int z, Aquifer.FluidStatus globalFluid, int lowestSurfaceLevel,
                                          double partiallyFloodedness, double fullyFloodidness) {
        if (fullyFloodidness > 0.0) {
            return globalFluid.fluidLevel();
        }
        if (partiallyFloodedness > 0.0) {
            return this.computeRandomizedFluidSurfaceLevel(x, y, z, lowestSurfaceLevel);
        }
        return DimensionType.WAY_BELOW_MIN_Y;
    }

    /** A:165-170. */
    private int surfaceLevel(int blockX, int blockZ) {
        int quantizedX = QuartPos.toBlock(QuartPos.fromBlock(blockX));
        int quantizedZ = QuartPos.toBlock(QuartPos.fromBlock(blockZ));
        return this.surfaceLevelCache
            .computeIfAbsent(ChunkPos.pack(quantizedX, quantizedZ), k -> Mth.floor(this.surfaceLevel.sampleValue(quantizedX, 0, quantizedZ)));
    }

    /** A:172-196. */
    private int maxSurfaceLevel(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        int minQuartX = QuartPos.fromBlock(minBlockX);
        int maxQuartX = QuartPos.fromBlock(maxBlockX);
        int minQuartZ = QuartPos.fromBlock(minBlockZ);
        int maxQuartZ = QuartPos.fromBlock(maxBlockZ);
        DensityVolume volume = new DensityVolume(
            maxQuartX - minQuartX + 1, 1, maxQuartZ - minQuartZ + 1, QuartPos.toBlock(minQuartX), 0, QuartPos.toBlock(minQuartZ), 4, 1, 4
        );
        try (ScopedDensityBuffer buffer = this.surfaceLevel.sampleVolume(volume)) {
            int maxY = Integer.MIN_VALUE;
            for (int z = 0; z < volume.sizeZ(); z++) {
                for (int x = 0; x < volume.sizeX(); x++) {
                    int surfaceLevel = Mth.floor(buffer.get(volume.indexUnchecked(x, 0, z)));
                    this.surfaceLevelCache.put(ChunkPos.pack(volume.blockX(x), volume.blockZ(z)), surfaceLevel);
                    if (surfaceLevel > maxY) {
                        maxY = surfaceLevel;
                    }
                }
            }
            return maxY;
        }
    }

    /** A:464-500. */
    private Aquifer.FluidStatus computeFluid(int x, int y, int z) {
        Aquifer.FluidStatus globalFluid = this.globalFluidPicker.computeFluid(x, y, z);
        int lowestPreliminarySurface = Integer.MAX_VALUE;
        int topOfAquiferCell = y + 12;
        int bottomOfAquiferCell = y - 12;
        boolean surfaceAtCenterIsUnderGlobalFluidLevel = false;
        for (int[] offset : SURFACE_SAMPLING_OFFSETS_IN_CHUNKS) {
            int sampleX = x + SectionPos.sectionToBlockCoord(offset[0]);
            int sampleZ = z + SectionPos.sectionToBlockCoord(offset[1]);
            int surfaceLevel = this.surfaceLevel(sampleX, sampleZ);
            int adjustedSurfaceLevel = adjustSurfaceLevel(surfaceLevel);
            boolean start = offset[0] == 0 && offset[1] == 0;
            if (start && bottomOfAquiferCell > adjustedSurfaceLevel) {
                return globalFluid;
            }
            boolean topOfAquiferCellPokesAboveSurface = topOfAquiferCell > adjustedSurfaceLevel;
            if (topOfAquiferCellPokesAboveSurface || start) {
                Aquifer.FluidStatus globalFluidAtSurface = this.globalFluidPicker.computeFluid(sampleX, adjustedSurfaceLevel, sampleZ);
                if (!globalFluidAtSurface.at(adjustedSurfaceLevel).isAir()) {
                    if (start) {
                        surfaceAtCenterIsUnderGlobalFluidLevel = true;
                    }
                    if (topOfAquiferCellPokesAboveSurface) {
                        return globalFluidAtSurface;
                    }
                }
            }
            lowestPreliminarySurface = Math.min(lowestPreliminarySurface, surfaceLevel);
        }
        int fluidSurfaceLevel = this.computeSurfaceLevel(x, y, z, globalFluid, lowestPreliminarySurface, surfaceAtCenterIsUnderGlobalFluidLevel);
        return new Aquifer.FluidStatus(fluidSurfaceLevel, this.computeFluidType(x, y, z, globalFluid, fluidSurfaceLevel));
    }

    /** A:556-571. */
    public BlockState computeFluidType(int x, int y, int z, Aquifer.FluidStatus globalFluid, int fluidSurfaceLevel) {
        BlockState fluidType = globalFluid.fluidType();
        if (fluidSurfaceLevel <= -10 && fluidSurfaceLevel != DimensionType.WAY_BELOW_MIN_Y && globalFluid.fluidType() != Blocks.LAVA.defaultBlockState()) {
            int fluidTypeCellX = Math.floorDiv(x, 64);
            int fluidTypeCellY = Math.floorDiv(y, 40);
            int fluidTypeCellZ = Math.floorDiv(z, 64);
            double lavaNoiseValue = this.lavaNoise.sampleValue(fluidTypeCellX, fluidTypeCellY, fluidTypeCellZ);
            if (Math.abs(lavaNoiseValue) > 0.3) {
                fluidType = Blocks.LAVA.defaultBlockState();
            }
        }
        return fluidType;
    }
}
