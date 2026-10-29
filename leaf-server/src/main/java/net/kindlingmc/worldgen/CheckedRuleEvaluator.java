package net.kindlingmc.worldgen;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Evaluates every block with both the compiled and vanilla's evaluator, logs where they differ and keeps vanilla's
 * result. Turned on with {@code -Dkindling.verifyMaterialRules=true}, to check the compiler on a real world.
 */
final class CheckedRuleEvaluator implements RuleEvaluator {
    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final long DIFFERENCES_LOGGED = 20;
    private static final long SUMMARY_EVERY_BLOCKS = 50_000_000;
    private static final AtomicLong CHECKED = new AtomicLong();
    private static final AtomicLong DIFFERENCES = new AtomicLong();

    private final RuleEvaluator compiled;
    private final RuleEvaluator vanilla;

    CheckedRuleEvaluator(RuleEvaluator compiled, RuleEvaluator vanilla) {
        this.compiled = compiled;
        this.vanilla = vanilla;
    }

    @Override
    public @Nullable BlockState tryApply(int blockX, int blockY, int blockZ) {
        BlockState compiledState = this.compiled.tryApply(blockX, blockY, blockZ);
        BlockState vanillaState = this.vanilla.tryApply(blockX, blockY, blockZ);
        if (compiledState != vanillaState) {
            reportDifference(blockX, blockY, blockZ, compiledState, vanillaState);
        }
        reportProgress();
        return vanillaState;
    }

    private static void reportDifference(int blockX, int blockY, int blockZ, @Nullable BlockState compiled, @Nullable BlockState vanilla) {
        if (DIFFERENCES.incrementAndGet() <= DIFFERENCES_LOGGED) {
            LOGGER.warn("Material rules differ at {} {} {}: compiled {}, vanilla {}", blockX, blockY, blockZ, compiled, vanilla);
        }
    }

    private static void reportProgress() {
        long checked = CHECKED.incrementAndGet();
        if (checked % SUMMARY_EVERY_BLOCKS == 0) {
            LOGGER.info("Material rule check: {} blocks, {} differences", checked, DIFFERENCES.get());
        }
    }
}
