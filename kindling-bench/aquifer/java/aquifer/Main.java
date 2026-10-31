package aquifer;

import aquifer.candidate.BaseAquifer;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Entry point: --verify checks parity against vanilla, --bench times the variants, --self-test proves the verifier can fail. */
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
        if (options.mode == Options.Mode.LIST_BENCHMARKED) {
            Variants.benchmarked(null).forEach(variant -> System.out.println(variant.name()));
            return;
        }
        System.out.printf("java %s, %s, %d processors%n", System.getProperty("java.version"), System.getProperty("os.name"),
            Runtime.getRuntime().availableProcessors());
        boolean ok = switch (options.mode) {
            case VERIFY -> verify(options);
            case BENCH -> bench(options);
            case SELF_TEST -> selfTest(options);
            case LIST_BENCHMARKED -> true;
        };
        System.exit(ok ? 0 : EXIT_FAILED);
    }

    private static boolean verify(Options options) {
        long start = System.nanoTime();
        List<World> worlds = World.create(seedsOf(options), options.presets);
        Workload workload = Workload.build(worlds, options);
        long differing = RealChunkCheck.differences(workload.of(ChunkCase.Group.FILL).stream().filter(c -> c.volume().minBlockX() == 0).toList());
        System.out.printf("verify: harness VANILLA vs a real NoiseChunk on chunk (0, 0) of each world: %d differing calls%n", differing);
        System.out.printf("verify: %d cases on %d worlds (%s), reference %s, candidates %s%n", workload.cases().size(), worlds.size(),
            String.join(",", options.presets), Variants.reference().name(), Variants.candidates().stream().map(Variant::name).toList());
        Verifier verifier = new Verifier(Variants.reference(), Variants.candidates());
        verifier.verify(workload.cases());
        List<Tally> tallies = verifier.results();
        tallies.forEach(tally -> System.out.println(tally.summary()));
        printDiagnostics(Variants.candidates());
        boolean ok = differing == 0 && Verifier.allPassed(tallies) && partChecksPass(worlds.getFirst());
        System.out.printf("%s (%.1f s)%n", ok ? "VERIFY PASSED" : "VERIFY FAILED", (System.nanoTime() - start) / 1e9);
        return ok;
    }

    /** The pure pieces of every C2ME-family candidate against Mojang's private methods; independent of seed, so one world is enough. */
    private static boolean partChecksPass(World world) {
        boolean ok = true;
        for (Variant candidate : Variants.candidates()) {
            PartChecks.Result result = PartChecks.run(world, candidate, BaseAquifer::similarity);
            if (result != null) {
                System.out.printf("%s parts %-12s %s%n", result.passed() ? "PASS" : "FAIL", candidate.name(), result.summary());
                ok &= result.passed();
            }
        }
        return ok;
    }

    private static boolean bench(Options options) throws InterruptedException, IOException {
        if (options.rounds < 30) {
            System.out.println("note: fewer than 30 measured rounds; numbers are a smoke test only");
        }
        List<World> worlds = World.create(options.seeds, options.presets);
        Workload workload = Workload.build(worlds, options);
        List<Variant> variants = Variants.benchmarked(options.variants);
        System.out.printf("bench: variants %s%n", variants.stream().map(Variant::name).toList());
        Measurements measurements = new Bench(variants, workload, options).run();
        Report report = new Report(measurements, options);
        report.print();
        if (options.out != null) {
            report.writeJson(options.out);
        }
        measurements.warmVerify().forEach(tally -> System.out.println(tally.summary()));
        printDiagnostics(variants);
        boolean ok = measurements.warmVerify().stream().allMatch(Tally::passed);
        System.out.println(ok ? "WARM VERIFY PASSED" : "WARM VERIFY FAILED");
        return ok;
    }

    private static boolean selfTest(Options options) {
        List<World> worlds = World.create(options.seeds, List.of(options.presets.getFirst()));
        boolean ok = new SelfTest(worlds.getFirst(), options).run();
        System.out.println(ok ? "SELF-TEST PASSED" : "SELF-TEST FAILED");
        return ok;
    }

    /** The given seeds, plus one random seed (printed, so a failure can be repeated with --seeds) unless switched off. */
    private static long[] seedsOf(Options options) {
        if (!options.randomSeed) {
            return options.seeds;
        }
        long random = new SecureRandom().nextLong();
        System.out.println("verify: random seed " + random + " (repeat with --seeds " + random + ")");
        long[] seeds = Arrays.copyOf(options.seeds, options.seeds.length + 1);
        seeds[seeds.length - 1] = random;
        return seeds;
    }

    private static void printDiagnostics(List<Variant> variants) {
        List<String> texts = new ArrayList<>();
        for (Variant variant : variants) {
            if (!variant.diagnostics().isEmpty()) {
                texts.add(variant.diagnostics());
            }
        }
        texts.forEach(System.out::print);
    }
}
