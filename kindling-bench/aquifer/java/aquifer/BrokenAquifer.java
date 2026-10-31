package aquifer;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla's aquifer with exactly one deliberate defect. The self-test builds one per defect and the verifier must
 * reject every one; this proves each kind of check can fail. Most defects fire once, a few calls into the replay.
 */
final class BrokenAquifer implements Aquifer {
    /** The defects; traceOnly ones change no result, so only the trace check can see them. */
    enum Defect {
        /** One non-null result is replaced by another block state. */
        SWAPPED_STATE(false),
        /** One non-null result becomes null. */
        NULL_SWALLOWED(false),
        /** The update flag is inverted once. */
        FLAG_FLIPPED(false),
        /** The flag is not refreshed once and keeps the previous call's value. */
        FLAG_STALE(false),
        /** Density 0.0 is passed on as a tiny positive value, so the call counts as solid. */
        ZERO_DENSITY_AS_SOLID(false),
        /** A cache that never refreshes: every 4x4x4 block region answers with its first result. */
        CELL_MEMO(false),
        /** One extra barrier noise sample, which changes the shared sampler context's call sequence only. */
        EXTRA_SAMPLER_CALL(true),
        /** One extra fluid picker call. */
        EXTRA_PICKER_CALL(true);

        private final boolean traceOnly;

        Defect(boolean traceOnly) {
            this.traceOnly = traceOnly;
        }

        boolean traceOnly() {
            return this.traceOnly;
        }
    }

    private static final int ARMED_AFTER_CALLS = 7;

    private final Aquifer real;
    private final AquiferInputs inputs;
    private final Defect defect;
    private final Map<Long, BlockState> memo = new HashMap<>();
    private int calls;
    private boolean fired;
    private boolean reportedFlag;

    BrokenAquifer(Aquifer real, AquiferInputs inputs, Defect defect) {
        this.real = real;
        this.inputs = inputs;
        this.defect = defect;
    }

    @Override
    public @Nullable BlockState computeSubstance(int blockX, int blockY, int blockZ, double density) {
        this.calls++;
        double passed = this.defect == Defect.ZERO_DENSITY_AS_SOLID && density == 0.0 ? Double.MIN_VALUE : density;
        BlockState state = this.real.computeSubstance(blockX, blockY, blockZ, passed);
        boolean flag = this.real.shouldScheduleFluidUpdate();
        this.reportedFlag = flag;
        return switch (this.defect) {
            case SWAPPED_STATE -> this.fireOnce(state != null) ? other(state) : state;
            case NULL_SWALLOWED -> this.fireOnce(state != null) ? null : state;
            case FLAG_FLIPPED -> {
                this.reportedFlag = this.fireOnce(state != null) ? !flag : flag;
                yield state;
            }
            case FLAG_STALE -> {
                this.reportedFlag = this.fireOnce(flag) ? !flag : flag;
                yield state;
            }
            case CELL_MEMO -> this.memo.computeIfAbsent(regionKey(blockX, blockY, blockZ), key -> state);
            case EXTRA_SAMPLER_CALL -> {
                if (this.fireOnce(true)) {
                    this.inputs.samplers().get(this.inputs.config().barrierNoise()).sampleValue(blockX, blockY, blockZ);
                }
                yield state;
            }
            case EXTRA_PICKER_CALL -> {
                if (this.fireOnce(true)) {
                    this.inputs.picker().computeFluid(blockX, blockY, blockZ);
                }
                yield state;
            }
            case ZERO_DENSITY_AS_SOLID -> state;
        };
    }

    @Override
    public boolean shouldScheduleFluidUpdate() {
        return this.reportedFlag;
    }

    /** True exactly once: at the first call past the arming point where the defect can show. */
    private boolean fireOnce(boolean applicable) {
        if (this.fired || this.calls <= ARMED_AFTER_CALLS || !applicable) {
            return false;
        }
        this.fired = true;
        return true;
    }

    private static BlockState other(BlockState state) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        return state == stone ? Blocks.DIRT.defaultBlockState() : stone;
    }

    private static long regionKey(int blockX, int blockY, int blockZ) {
        return TracingSamplerSet.pack(blockX >> 2, blockZ >> 2) ^ ((long)(blockY >> 2) << 48);
    }
}
