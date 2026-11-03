package previewchunks;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Times TERRAIN (the whole chunk) against PREVIEW (the band) on the same chunks. Every thread generates the whole
 * workload once per variant per round, all threads start each pass together, and the order of the variants alternates
 * from round to round. One JVM is one fork; run.sh starts the forks.
 */
final class Bench {
    enum Variant {
        TERRAIN {
            @Override
            ProtoChunk generate(Workload.Task task) {
                return task.terrain().generateFull(task.pos());
            }
        },
        PREVIEW {
            @Override
            ProtoChunk generate(Workload.Task task) {
                return task.preview().generateSkin(task.pos());
            }
        };

        abstract ProtoChunk generate(Workload.Task task);
    }

    private record Pass(double millis, int sink) {
    }

    private final Workload workload;
    private final Options options;
    private final CyclicBarrier barrier;
    /** Milliseconds per chunk, by round, variant and thread. */
    private final double[][][] msPerChunk;

    private Bench(Workload workload, Options options) {
        this.workload = workload;
        this.options = options;
        this.barrier = new CyclicBarrier(options.threads());
        this.msPerChunk = new double[options.rounds()][Variant.values().length][options.threads()];
    }

    static int run(Workload workload, Options options) throws IOException, InterruptedException, ExecutionException {
        Bench bench = new Bench(workload, options);
        bench.measure();
        bench.write();
        bench.printSummary();
        return 0;
    }

    /** Runs every thread; the first failure ends the run (it breaks the barrier, so no thread waits for it). */
    private void measure() throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(this.options.threads());
        try {
            CompletionService<Integer> threads = new ExecutorCompletionService<>(pool);
            for (int thread = 0; thread < this.options.threads(); thread++) {
                int index = thread;
                threads.submit(() -> runThreadOrBreakBarrier(index));
            }
            int sink = 0;
            for (int thread = 0; thread < this.options.threads(); thread++) {
                sink ^= threads.take().get();
            }
            System.out.println("sink " + sink);
        } finally {
            pool.shutdownNow();
        }
    }

    private int runThreadOrBreakBarrier(int thread) throws Exception {
        try {
            return runThread(thread);
        } catch (Exception e) {
            this.barrier.reset();
            throw e;
        }
    }

    private int runThread(int thread) throws Exception {
        int sink = 0;
        for (int round = 0; round < this.options.warmupRounds(); round++) {
            for (Variant variant : order(round)) {
                sink ^= pass(variant).sink();
            }
            System.out.printf("thread %d warmup round %d done%n", thread, round + 1);
        }
        for (int round = 0; round < this.options.rounds(); round++) {
            for (Variant variant : order(round)) {
                Pass pass = pass(variant);
                this.msPerChunk[round][variant.ordinal()][thread] = pass.millis() / this.workload.tasks().size();
                sink ^= pass.sink();
            }
        }
        return sink;
    }

    private Pass pass(Variant variant) throws Exception {
        this.barrier.await();
        long start = System.nanoTime();
        int sink = 0;
        for (Workload.Task task : this.workload.tasks()) {
            sink ^= variant.generate(task).getHeight(Heightmap.Types.WORLD_SURFACE_WG, 7, 7);
        }
        double millis = (System.nanoTime() - start) / 1e6;
        this.barrier.await();
        return new Pass(millis, sink);
    }

    private static Variant[] order(int round) {
        return round % 2 == 0 ? new Variant[] {Variant.TERRAIN, Variant.PREVIEW} : new Variant[] {Variant.PREVIEW, Variant.TERRAIN};
    }

    /** The mean over the threads of one round's pass. */
    private double roundMean(int round, Variant variant) {
        return Stats.mean(this.msPerChunk[round][variant.ordinal()]);
    }

    private void write() throws IOException {
        JsonObject json = new JsonObject();
        JsonObject setup = new JsonObject();
        for (Map.Entry<String, String> entry : Setup.describe(this.options).entrySet()) {
            setup.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("setup", setup);
        json.addProperty("threads", this.options.threads());
        json.addProperty("chunks", this.workload.tasks().size());
        JsonArray rounds = new JsonArray();
        for (int round = 0; round < this.options.rounds(); round++) {
            for (Variant variant : Variant.values()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("round", round);
                entry.addProperty("variant", variant.name());
                entry.addProperty("msPerChunk", roundMean(round, variant));
                rounds.add(entry);
            }
        }
        json.add("rounds", rounds);
        Path out = this.options.out();
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(json));
    }

    private void printSummary() {
        int rounds = this.options.rounds();
        double[] terrain = new double[rounds];
        double[] preview = new double[rounds];
        double[] ratio = new double[rounds];
        for (int round = 0; round < rounds; round++) {
            terrain[round] = roundMean(round, Variant.TERRAIN);
            preview[round] = roundMean(round, Variant.PREVIEW);
            ratio[round] = terrain[round] / preview[round];
        }
        System.out.printf("threads %d, chunks %d, rounds %d%n", this.options.threads(), this.workload.tasks().size(), rounds);
        System.out.printf("TERRAIN median %.3f ms per chunk%n", Stats.median(terrain));
        System.out.printf("PREVIEW median %.3f ms per chunk%n", Stats.median(preview));
        System.out.printf("TERRAIN / PREVIEW per round: median %.3f, min %.3f, max %.3f%n", Stats.median(ratio),
            Stats.percentile(ratio, 0), Stats.percentile(ratio, 100));
        if (rounds < 30) {
            System.out.println("fewer than 30 rounds: a smoke test, not a measurement");
        }
    }
}
