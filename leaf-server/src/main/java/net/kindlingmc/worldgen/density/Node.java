package net.kindlingmc.worldgen.density;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/**
 * One node of the corner-level evaluator, built from one of Paper's samplers. Nodes are immutable and shared by every
 * thread; all per-call state lives in the {@link Frame}. The region is the exact volume Paper would pass to that
 * sampler. A node must write Paper's bits at every index the demand marks; what it writes elsewhere is unspecified
 * and never read.
 */
abstract class Node {
    abstract void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand);

    /** The nodes this one evaluates, for the build-time analysis. */
    abstract Node[] children();
}
