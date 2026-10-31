package aquifer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import net.minecraft.world.level.levelgen.RandomState;

/** Console table and JSON file for one benchmark fork. */
final class Report {
    private static final double NANOS_PER_MICRO = 1e3;
    private static final double NANOS_PER_MILLI = 1e6;

    private final Measurements measurements;
    private final Options options;

    Report(Measurements measurements, Options options) {
        this.measurements = measurements;
        this.options = options;
    }

    void print() {
        Measurements m = this.measurements;
        System.out.printf("%n%d rounds, %d thread(s), %d warmup rounds, %d chunks and %d columns per round%n", this.options.rounds, m.threads(),
            m.warmupRounds(), m.chunkCount(), m.columnCount());
        System.out.printf("%-12s %26s %26s %24s %24s%n", "variant", "construct us/chunk", "fill ms/chunk", "us/column (full)", "us/column (early)");
        for (int v = 0; v < m.variants().size(); ++v) {
            System.out.printf(Locale.ROOT, "%-12s %s %s %s %s%n", m.variants().get(v), cell(m.constructNsPerChunk()[v], NANOS_PER_MICRO, 26),
                cell(m.fillNsPerChunk()[v], NANOS_PER_MILLI, 26), cell(m.nsPerColumn()[v], NANOS_PER_MICRO, 24),
                cell(m.nsPerColumnEarly()[v], NANOS_PER_MICRO, 24));
        }
        System.out.printf(Locale.ROOT, "median (min..max) over rounds, per thread; sink %08x%n", m.sink());
        this.printSpeedups();
    }

    /** Speedup of each variant over the reference, per metric, by medians; shown only when the reference ran. */
    private void printSpeedups() {
        Measurements m = this.measurements;
        int baseline = m.variants().indexOf(Variants.reference().name());
        if (baseline < 0 || m.variants().size() < 2) {
            return;
        }
        System.out.printf("%nspeedup over %s (median ratio, above 1 is faster; this table is a smoke test unless there were 30+ rounds;%n"
            + "VANILLA_B is the same code as the reference, so its row is the noise floor, and a variant only counts beyond it):%n", m.variants().get(baseline));
        for (int v = 0; v < m.variants().size(); ++v) {
            if (v != baseline) {
                System.out.printf(Locale.ROOT, "  %-12s construct %.3fx, fill %.3fx, column %.3fx, column early exit %.3fx%n", m.variants().get(v),
                    ratio(m.constructNsPerChunk(), baseline, v), ratio(m.fillNsPerChunk(), baseline, v), ratio(m.nsPerColumn(), baseline, v),
                    ratio(m.nsPerColumnEarly(), baseline, v));
            }
        }
    }

    void writeJson(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, this.toJson(), StandardCharsets.UTF_8);
        System.out.println("wrote " + path);
    }

    private static double ratio(double[][] metric, int baseline, int variant) {
        return Stats.of(metric[baseline]).median() / Stats.of(metric[variant]).median();
    }

    private static String cell(double[] nanos, double unit, int width) {
        Stats stats = Stats.of(nanos);
        String text = String.format(Locale.ROOT, "%.2f (%.2f..%.2f)", stats.median() / unit, stats.min() / unit, stats.max() / unit);
        return String.format("%" + width + "s", text);
    }

    private String toJson() {
        Measurements m = this.measurements;
        StringBuilder json = new StringBuilder(1 << 13);
        json.append("{\n");
        json.append("  \"meta\": ").append(this.metaJson()).append(",\n");
        json.append("  \"warm_verify\": ").append(this.warmVerifyJson()).append(",\n");
        json.append("  \"variants\": {\n");
        for (int v = 0; v < m.variants().size(); ++v) {
            json.append("    ").append(quote(m.variants().get(v))).append(": {\"construct_ns_per_chunk\": ").append(array(m.constructNsPerChunk()[v]));
            json.append(", \"fill_ns_per_chunk\": ").append(array(m.fillNsPerChunk()[v]));
            json.append(", \"ns_per_column\": ").append(array(m.nsPerColumn()[v]));
            json.append(", \"ns_per_column_early_exit\": ").append(array(m.nsPerColumnEarly()[v])).append("}");
            json.append(v + 1 < m.variants().size() ? ",\n" : "\n");
        }
        json.append("  }\n}\n");
        return json.toString();
    }

    private String metaJson() {
        Measurements m = this.measurements;
        StringBuilder meta = new StringBuilder("{");
        meta.append("\"server_jar_version\": ").append(quote(serverJarVersion()));
        meta.append(", \"java_version\": ").append(quote(System.getProperty("java.version") + " " + System.getProperty("java.vm.name")));
        meta.append(", \"os\": ").append(quote(System.getProperty("os.name") + " " + System.getProperty("os.version")));
        meta.append(", \"arch\": ").append(quote(System.getProperty("os.arch")));
        meta.append(", \"available_processors\": ").append(Runtime.getRuntime().availableProcessors());
        meta.append(", \"presets\": ").append(quote(String.join(",", this.options.presets)));
        meta.append(", \"world_seeds\": ").append(Arrays.toString(this.options.seeds));
        meta.append(", \"threads\": ").append(m.threads());
        meta.append(", \"warmup_seconds\": ").append(this.options.warmupSeconds);
        meta.append(", \"warmup_rounds\": ").append(m.warmupRounds());
        meta.append(", \"rounds\": ").append(this.options.rounds);
        meta.append(", \"chunks_per_round\": ").append(m.chunkCount());
        meta.append(", \"columns_per_round\": ").append(m.columnCount());
        meta.append(", \"workload_seed\": ").append(this.options.workloadSeed);
        meta.append(", \"sink\": ").append(m.sink());
        meta.append(", \"variant_order\": \"round r runs variant (r + i) mod V for i = 0..V-1\"");
        meta.append("}");
        return meta.toString();
    }

    private String warmVerifyJson() {
        List<Tally> tallies = this.measurements.warmVerify();
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < tallies.size(); ++i) {
            Tally tally = tallies.get(i);
            json.append(i == 0 ? "" : ", ");
            json.append("{\"variant\": ").append(quote(tally.candidate()));
            json.append(", \"label\": ").append(quote(tally.label()));
            json.append(", \"passed\": ").append(tally.passed());
            json.append(", \"calls\": ").append(tally.calls());
            json.append(", \"mismatches\": ").append(tally.mismatches()).append("}");
        }
        return json.append("]").toString();
    }

    /** Implementation-Version of the jar the server classes come from, or "unknown". */
    private static String serverJarVersion() {
        try {
            Path jarPath = Path.of(RandomState.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                String version = jar.getManifest().getMainAttributes().getValue("Implementation-Version");
                return jarPath.getFileName() + " " + version;
            }
        } catch (Exception e) {
            return "unknown (" + e.getClass().getSimpleName() + ")";
        }
    }

    private static String array(double[] values) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.length; ++i) {
            json.append(i == 0 ? "" : ", ").append(values[i]);
        }
        return json.append("]").toString();
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
