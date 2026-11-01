package previewchunks;

/**
 * Preview chunks experiment: how much cheaper, and how exact, vanilla terrain is when only a band around the surface is
 * generated. Commands: verify, bench, report, packets (see README.md).
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        // The server's background executors are not daemon threads: exit explicitly, also after a failure.
        int status = 1;
        try {
            status = run(Options.parse(args));
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            System.exit(status);
        }
    }

    private static int run(Options options) throws Exception {
        if (options.command().equals("report")) {
            Report.run(options.dir());
            return 0;
        }
        Vanilla vanilla = Vanilla.load();
        Setup.print(options);
        Workload workload = Workload.create(vanilla, options);
        return switch (options.command()) {
            case "verify" -> Verify.run(workload);
            case "bench" -> Bench.run(workload, options);
            case "packets" -> Packets.run(workload, vanilla, options);
            default -> throw new IllegalArgumentException("unknown command " + options.command());
        };
    }
}
