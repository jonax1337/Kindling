package aquifer;

import net.minecraft.world.level.levelgen.Aquifer;

/**
 * One way to build the aquifer of a chunk. A new implementation is added by writing one class and listing it in
 * Variants; verify and bench then pick it up.
 */
interface Variant {
    String name();

    /** Whether bench runs this variant when --variants is not given. */
    boolean benchmarkedByDefault();

    /** Builds a fresh aquifer from the inputs NoiseChunk hands to Aquifer.Config.create. */
    Aquifer create(AquiferInputs inputs);

    /** What the variant wants printed after a verify or bench run; empty by default. */
    default String diagnostics() {
        return "";
    }
}
