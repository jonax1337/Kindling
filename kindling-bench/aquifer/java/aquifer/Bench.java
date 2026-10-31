package aquifer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;

/**
 * Interleaved benchmark. Each round runs the whole workload once per variant, the variants in a rotating order so
 * drift cancels. Per chunk the aquifer's construction and the fill replay are timed separately; finalDensity runs
 * between them, untimed. A column is timed as construction plus its whole replay. All threads run the same variant
 * at the same time. After its last round every thread verifies the variants it timed against the reference.
 * A failing thread terminates the phaser, so no other thread can wait for it forever.
 */
final class Bench {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int CONSTRUCT = 0;
    private static final int FILL = 1;
    private static final int COLUMN = 2;
    private static final int COLUMN_EARLY = 3;
    private static final int METRICS = 4;

    private final List<Variant> variants;
    private final List<ChunkCase> chunkCases;
    private final List<CallList> chunkCalls;
    private final List<ChunkCase> columnCases;
    private final List<CallList> columnCalls;
    /** Per column case: the calls getBaseHeight makes before its tester accepts a state (the first block that is not air). */
    private final int[] earlyExitCalls;
    private final List<ChunkCase> warmVerifyCases;
    private final Options options;
    private final Phaser sync;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final long[][][][] nanos;
    private final int[] sinks;
    private final List<Tally> warmVerify = new ArrayList<>();
    private volatile boolean warmupDone;
    private volatile int warmupRounds;
    private long warmupDeadline;

    Bench(List<Variant> variants, Workload workload, Options options) {
        this.variants = variants;
        this.chunkCases = workload.of(ChunkCase.Group.FILL);
        this.chunkCalls = firstPhases(this.chunkCases);
        this.columnCases = workload.of(ChunkCase.Group.COLUMN);
        this.columnCalls = firstPhases(this.columnCases);
        this.earlyExitCalls = earlyExitLengths(this.columnCases, this.columnCalls);
        this.warmVerifyCases = workload.cases().stream().filter(c -> c.group() != ChunkCase.Group.EDGE).toList();
        this.options = options;
        this.sync = new Phaser(options.threads);
        this.nanos = new long[options.threads][variants.size()][METRICS][options.rounds];
        this.sinks = new int[options.threads];
    }

    Measurements run() throws InterruptedException {
        this.warmupDeadline = System.nanoTime() + this.options.warmupSeconds * NANOS_PER_SECOND;
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < this.options.threads; ++t) {
            int threadIndex = t;
            Thread thread = new Thread(() -> this.work(threadIndex), "bench-" + t);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        if (this.failure.get() != null) {
            throw new IllegalStateException("Benchmark worker failed", this.failure.get());
        }
        return this.aggregate();
    }

    private static List<CallList> firstPhases(List<ChunkCase> cases) {
        return cases.stream().map(c -> c.phases().getFirst().calls()).toList();
    }

    private void work(int threadIndex) {
        try {
            int round = 0;
            do {
                this.playRound(threadIndex, round++, -1);
                this.await();
                if (threadIndex == 0) {
                    this.warmupDone = System.nanoTime() >= this.warmupDeadline;
                    this.warmupRounds = round;
                }
                this.await();
            } while (!this.warmupDone);
            for (int measured = 0; measured < this.options.rounds; ++measured) {
                this.playRound(threadIndex, measured, measured);
            }
            this.verifyTimedVariants();
        } catch (Throwable t) {
            this.failure.compareAndSet(null, t);
            this.sync.forceTermination();
        }
    }

    /** The variants that were just timed, against the reference, on this thread's warm code. */
    private void verifyTimedVariants() {
        String referenceName = Variants.reference().name();
        List<Variant> candidates = this.variants.stream().filter(v -> !v.name().equals(referenceName)).toList();
        if (candidates.isEmpty()) {
            return;
        }
        Verifier verifier = new Verifier(Variants.reference(), candidates);
        verifier.verify(this.warmVerifyCases);
        synchronized (this.warmVerify) {
            this.warmVerify.addAll(verifier.results());
        }
    }

    /** Runs every variant once in rotated order; record is the measured round index, or -1 while warming up. */
    private void playRound(int threadIndex, int rotation, int record) {
        int count = this.variants.size();
        for (int i = 0; i < count; ++i) {
            int v = (rotation + i) % count;
            this.await();
            long[] result = new long[METRICS];
            int sink = this.timeChunks(this.variants.get(v), result) + this.timeColumns(this.variants.get(v), result);
            this.sinks[threadIndex] += sink;
            if (record >= 0) {
                for (int metric = 0; metric < METRICS; ++metric) {
                    this.nanos[threadIndex][v][metric][record] = result[metric];
                }
            }
        }
    }

