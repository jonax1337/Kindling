package aquifer;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Sanity check of the harness itself: a real NoiseChunk, built by the server's own code, must give the same
 * results and flags as the harness-built VANILLA aquifer. It proves the harness feeds the aquifer the same inputs.
 */
final class RealChunkCheck {
    private RealChunkCheck() {
    }

    /** Compares the FILL phase of each given case; returns the number of differing calls. */
    static long differences(List<ChunkCase> fillCases) {
        long differing = 0;
        for (ChunkCase chunkCase : fillCases) {
            CallList calls = chunkCase.phases().getFirst().calls();
            BlockState[] harnessStates = new BlockState[calls.size];
            boolean[] harnessFlags = new boolean[calls.size];
            try (AquiferRun run = AquiferRun.open(chunkCase.world(), false)) {
                Aquifer aquifer = run.construct(Variants.reference(), chunkCase.volume());
                run.fillDensityCaches(chunkCase.volume());
                Replay.record(aquifer, calls, harnessStates, harnessFlags);
            }
            differing += compareWithServerChunk(chunkCase, calls, harnessStates, harnessFlags);
        }
        return differing;
    }

    private static long compareWithServerChunk(ChunkCase chunkCase, CallList calls, BlockState[] states, boolean[] flags) {
        World world = chunkCase.world();
        long differing = 0;
        try (NoiseChunk noiseChunk = new NoiseChunk(world.randomState(), null, world.settings(), world.fluidPicker(), Blender.empty(), chunkCase.volume())) {
            Aquifer aquifer = noiseChunk.aquifer();
            ScopedDensityBuffer densityBuffer = noiseChunk.cachingSamplers().get(world.finalDensity()).sampleVolume(chunkCase.volume());
            for (int i = 0; i < calls.size; ++i) {
                BlockState state = aquifer.computeSubstance(calls.x[i], calls.y[i], calls.z[i], calls.density[i]);
                if (state != states[i] || aquifer.shouldScheduleFluidUpdate() != flags[i]) {
                    differing++;
                }
            }
            densityBuffer.close();
        }
        return differing;
    }
}
