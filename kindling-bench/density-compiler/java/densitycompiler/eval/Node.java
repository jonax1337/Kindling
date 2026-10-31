package densitycompiler.eval;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/**
 * One node of the corner-level evaluator, built from one of Paper's samplers. Region is the exact volume Paper would
 * pass to that sampler. A node must write Paper's bits at every index the demand marks; what it writes elsewhere is
 * unspecified and never read.
 */
abstract class Node {
    private final String label;

    Node(String label) {
        this.label = label;
    }

    abstract void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand);

    final String label() {
        return this.label;
    }
}
