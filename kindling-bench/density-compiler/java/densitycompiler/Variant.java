package densitycompiler;

import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;

/**
 * One way to evaluate a world's finalDensity over a chunk volume. A new evaluator is added by writing one class
 * and listing it in Variants; verify and bench then pick it up.
 */
interface Variant {
    String name();

    /** False runs the call with a context without Paper's per-id volume caches. */
    boolean cachesEnabled();

    /** Whether bench runs this variant when --variants is not given. */
    boolean benchmarkedByDefault();

    /** A sampler for one world. Called once per world and thread, so the result may keep per-thread state. */
    DensitySampler sampler(World world);

    /** What the variant wants printed after a verify or bench run (pruning counters); empty by default. */
    default String diagnostics() {
        return "";
    }
}
