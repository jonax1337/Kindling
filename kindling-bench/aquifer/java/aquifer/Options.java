package aquifer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Command line of the harness. */
final class Options {
    enum Mode { VERIFY, BENCH, SELF_TEST, LIST_BENCHMARKED }

    private static final int UNSET = -1;
    private static final List<String> AQUIFER_PRESETS = List.of("overworld", "large_biomes", "amplified");
    private static final long[] VERIFY_SEEDS = {0L, 1L, -7L, 42L};
    private static final long[] BENCH_SEEDS = {0L, 1L};

    Mode mode;
    long[] seeds;
    boolean randomSeed = true;
    List<String> presets;
    int chunks = UNSET;
    int columns = UNSET;
    int shuffles = UNSET;
    /** Whether --chunks, --columns or --shuffles was given; the self-test then uses that size instead of its small one. */
    boolean sizeGiven;
    Boolean edge;
    int threads = 1;
    int warmupSeconds = 4;
    int rounds = 10;
    long workloadSeed = 20261030L;
    List<String> variants;
    Path out;

    static Options parse(String[] args) {
        Options options = new Options();
        for (int i = 0; i < args.length; ++i) {
            switch (args[i]) {
                case "--verify" -> options.mode = Mode.VERIFY;
                case "--bench" -> options.mode = Mode.BENCH;
                case "--self-test" -> options.mode = Mode.SELF_TEST;
                case "--list-benchmarked" -> options.mode = Mode.LIST_BENCHMARKED;
                case "--seeds" -> options.setSeeds(Arrays.stream(value(args, ++i).split(",")).mapToLong(Long::parseLong).toArray());
                case "--seed" -> options.setSeeds(new long[] {Long.parseLong(value(args, ++i))});
                case "--no-random-seed" -> options.randomSeed = false;
                case "--presets" -> options.presets = List.of(value(args, ++i).split(","));
                case "--chunks" -> options.chunks = options.size(nonNegative(args, ++i));
                case "--columns" -> options.columns = options.size(nonNegative(args, ++i));
                case "--shuffles" -> options.shuffles = options.size(nonNegative(args, ++i));
                case "--edge" -> options.edge = true;
                case "--no-edge" -> options.edge = false;
                case "--threads" -> options.threads = positive(args, ++i);
                case "--warmup-sec" -> options.warmupSeconds = nonNegative(args, ++i);
                case "--rounds" -> options.rounds = positive(args, ++i);
                case "--workload-seed" -> options.workloadSeed = Long.parseLong(value(args, ++i));
                case "--variants" -> options.variants = List.of(value(args, ++i).split(","));
                case "--out" -> options.out = Path.of(value(args, ++i));
                default -> throw new IllegalArgumentException("Unknown option " + args[i] + "\n" + usage());
            }
        }
        if (options.mode == null) {
            throw new IllegalArgumentException("Give one of --verify, --bench, --self-test\n" + usage());
        }
        return options.resolveDefaults();
    }

    static String usage() {
        return "usage: Main (--verify | --bench | --self-test | --list-benchmarked)\n"
            + "            [--seeds a,b,c | --seed N] [--no-random-seed] [--presets overworld,large_biomes,amplified]\n"
            + "            [--chunks N] [--columns N] [--shuffles N] [--edge | --no-edge] [--workload-seed N]\n"
            + "            [--threads N] [--warmup-sec S] [--rounds N] [--variants A,B] [--out file.json]";
    }

    /** Verify wants breadth; bench wants a fixed, small workload on the overworld. Self-test builds its own cases. */
    private Options resolveDefaults() {
        boolean bench = this.mode == Mode.BENCH;
        if (this.seeds == null) {
            this.seeds = bench ? BENCH_SEEDS : VERIFY_SEEDS;
        }
        this.randomSeed &= !bench;
        if (this.presets == null) {
            this.presets = bench ? List.of("overworld") : AQUIFER_PRESETS;
        }
        this.chunks = this.chunks == UNSET ? (bench ? 6 : 6) : this.chunks;
        this.columns = this.columns == UNSET ? (bench ? 48 : 24) : this.columns;
        this.shuffles = this.shuffles == UNSET ? (bench ? 1 : 3) : this.shuffles;
        this.edge = this.edge == null ? !bench : this.edge;
        return this;
    }

    private int size(int value) {
        this.sizeGiven = true;
        return value;
    }

    /** Explicit seeds replace the defaults and switch the extra random seed off. */
    private void setSeeds(long[] explicit) {
        this.seeds = explicit;
        this.randomSeed = false;
    }

    private static String value(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + args[index - 1]);
        }
        return args[index];
    }

    private static int nonNegative(String[] args, int index) {
        int parsed = Integer.parseInt(value(args, index));
        if (parsed < 0) {
            throw new IllegalArgumentException(args[index - 1] + " must not be negative");
        }
        return parsed;
    }

    private static int positive(String[] args, int index) {
        int parsed = nonNegative(args, index);
        if (parsed < 1) {
            throw new IllegalArgumentException(args[index - 1] + " must be at least 1");
        }
        return parsed;
    }
}
