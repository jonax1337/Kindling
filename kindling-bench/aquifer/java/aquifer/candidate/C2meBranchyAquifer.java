package aquifer.candidate;

import net.minecraft.SharedConstants;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import org.jspecify.annotations.Nullable;

/**
 * The C2ME candidate with vanilla's branchy insertion (A:260-284) instead of the min/max network: everything but T3,
 * so the difference to C2meAquifer is what the branch-free selection is worth. On unique keys {@code key < slot}
 * is vanilla's {@code distance >= newDistance}, because a later cell always has the smaller tie rank.
 */
public final class C2meBranchyAquifer extends C2meAquifer {
    private C2meBranchyAquifer(DensitySamplerSet cachingSamplers, Aquifer.Config config, PositionalRandomFactory positionalRandomFactory,
                               DensityVolume volume, Aquifer.FluidPicker globalFluidPicker) {
        super(cachingSamplers, config, positionalRandomFactory, volume, globalFluidPicker, VANILLA_TIE_RANKS);
    }

    public static C2meBranchyAquifer filledEagerly(DensitySamplerSet cachingSamplers, Aquifer.Config config,
                                                   PositionalRandomFactory positionalRandomFactory, DensityVolume volume,
                                                   Aquifer.FluidPicker globalFluidPicker) {
        C2meBranchyAquifer aquifer = new C2meBranchyAquifer(cachingSamplers, config, positionalRandomFactory, volume, globalFluidPicker);
        aquifer.fillReachableCells();
        return aquifer;
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
        this.selectNearestByInsertion(blockX, blockY, blockZ);
        return this.substanceOfNearest(blockX, blockY, blockZ, density);
    }

    private void selectNearestByInsertion(int blockX, int blockY, int blockZ) {
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
                    int index = baseIndex + this.offsetOfOrder(order);
                    this.ensureFilled(index, xAnchor + x1, yAnchor + y1, zAnchor + z1);
                    int key = this.distanceKey(index, blockX, blockY, blockZ, order++);
                    if (key < key1) {
                        key4 = key3;
                        key3 = key2;
                        key2 = key1;
                        key1 = key;
                    } else if (key < key2) {
                        key4 = key3;
                        key3 = key2;
                        key2 = key;
                    } else if (key < key3) {
                        key4 = key3;
                        key3 = key;
                    } else if (key < key4) {
                        key4 = key;
                    }
                }
            }
        }
        this.nearestBaseIndex = baseIndex;
        this.nearestKey1 = key1;
        this.nearestKey2 = key2;
        this.nearestKey3 = key3;
        this.nearestKey4 = key4;
    }
}
