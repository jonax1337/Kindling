package densitycompiler;

import java.util.ArrayList;
import java.util.List;
import densitycompiler.eval.TreeDump;

/** Entry point: --verify checks bit-exactness of every variant, --bench times them, --self-test proves the verifier can fail. */
public final class Main {
    private static final int EXIT_FAILED = 1;
    private static final int EXIT_USAGE = 2;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(EXIT_USAGE);
            return;
        }
        System.out.printf("java %s, %s, %d processors%n", System.getProperty("java.version"), System.getProperty("os.name"),
            Runtime.getRuntime().availableProcessors());
        Variants.configure(options.tuning);
        List<World> worlds = World.create(options.seeds, options.dimension);
        boolean ok = switch (options.mode) {
            case VERIFY -> verify(worlds, options);
            case BENCH -> bench(worlds, options);
            case SELF_TEST -> selfTest(worlds, options);
            case DUMP -> dump(worlds);
        };
        System.exit(ok ? 0 : EXIT_FAILED);
    }

    private static boolean verify(List<World> worlds, Options options) {
        List<Verifier.Tally> tallies = new ArrayList<>();
        System.out.printf("verify: COMPILED tuning callCost %d, runGap %d%n", options.tuning.callCost(), options.tuning.runGap());
        if (!options.syntheticOnly) {
            tallies.addAll(verifyWorlds(worlds, options));
        }
        tallies.addAll(SyntheticSuite.run(worlds.getFirst()));
        tallies.forEach(tally -> System.out.println(tally.summary()));
        boolean ok = Verifier.allPassed(tallies);
        System.out.println(ok ? "VERIFY PASSED" : "VERIFY FAILED");
        return ok;
    }

    private static List<Verifier.Tally> verifyWorlds(List<World> worlds, Options options) {
        Workload workload = Workload.build(worlds, options);
        List<Chunk> chunks = verifyChunks(worlds, options, workload);
        System.out.printf("verify: %d calls (%d workload chunks, %d verify-only: %d fuzz chunks, %d columns, %d boxes), %d seeds, dimension %s%n",
            chunks.size(), workload.chunks().size(), chunks.size() - workload.chunks().size(), options.fuzz, options.columns, options.boxes,
            worlds.size(), options.dimension);
        List<Verifier.Tally> tallies = Verifier.fresh(worlds, Variants.reference(), Variants.candidates()).verify(chunks);
        printDiagnostics(Variants.candidates());
        System.out.println(new NaiveOracle(worlds, options).check(workload.chunks()).summary());
        return tallies;
    }

    /** The bench chunk list plus everything only the verifier checks. */
    private static List<Chunk> verifyChunks(List<World> worlds, Options options, Workload workload) {
        List<Chunk> chunks = new ArrayList<>(workload.chunks());
        chunks.addAll(Workload.verifyOnly(worlds, options));
        return chunks;
    }

    private static boolean bench(List<World> worlds, Options options) throws InterruptedException, java.io.IOException {
        if (options.rounds < 30) {
            System.out.println("note: fewer than 30 measured rounds; numbers are a smoke test only");
        }
        Workload workload = Workload.build(worlds, options);
        List<Variant> variants = Variants.benchmarked(options.variants);
        System.out.printf("bench: %d chunks per round, variants %s%n", workload.chunks().size(), variants.stream().map(Variant::name).toList());
        Measurements measurements = new Bench(worlds, variants, workload.chunks(), verifyChunks(worlds, options, workload), options).run();
        List<Verifier.Tally> warm = measurements.warmVerify();
        Report report = new Report(measurements, options, workload);
        report.print();
        if (options.out != null) {
            report.writeJson(options.out);
        }
        warm.forEach(tally -> System.out.println(tally.summary()));
        printDiagnostics(variants);
        boolean ok = Verifier.allPassed(warm);
        System.out.println(ok ? "WARM VERIFY PASSED" : "WARM VERIFY FAILED");
        return ok;
    }

    private static void printDiagnostics(List<Variant> variants) {
        for (Variant variant : variants) {
            String diagnostics = variant.diagnostics();
            if (!diagnostics.isEmpty()) {
                System.out.print(diagnostics);
            }
        }
    }

    private static boolean dump(List<World> worlds) {
        System.out.println("PAPER sampler tree of finalDensity, seed " + worlds.getFirst().seed() + ":");
        System.out.print(TreeDump.of(worlds.getFirst().paperSampler()));
        return true;
    }

    private static boolean selfTest(List<World> worlds, Options options) {
        boolean ok = SelfTest.run(worlds.getFirst(), options);
        System.out.println(ok ? "SELF-TEST PASSED" : "SELF-TEST FAILED");
        return ok;
    }
}
