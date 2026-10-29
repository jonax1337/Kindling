package net.kindlingmc.spawn;

import com.mojang.logging.LogUtils;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * The check of {@code -Dkindling.verifySpawnPrechecks=true}: for every record entry the replay uses, the main thread
 * computes the same step again with Leaf's own lookups and a copy of the pre-check's random and counts the entries
 * that differ. Expected: none. Costs the main thread what the pre-checks save, so it is for test runs only. Main
 * thread only; reports once a minute.
 */
final class PrecheckVerifier {

    static final boolean ENABLED = Boolean.getBoolean("kindling.verifySpawnPrechecks");

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long REPORT_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final int DIFFERENCES_LOGGED_IN_FULL = 20;

    private static long replays;
    private static long liveReplays;
    private static long picks;
    private static long pickDifferences;
    private static long verdicts;
    private static long verdictDifferences;
    private static long lastReport = System.nanoTime();

    private PrecheckVerifier() {
    }

    static void countReplay(final boolean wentLive) {
        if (!ENABLED) {
            return;
        }
        replays++;
        if (wentLive) {
            liveReplays++;
        }
    }

    static void comparePick(final Pick recorded, final Pick expected, final SpawnTry.Step step) {
        picks++;
        if (!recorded.equals(expected)) {
            pickDifferences++;
            logDifference("pick", recorded, expected, step);
        }
    }

    static void compareVerdict(final boolean recorded, final boolean expected, final SpawnTry.Step step) {
        verdicts++;
        if (recorded != expected) {
            verdictDifferences++;
            logDifference("placement", recorded, expected, step);
        }
    }

    static void reportWhenDue() {
        if (!ENABLED || System.nanoTime() - lastReport < REPORT_INTERVAL_NANOS) {
            return;
        }
        lastReport = System.nanoTime();
        LOGGER.info(
            "Spawn pre-check verification: {} tries replayed ({} finished live), {} picks with {} differences, {} placement checks with {} differences",
            replays, liveReplays, picks, pickDifferences, verdicts, verdictDifferences
        );
    }

    private static void logDifference(final String what, final Object recorded, final Object expected, final SpawnTry.Step step) {
        if (pickDifferences + verdictDifferences <= DIFFERENCES_LOGGED_IN_FULL) {
            LOGGER.warn("Spawn pre-check {} differs at {} (group {}, step {}): recorded {}, main thread {}",
                what, step.pos(), step.group(), step.attempt(), recorded, expected);
        }
    }
}
