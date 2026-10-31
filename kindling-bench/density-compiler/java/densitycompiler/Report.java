package densitycompiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import net.minecraft.world.level.levelgen.RandomState;

/** Console table and JSON file for one benchmark run. */
final class Report {
    private final Measurements measurements;
    private final Options options;
    private final Workload workload;

    Report(Measurements measurements, Options options, Workload workload) {
        this.measurements = measurements;
        this.options = options;
        this.workload = workload;
    }

    void print() {
        Measurements m = this.measurements;
        System.out.printf("%n%d rounds, %d thread(s), %d warmup rounds, %d chunks per round (%d with a structure)%n",
            this.options.rounds, m.threads(), m.warmupRounds(), m.chunkCount(), this.workload.structureCount());
        System.out.printf("%-16s %12s %12s %12s %12s %10s%n", "variant", "ms/chunk", "min", "max", "chunks/s", "speedup");
        for (int v = 0; v < m.variants().size(); ++v) {
            Stats ns = Stats.of(m.nsPerChunk()[v]);
            Stats rate = Stats.of(m.chunksPerSecond()[v]);
            System.out.printf(Locale.ROOT, "%-16s %12.3f %12.3f %12.3f %12.1f %10s%n", m.variants().get(v),
                ns.median() / 1e6, ns.min() / 1e6, ns.max() / 1e6, rate.median(), this.speedup(v));
        }
        System.out.printf(Locale.ROOT, "ms per chunk, median/min/max over rounds, per thread; speedup = median(PAPER) / median(variant); sink %08x%n", m.sink());
    }

    private String speedup(int variant) {
        int baseline = this.measurements.variants().indexOf("PAPER");
        if (baseline < 0) {
            return "n/a";
        }
        double paper = Stats.of(this.measurements.nsPerChunk()[baseline]).median();
        return String.format(Locale.ROOT, "%.3fx", paper / Stats.of(this.measurements.nsPerChunk()[variant]).median());
    }

    void writeJson(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, this.toJson(), StandardCharsets.UTF_8);
        System.out.println("wrote " + path);
    }

    private String toJson() {
        StringBuilder json = new StringBuilder(1 << 14);
        json.append("{\n");
        json.append("  \"meta\": ").append(this.metaJson()).append(",\n");
        json.append("  \"warm_verify\": ").append(this.warmVerifyJson()).append(",\n");
        json.append("  \"variants\": {\n");
        for (int v = 0; v < this.measurements.variants().size(); ++v) {
            json.append("    ").append(quote(this.measurements.variants().get(v))).append(": ").append(this.variantJson(v));
            json.append(v + 1 < this.measurements.variants().size() ? ",\n" : "\n");
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
        meta.append(", \"dimension\": ").append(quote(this.options.dimension));
        meta.append(", \"world_seeds\": ").append(java.util.Arrays.toString(this.options.seeds));
        meta.append(", \"threads\": ").append(m.threads());
        meta.append(", \"warmup_seconds\": ").append(this.options.warmupSeconds);
        meta.append(", \"warmup_rounds\": ").append(m.warmupRounds());
        meta.append(", \"rounds\": ").append(this.options.rounds);
        meta.append(", \"chunks_per_round\": ").append(m.chunkCount());
        meta.append(", \"chunks_with_structure\": ").append(this.workload.structureCount());
        meta.append(", \"random_chunks_per_seed\": ").append(this.options.chunks);
        meta.append(", \"square_side\": ").append(this.options.square);
        meta.append(", \"far_chunks_per_seed\": ").append(this.options.far);
        meta.append(", \"beard_every\": ").append(this.options.beardEvery);
        meta.append(", \"verify_fuzz_chunks\": ").append(this.options.fuzz);
        meta.append(", \"verify_columns\": ").append(this.options.columns);
        meta.append(", \"verify_boxes\": ").append(this.options.boxes);
        meta.append(", \"workload_seed\": ").append(this.options.workloadSeed);
        meta.append(", \"sink\": ").append(m.sink());
        meta.append(", \"variant_order\": \"round r runs variant (r + i) mod V for i = 0..V-1\"");
        meta.append("}");
        return meta.toString();
    }

    private String warmVerifyJson() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < this.measurements.warmVerify().size(); ++i) {
            Verifier.Tally tally = this.measurements.warmVerify().get(i);
            json.append(i == 0 ? "" : ", ");
            json.append("{\"variant\": ").append(quote(tally.candidate()));
            json.append(", \"passed\": ").append(tally.passed());
            json.append(", \"chunks\": ").append(tally.chunks());
            json.append(", \"mismatches\": ").append(tally.mismatches());
            json.append(", \"both_nan\": ").append(tally.bothNan()).append("}");
        }
        return json.append("]").toString();
    }

    private String variantJson(int variant) {
        Measurements m = this.measurements;
        return "{\"ns_per_chunk\": " + array(m.nsPerChunk()[variant]) + ", \"chunks_per_second\": " + array(m.chunksPerSecond()[variant]) + "}";
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
