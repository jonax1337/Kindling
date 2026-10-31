package aquifer;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;

/** Plays a CallList on an aquifer. The two loops differ only in what they keep. */
final class Replay {
    private Replay() {
    }

    /** Keeps the state and the update flag after every call. */
    static void record(Aquifer aquifer, CallList calls, BlockState[] states, boolean[] flags) {
        for (int i = 0; i < calls.size; ++i) {
            states[i] = aquifer.computeSubstance(calls.x[i], calls.y[i], calls.z[i], calls.density[i]);
            flags[i] = aquifer.shouldScheduleFluidUpdate();
        }
    }

    /** Reads the flag after every call, as the checked version does, and folds both into a number so the work stays. */
    static int run(Aquifer aquifer, CallList calls) {
        return run(aquifer, calls, calls.size);
    }

    /** The first count calls only: a column that getBaseHeight leaves at the first block its tester accepts. */
    static int run(Aquifer aquifer, CallList calls, int count) {
        int sink = 0;
        for (int i = 0; i < count; ++i) {
            BlockState state = aquifer.computeSubstance(calls.x[i], calls.y[i], calls.z[i], calls.density[i]);
            sink += (state == null ? 0 : 1) + (aquifer.shouldScheduleFluidUpdate() ? 2 : 0);
        }
        return sink;
    }
}