    private int timeChunks(Variant variant, long[] result) {
        int sink = 0;
        for (int i = 0; i < this.chunkCases.size(); ++i) {
            ChunkCase chunkCase = this.chunkCases.get(i);
            try (AquiferRun run = AquiferRun.open(chunkCase.world(), false)) {
                long start = System.nanoTime();
                Aquifer aquifer = run.construct(variant, chunkCase.volume());
                long constructed = System.nanoTime();
                run.fillDensityCaches(chunkCase.volume());
                long replayStart = System.nanoTime();
                sink += Replay.run(aquifer, this.chunkCalls.get(i));
                long end = System.nanoTime();
                result[CONSTRUCT] += constructed - start;
                result[FILL] += end - replayStart;
            }
        }
        return sink;
    }

    /** getBaseColumn's pattern (the whole column) and getBaseHeight's (stop at the first block its tester accepts), on fresh aquifers. */
    private int timeColumns(Variant variant, long[] result) {
        int sink = 0;
        for (int i = 0; i < this.columnCases.size(); ++i) {
            ChunkCase columnCase = this.columnCases.get(i);
            CallList calls = this.columnCalls.get(i);
            sink += this.timeColumn(variant, columnCase, calls, calls.size, result, COLUMN);
            sink += this.timeColumn(variant, columnCase, calls, this.earlyExitCalls[i], result, COLUMN_EARLY);
        }
        return sink;
    }

    /** Construction plus the first count calls of a column; finalDensity runs between them, untimed. */
    private int timeColumn(Variant variant, ChunkCase columnCase, CallList calls, int count, long[] result, int metric) {
        try (AquiferRun run = AquiferRun.open(columnCase.world(), false)) {
            long start = System.nanoTime();
            Aquifer aquifer = run.construct(variant, columnCase.volume());
            long constructed = System.nanoTime();
            run.fillDensityCaches(columnCase.volume());
            long replayStart = System.nanoTime();
            int sink = Replay.run(aquifer, calls, count);
            long end = System.nanoTime();
            result[metric] += (constructed - start) + (end - replayStart);
            return sink;
        }
    }

    /**
     * Where getBaseHeight stops (NBCG:207-209): after the first call whose state is not air, a null state counting as the default
     * block. Found once per column with the reference, untimed; every variant replays that many calls.
     */
    private static int[] earlyExitLengths(List<ChunkCase> cases, List<CallList> calls) {
        int[] lengths = new int[cases.size()];
        for (int i = 0; i < lengths.length; ++i) {
            CallList list = calls.get(i);
            BlockState[] states = new BlockState[list.size];
            boolean[] flags = new boolean[list.size];
            try (AquiferRun run = AquiferRun.open(cases.get(i).world(), false)) {
                Aquifer reference = run.construct(Variants.reference(), cases.get(i).volume());
                run.fillDensityCaches(cases.get(i).volume());
                Replay.record(reference, list, states, flags);
            }
            lengths[i] = list.size;
            for (int call = 0; call < list.size; ++call) {
                if (states[call] == null || !states[call].isAir()) {
                    lengths[i] = call + 1;
                    break;
                }
            }
        }
        return lengths;
    }

    private void await() {
        if (this.sync.arriveAndAwaitAdvance() < 0) {
            throw new IllegalStateException("Benchmark aborted because another thread failed");
        }
    }

    private Measurements aggregate() {
        int variantCount = this.variants.size();
        int rounds = this.options.rounds;
        double[][] construct = new double[variantCount][rounds];
        double[][] fill = new double[variantCount][rounds];
        double[][] column = new double[variantCount][rounds];
        double[][] columnEarly = new double[variantCount][rounds];
        for (int v = 0; v < variantCount; ++v) {
            for (int r = 0; r < rounds; ++r) {
                construct[v][r] = this.perUnit(v, CONSTRUCT, r, this.chunkCases.size());
                fill[v][r] = this.perUnit(v, FILL, r, this.chunkCases.size());
                column[v][r] = this.perUnit(v, COLUMN, r, this.columnCases.size());
                columnEarly[v][r] = this.perUnit(v, COLUMN_EARLY, r, this.columnCases.size());
            }
        }
        int sink = 0;
        for (int value : this.sinks) {
            sink += value;
        }
        List<String> names = this.variants.stream().map(Variant::name).toList();
        return new Measurements(names, this.chunkCases.size(), this.columnCases.size(), this.options.threads, this.warmupRounds, construct, fill,
            column, columnEarly, sink, this.warmVerify);
    }

    /** Mean over threads of one metric's time, divided by the number of chunks or columns timed; NaN when there are none. */
    private double perUnit(int variant, int metric, int round, int units) {
        if (units == 0) {
            return Double.NaN;
        }
        double sum = 0.0;
        for (long[][][] perThread : this.nanos) {
            sum += perThread[variant][metric][round];
        }
        return sum / this.options.threads / units;
    }
}
