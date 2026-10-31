package densitycompiler;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import densitycompiler.eval.Tuning;

/** Command line of the harness. */
final class Options {
    enum Mode { VERIFY, BENCH, SELF_TEST, DUMP }

    Mode mode;
    long[] seeds = {0L, 12345L, -4172144997902289642L};
    String dimension = "overworld";
    int chunks = 128;
    int square = 8;
    int far = 4;
    int beardEvery = 8;
    int fuzz = 64;
    int columns = 32;
    int boxes = 48;
    int naiveChunks = 4;
    int naivePoints = 64;
    int threads = 1;
    int warmupSeconds = 10;
    int rounds = 30;
    long workloadSeed = 20261030L;
    Tuning tuning = Tuning.DEFAULT;
    boolean syntheticOnly;
    List<String> variants;
    Path out;

    static Options parse(String[] args) {
        Options options = new Options();
        for (int i = 0; i < args.length; ++i) {
            switch (args[i]) {
                case "--verify" -> options.mode = Mode.VERIFY;
                case "--bench" -> options.mode = Mode.BENCH;
                case "--self-test" -> options.mode = Mode.SELF_TEST;
                case "--dump" -> options.mode = Mode.DUMP;
                case "--seeds" -> options.seeds = Arrays.stream(value(args, ++i).split(",")).mapToLong(Long::parseLong).toArray();
                case "--seed" -> options.seeds = new long[] {Long.parseLong(value(args, ++i))};
                case "--dimension" -> options.dimension = value(args, ++i);
                case "--chunks" -> options.chunks = nonNegative(args, ++i);
                case "--square" -> options.square = nonNegative(args, ++i);
                case "--far" -> options.far = nonNegative(args, ++i);
                case "--beard-every" -> options.beardEvery = nonNegative(args, ++i);
                case "--fuzz" -> options.fuzz = nonNegative(args, ++i);
                case "--columns" -> options.columns = nonNegative(args, ++i);
                case "--boxes" -> options.boxes = nonNegative(args, ++i);
                case "--naive-chunks" -> options.naiveChunks = nonNegative(args, ++i);
                case "--naive-points" -> options.naivePoints = nonNegative(args, ++i);
                case "--threads" -> options.threads = positive(args, ++i);
                case "--warmup-sec" -> options.warmupSeconds = nonNegative(args, ++i);
                case "--rounds" -> options.rounds = positive(args, ++i);
                case "--workload-seed" -> options.workloadSeed = Long.parseLong(value(args, ++i));
                case "--tuning" -> options.tuning = parseTuning(value(args, ++i));
                case "--synthetic-only" -> options.syntheticOnly = true;
                case "--variants" -> options.variants = List.of(value(args, ++i).split(","));
                case "--out" -> options.out = Path.of(value(args, ++i));
                default -> throw new IllegalArgumentException("Unknown option " + args[i] + "\n" + usage());
            }
        }
        if (options.mode == null) {
            throw new IllegalArgumentException("Give one of --verify, --bench, --self-test\n" + usage());
        }
        return options;
    }

    static String usage() {
        return "usage: Main (--verify | --bench | --self-test) [--seeds a,b,c | --seed N]\n"
            + "            [--dimension overworld|amplified|large_biomes|nether|end|caves|floating_islands]\n"
            + "            [--chunks N] [--square N] [--far N] [--beard-every N] [--fuzz N] [--columns N] [--boxes N]\n"
            + "            [--naive-chunks N] [--naive-points N]\n"
            + "            [--threads N] [--warmup-sec S] [--rounds N] [--workload-seed N] [--variants A,B] [--out file.json]\n"
            + "            [--tuning callCost,runGap] [--synthetic-only]";
    }

    private static Tuning parseTuning(String text) {
        String[] parts = text.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("--tuning takes callCost,runGap");
        }
        return new Tuning(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
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
