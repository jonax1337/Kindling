package noisekernel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Interleaved benchmark. Each round runs the whole call list once per variant, the variants in a
 * rotating order so drift cancels. All threads run the same variant at the same time.
 */
final class Bench {
    private static final int PERLIN = 0;
    private static final int SMEARED = 1;

    private final List<Variant> variants;
    private final Options options;
    private final KernelCall[] perlinCalls;
    private final KernelCall[] smearedCalls;
    private final int capacity;
    private final CyclicBarrier sync;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final long[][][][] nanos;
    private final double[] checksums;
    private volatile boolean warmupDone;
    private volatile int warmupRounds;
    private long warmupDeadline;

    Bench(Workload workload, List<Variant> variants, Options options) {
        this.variants = variants;
        this.options = options;
        this.perlinCalls = workload.callsOf(Kind.PERLIN);
        this.smearedCalls = workload.callsOf(Kind.SMEARED);
        this.capacity = maxSamples(workload.calls);
        this.sync = new CyclicBarrier(options.threads);
        this.nanos = new long[options.threads][variants.size()][2][options.rounds];
        this.checksums = new double[options.threads];
    }

    Measurements run() throws InterruptedException {
        this.warmupDeadline = System.nanoTime() + options.warmupSeconds * 1_000_000_000L;
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
            Work work = new Work(this.capacity);
            KernelCall[] perlin = this.perlinCalls.clone();
            KernelCall[] smeared = this.smearedCalls.clone();
            int round = 0;
            do {
                this.playRound(threadIndex, work, perlin, smeared, round++, -1);
                this.await();
                if (threadIndex == 0) {
                    this.warmupDone = System.nanoTime() >= this.warmupDeadline;
                    this.warmupRounds = round;
                }
                this.await();
            } while (!this.warmupDone);
            for (int measured = 0; measured < this.options.rounds; ++measured) {
                this.playRound(threadIndex, work, perlin, smeared, measured, measured);
            }
        } catch (Throwable t) {
            this.failure.compareAndSet(null, t);
            this.sync.reset();
        }
    }

    /** Runs every variant once in rotated order; record is the measured round index or -1 while warming up. */
    private void playRound(int threadIndex, Work work, KernelCall[] perlin, KernelCall[] smeared, int rotation, int record) {
        int count = this.variants.size();
        for (int i = 0; i < count; ++i) {
            int v = (rotation + i) % count;
            Variant variant = this.variants.get(v);
            this.await();
            work.clear();
            long start = System.nanoTime();
            variant.runAll(perlin, work);
            long middle = System.nanoTime();
            this.checksums[threadIndex] += work.checksum();
            work.clear();
            long afterClear = System.nanoTime();
            variant.runAll(smeared, work);
            long end = System.nanoTime();
            this.checksums[threadIndex] += work.checksum();
            if (record >= 0) {
                this.nanos[threadIndex][v][PERLIN][record] = middle - start;
                this.nanos[threadIndex][v][SMEARED][record] = end - afterClear;
            }
        }
    }

    private void await() {
        try {
            this.sync.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            throw new IllegalStateException("Benchmark thread released early", e);
        }
    }

    private Measurements aggregate() {
        int variantCount = this.variants.size();
        int rounds = this.options.rounds;
        long perlinSamples = totalSamples(this.perlinCalls);
        long smearedSamples = totalSamples(this.smearedCalls);
        long calls = (long)this.perlinCalls.length + this.smearedCalls.length;
        double[][][] nsPerSample = new double[variantCount][3][rounds];
        double[][] nsPerCall = new double[variantCount][rounds];
        for (int v = 0; v < variantCount; ++v) {
            for (int r = 0; r < rounds; ++r) {
                double perlinNanos = this.meanOverThreads(v, PERLIN, r);
                double smearedNanos = this.meanOverThreads(v, SMEARED, r);
                nsPerSample[v][0][r] = perlinNanos / perlinSamples;
                nsPerSample[v][1][r] = smearedNanos / smearedSamples;
                nsPerSample[v][2][r] = (perlinNanos + smearedNanos) / (perlinSamples + smearedSamples);
                nsPerCall[v][r] = (perlinNanos + smearedNanos) / calls;
            }
        }
        double checksum = 0.0;
        for (double value : this.checksums) {
            checksum += value;
        }
        List<String> names = this.variants.stream().map(Variant::name).toList();
        return new Measurements(names, perlinSamples, smearedSamples, this.perlinCalls.length, this.smearedCalls.length,
            this.options.threads, this.warmupRounds, nsPerSample, nsPerCall, checksum);
    }

    private double meanOverThreads(int variant, int part, int round) {
        double sum = 0.0;
        for (long[][][] perThread : this.nanos) {
            sum += perThread[variant][part][round];
        }
        return sum / this.options.threads;
    }

    private static long totalSamples(KernelCall[] calls) {
        long total = 0;
        for (KernelCall call : calls) {
            total += call.samples();
        }
        return total;
    }

    private static int maxSamples(KernelCall[] calls) {
        int max = 0;
        for (KernelCall call : calls) {
            max = Math.max(max, call.samples());
        }
        return max;
    }
}
