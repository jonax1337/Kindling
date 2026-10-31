package densitycompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Interleaved benchmark. Each round runs the whole chunk list once per variant, the variants in a rotating order
 * so drift cancels. All threads run the same variant at the same time; each has its own samplers and arena.
 * After its last round every thread verifies the variant instances it timed, on its own arena, against the reference.
 * A failing thread terminates the phaser, so no other thread can wait for it forever.
 */
final class Bench {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final List<World> worlds;
    private final List<Variant> variants;
    private final List<Chunk> chunks;
    private final Options options;
    private final List<Chunk> verifyChunks;
    private final Phaser sync;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final long[][][] nanos;
    private final int[] sinks;
    private final List<Verifier.Tally> warmVerify = new ArrayList<>();
    private volatile boolean warmupDone;
    private volatile int warmupRounds;
    private long warmupDeadline;

    Bench(List<World> worlds, List<Variant> variants, List<Chunk> chunks, List<Chunk> verifyChunks, Options options) {
        this.worlds = worlds;
        this.variants = variants;
        this.chunks = chunks;
        this.verifyChunks = verifyChunks;
        this.options = options;
        this.sync = new Phaser(options.threads);
        this.nanos = new long[options.threads][variants.size()][options.rounds];
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

    private void work(int threadIndex) {
        try {
            List<BoundVariant> bound = this.variants.stream().map(variant -> new BoundVariant(variant, this.worlds)).toList();
            int round = 0;
            do {
                this.playRound(threadIndex, bound, round++, -1);
                this.await();
                if (threadIndex == 0) {
                    this.warmupDone = System.nanoTime() >= this.warmupDeadline;
                    this.warmupRounds = round;
                }
                this.await();
            } while (!this.warmupDone);
            for (int measured = 0; measured < this.options.rounds; ++measured) {
                this.playRound(threadIndex, bound, measured, measured);
            }
            this.verifyTimedInstances(threadIndex, bound);
        } catch (Throwable t) {
            this.failure.compareAndSet(null, t);
            this.sync.forceTermination();
        }
    }

    /** The instances that were just timed, against the reference, on the chunks and the verify-only volumes. */
    private void verifyTimedInstances(int threadIndex, List<BoundVariant> bound) {
        String referenceName = Variants.reference().name();
        BoundVariant reference = bound.stream().filter(b -> b.name().equals(referenceName)).findFirst()
            .orElseGet(() -> new BoundVariant(Variants.reference(), this.worlds));
        List<BoundVariant> candidates = bound.stream().filter(b -> !b.name().equals(referenceName)).toList();
        String scope = this.options.threads > 1 ? " [thread " + threadIndex + "]" : "";
        List<Verifier.Tally> tallies = new Verifier(reference, candidates, scope, false).verify(this.verifyChunks);
        synchronized (this.warmVerify) {
            this.warmVerify.addAll(tallies);
        }
    }

    /** Runs every variant once in rotated order; record is the measured round index, or -1 while warming up. */
    private void playRound(int threadIndex, List<BoundVariant> bound, int rotation, int record) {
        int count = bound.size();
        for (int i = 0; i < count; ++i) {
            int v = (rotation + i) % count;
            this.await();
            long start = System.nanoTime();
            this.sinks[threadIndex] += runAll(bound.get(v), this.chunks);
            long end = System.nanoTime();
            if (record >= 0) {
                this.nanos[threadIndex][v][record] = end - start;
            }
        }
    }

    private static int runAll(BoundVariant variant, List<Chunk> chunks) {
        int sink = 0;
        for (int i = 0; i < chunks.size(); ++i) {
            sink += variant.run(chunks.get(i));
        }
        return sink;
    }

    private void await() {
        if (this.sync.arriveAndAwaitAdvance() < 0) {
            throw new IllegalStateException("Benchmark aborted because another thread failed");
        }
    }

    private Measurements aggregate() {
        int variantCount = this.variants.size();
        int rounds = this.options.rounds;
        double[][] nsPerChunk = new double[variantCount][rounds];
        double[][] chunksPerSecond = new double[variantCount][rounds];
        for (int v = 0; v < variantCount; ++v) {
            for (int r = 0; r < rounds; ++r) {
                double meanNanos = this.meanOverThreads(v, r);
                nsPerChunk[v][r] = meanNanos / this.chunks.size();
                chunksPerSecond[v][r] = this.options.threads * this.chunks.size() * NANOS_PER_SECOND / meanNanos;
            }
        }
        int sink = 0;
        for (int value : this.sinks) {
            sink += value;
        }
        List<String> names = this.variants.stream().map(Variant::name).toList();
        return new Measurements(names, this.chunks.size(), this.options.threads, this.warmupRounds, nsPerChunk, chunksPerSecond, sink, this.warmVerify);
    }

    private double meanOverThreads(int variant, int round) {
        double sum = 0.0;
        for (long[][] perThread : this.nanos) {
            sum += perThread[variant][round];
        }
        return sum / this.options.threads;
    }
}
