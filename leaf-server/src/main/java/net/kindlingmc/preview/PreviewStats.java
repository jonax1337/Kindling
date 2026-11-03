package net.kindlingmc.preview;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * Server-wide counters of the previews since the server started, for measuring what they cost and what they deliver.
 * Every method may be called from any thread; a {@link LongAdder} keeps recording cheap for the preview workers and the
 * tick thread alike. {@link #snapshot()} reads the counters one after the other, not at one instant, so counters that
 * belong together can differ by the previews in flight.
 */
public final class PreviewStats {
    private static final LongAdder SAVED_READ_TIMEOUTS = new LongAdder();
    private static final LongAdder SENT = new LongAdder();
    private static final LongAdder SENT_RAW_BYTES = new LongAdder();
    private static final LongAdder DROPPED_ON_ARRIVAL = new LongAdder();
    private static final LongAdder FAILED = new LongAdder();
    private static final LongAdder KEPT_ON_CLIENT = new LongAdder();
    private static final GenerationStep.Decoration[] DECORATION_STEPS = GenerationStep.Decoration.values();
    private static final LongAdder[] DECORATION_STEP_COUNTS = adders(DECORATION_STEPS.length);
    private static final LongAdder[] DECORATION_STEP_NANOS = adders(DECORATION_STEPS.length);

    private PreviewStats() {
    }

    /** Where a computed preview came from. */
    public enum Source {
        /** Built from the chunk as saved in the world's region files. */
        SAVED,
        /** Generated from the world seed. */
        SEED,
        /** A seed preview packet that an earlier request had already built. */
        SEED_FROM_CACHE;

        private final LongAdder count = new LongAdder();
        private final LongAdder nanos = new LongAdder();

        private Computed totals() {
            return new Computed(this.count.sum(), this.nanos.sum());
        }
    }

    /**
     * @param count previews computed from one source
     * @param nanos the time computing them took on the worker threads, from the request to the finished packet (for a
     *     seed preview that follows a saved read that timed out or found nothing, the time of that attempt included)
     */
    public record Computed(long count, long nanos) {
    }

    /**
     * The totals at one moment.
     *
     * @param savedReadTimeouts saved chunk reads that did not finish in time, so the seed preview was used
     * @param sent previews sent to players, once per player
     * @param sentRawBytes the uncompressed size of the sent previews ({@link PreviewPacket#rawBytes()})
     * @param droppedOnArrival previews that were computed for a player but no longer wanted when they arrived
     * @param failed previews that could not be computed
     * @param keptOnClient real chunks the client was told to keep as their own preview
     * @param decorationSteps per vanilla decoration step that ran on seed previews, how many previews it ran on and the
     *     time it took there; a part of the {@link #seed} time
     */
    public record Snapshot(Computed saved, Computed seed, Computed seedFromCache, long savedReadTimeouts, long sent, long sentRawBytes,
                           long droppedOnArrival, long failed, long keptOnClient, Map<GenerationStep.Decoration, Computed> decorationSteps) {

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                "computed: saved %s, seed %s, seed from cache %s; saved read timeouts %d; sent %d (%.1f MiB raw), dropped on arrival %d, "
                    + "failed %d; real chunks kept on the client %d; decoration steps %s",
                describe(this.saved), describe(this.seed), describe(this.seedFromCache), this.savedReadTimeouts, this.sent,
                this.sentRawBytes / (double) (1 << 20), this.droppedOnArrival, this.failed, this.keptOnClient, describeSteps());
        }

        private String describeSteps() {
            StringBuilder steps = new StringBuilder();
            for (Map.Entry<GenerationStep.Decoration, Computed> step : this.decorationSteps.entrySet()) {
                steps.append(steps.isEmpty() ? "" : ", ").append(step.getKey().getName()).append(' ').append(describe(step.getValue()));
            }
            return steps.isEmpty() ? "none" : steps.toString();
        }

        private static String describe(final Computed computed) {
            return computed.count + " in " + TimeUnit.NANOSECONDS.toMillis(computed.nanos) + " ms";
        }
    }

    public static void recordComputed(final Source source, final long nanos) {
        source.count.increment();
        source.nanos.add(nanos);
    }

    /** One vanilla decoration step on one seed preview, named by its ordinal in {@link GenerationStep.Decoration}. */
    public static void recordDecorationStep(final int step, final long nanos) {
        DECORATION_STEP_COUNTS[step].increment();
        DECORATION_STEP_NANOS[step].add(nanos);
    }

    public static void recordSavedReadTimeout() {
        SAVED_READ_TIMEOUTS.increment();
    }

    public static void recordSent(final int rawBytes) {
        SENT.increment();
        SENT_RAW_BYTES.add(rawBytes);
    }

    public static void recordDroppedOnArrival() {
        DROPPED_ON_ARRIVAL.increment();
    }

    public static void recordFailed() {
        FAILED.increment();
    }

    public static void recordKeptOnClient() {
        KEPT_ON_CLIENT.increment();
    }

    public static Snapshot snapshot() {
        return new Snapshot(Source.SAVED.totals(), Source.SEED.totals(), Source.SEED_FROM_CACHE.totals(), SAVED_READ_TIMEOUTS.sum(),
            SENT.sum(), SENT_RAW_BYTES.sum(), DROPPED_ON_ARRIVAL.sum(), FAILED.sum(), KEPT_ON_CLIENT.sum(), decorationSteps());
    }

    private static Map<GenerationStep.Decoration, Computed> decorationSteps() {
        Map<GenerationStep.Decoration, Computed> steps = new EnumMap<>(GenerationStep.Decoration.class);
        for (GenerationStep.Decoration step : DECORATION_STEPS) {
            long count = DECORATION_STEP_COUNTS[step.ordinal()].sum();
            if (count > 0) {
                steps.put(step, new Computed(count, DECORATION_STEP_NANOS[step.ordinal()].sum()));
            }
        }
        return Collections.unmodifiableMap(steps);
    }

    private static LongAdder[] adders(final int length) {
        LongAdder[] adders = new LongAdder[length];
        for (int i = 0; i < length; i++) {
            adders[i] = new LongAdder();
        }
        return adders;
    }
}
