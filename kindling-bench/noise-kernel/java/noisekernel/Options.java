package noisekernel;

import java.nio.file.Path;

/** Command line of the harness. */
final class Options {
    boolean verify;
    boolean bench;
    boolean noNative;
    int threads = 1;
    int warmupSeconds = 10;
    int rounds = 30;
    int chunks = 64;
    int fuzz = 2000;
    long seed = 20261029L;
    Path out;

    static Options parse(String[] args) {
        Options options = new Options();
        for (int i = 0; i < args.length; ++i) {
            switch (args[i]) {
                case "--verify" -> options.verify = true;
                case "--bench" -> options.bench = true;
                case "--no-native" -> options.noNative = true;
                case "--threads" -> options.threads = positive(args, ++i);
                case "--warmup-sec" -> options.warmupSeconds = positive(args, ++i);
                case "--rounds" -> options.rounds = positive(args, ++i);
                case "--chunks" -> options.chunks = positive(args, ++i);
                case "--fuzz" -> options.fuzz = positive(args, ++i);
                case "--seed" -> options.seed = Long.parseLong(value(args, ++i));
                case "--out" -> options.out = Path.of(value(args, ++i));
                default -> throw new IllegalArgumentException("Unknown option " + args[i] + "\n" + usage());
            }
        }
        if (options.verify == options.bench) {
            throw new IllegalArgumentException("Give exactly one of --verify and --bench\n" + usage());
        }
        return options;
    }

    static String usage() {
        return "usage: Main (--verify | --bench) [--no-native] [--threads N] [--warmup-sec S] [--rounds N]\n"
            + "            [--chunks N] [--fuzz N] [--seed N] [--out file.json]";
    }

    private static String value(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + args[index - 1]);
        }
        return args[index];
    }

    private static int positive(String[] args, int index) {
        int parsed = Integer.parseInt(value(args, index));
        if (parsed < 1) {
            throw new IllegalArgumentException(args[index - 1] + " must be at least 1");
        }
        return parsed;
    }
}
