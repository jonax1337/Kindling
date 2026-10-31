package net.kindlingmc.worldgen.density;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import org.dreeam.leaf.config.modules.opt.CompiledDensity;
import org.slf4j.Logger;

/**
 * Evaluates finalDensity so that range choices, lerps and interval selects inside the cell corners of its interpolated
 * functions compute a branch only where Paper keeps its value. Every kept value comes from Paper's own leaves on the
 * same coordinates, so the output is Paper's bit for bit, and the context's cache cells end up as Paper leaves them
 * (see {@link PendingCacheCell}). Off by default ({@code performance.compiled-density.enabled}); with
 * {@code -Dkindling.verifyDensity=true} every call also runs Paper's sampler, compares and keeps Paper's result.
 */
public final class DensityPruning {
    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final boolean VERIFY = Boolean.getBoolean("kindling.verifyDensity");

    private DensityPruning() {
    }

    /** The sampler RandomState hands out for finalDensity: Paper's own when the setting is off or nothing compiles. */
    public static DensitySampler finalDensity(DensitySampler paper) {
        if (!CompiledDensity.enabled) {
            return paper;
        }
        DensitySampler compiled;
        try {
            compiled = compile(paper, Tuning.DEFAULT);
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            LOGGER.warn("Could not compile the terrain density; world generation evaluates it the vanilla way", failure);
            return paper;
        }
        if (!(compiled instanceof CompiledFinalDensity density)) {
            LOGGER.info("Compiled density: no interpolated function to prune in this world's terrain density, it runs the vanilla way");
            return paper;
        }
        LOGGER.info("Compiled density: {} of {} interpolated functions pruned{}", density.corners, density.interpolated,
            VERIFY ? ", every call verified against vanilla" : "");
        return VERIFY ? new CheckedDensitySampler(density) : density;
    }

    /** The compiled sampler with the given cost model, or paper itself; for tests. */
    static DensitySampler compile(DensitySampler paper, Tuning tuning) {
        return CompiledFinalDensity.compile(paper, tuning);
    }
}
