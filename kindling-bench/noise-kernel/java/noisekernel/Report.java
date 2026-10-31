package noisekernel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Console table and JSON file for one benchmark run. */
final class Report {
    private static final String BASELINE = "PAPER";
    private static final String CALL_OVERHEAD_VARIANT = "NATIVE_NOOP";

    private final Measurements measurements;
    private final Options options;
    private final Workload workload;
    private final Integer simdLevel;

    Report(Measurements measurements, Options options, Workload workload, Integer simdLevel) {
        this.measurements = measurements;
        this.options = options;
        this.workload = workload;
        this.simdLevel = simdLevel;
    }

    void print() {
        Measurements m = this.measurements;
        System.out.printf("%n%d rounds, %d thread(s), %d warmup rounds; samples per round: perlin %d, smeared %d (%d + %d calls)%n",
            m.rounds(), m.threads(), m.warmupRounds(), m.perlinSamples(), m.smearedSamples(), m.perlinCalls(), m.smearedCalls());
        System.out.printf("%-14s %-9s %10s %10s %10s %10s%n", "variant", "kind", "median", "min", "max", "speedup");
        for (int part = 0; part < Measurements.PART_NAMES.length; ++part) {
            for (int v = 0; v < m.variants().size(); ++v) {
                this.printRow(v, part);
            }
        }
        this.printCallOverhead();
        System.out.printf(Locale.ROOT, "ns per output sample; speedup = median(%s) / median(variant); checksum %.6e%n", BASELINE, m.checksum());
    }

    private void printRow(int variant, int part) {
        Measurements m = this.measurements;
        String name = m.variants().get(variant);
        Stats stats = Stats.of(m.nsPerSample()[variant][part]);
        String speedup = name.equals(CALL_OVERHEAD_VARIANT) ? "n/a" : String.format(Locale.ROOT, "%.2fx", this.speedup(variant, part));
        System.out.printf(Locale.ROOT, "%-14s %-9s %10.3f %10.3f %10.3f %10s%n",
            name, Measurements.PART_NAMES[part], stats.median(), stats.min(), stats.max(), speedup);
    }

    private void printCallOverhead() {
        int noop = this.measurements.variants().indexOf(CALL_OVERHEAD_VARIANT);
        if (noop >= 0) {
            Stats stats = Stats.of(this.measurements.nsPerCall()[noop]);
            System.out.printf(Locale.ROOT, "%s: %.1f ns per call (median), min %.1f, max %.1f%n",
                CALL_OVERHEAD_VARIANT, stats.median(), stats.min(), stats.max());
        }
    }

    private double speedup(int variant, int part) {
        int baseline = this.measurements.variants().indexOf(BASELINE);
        double paper = Stats.of(this.measurements.nsPerSample()[baseline][part]).median();
        return paper / Stats.of(this.measurements.nsPerSample()[variant][part]).median();
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
        StringBuilder json = new StringBuilder(1 << 16);
        json.append("{\n");
        json.append("  \"meta\": ").append(this.metaJson()).append(",\n");
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
        meta.append("\"java_version\": ").append(quote(System.getProperty("java.version") + " " + System.getProperty("java.vm.name")));
        meta.append(", \"os\": ").append(quote(System.getProperty("os.name") + " " + System.getProperty("os.version")));
        meta.append(", \"arch\": ").append(quote(System.getProperty("os.arch")));
        meta.append(", \"available_processors\": ").append(Runtime.getRuntime().availableProcessors());
        meta.append(", \"kn_simd_level\": ").append(this.simdLevel == null ? "null" : this.simdLevel.toString());
        meta.append(", \"vector_species\": ").append(quote(VectorKernel.speciesName()));
        meta.append(", \"vector_lanes\": ").append(VectorKernel.laneCount());
        meta.append(", \"threads\": ").append(m.threads());
        meta.append(", \"warmup_seconds\": ").append(this.options.warmupSeconds);
        meta.append(", \"warmup_rounds\": ").append(m.warmupRounds());
        meta.append(", \"rounds\": ").append(m.rounds());
        meta.append(", \"seed\": ").append(this.options.seed);
        meta.append(", \"chunks\": ").append(this.workload.chunkCount);
        meta.append(", \"calls_perlin\": ").append(m.perlinCalls());
        meta.append(", \"calls_smeared\": ").append(m.smearedCalls());
        meta.append(", \"samples_perlin\": ").append(m.perlinSamples());
        meta.append(", \"samples_smeared\": ").append(m.smearedSamples());
        meta.append(", \"checksum\": ").append(m.checksum());
        meta.append(", \"variant_order\": \"round r runs variant (r + i) mod V for i = 0..V-1\"");
        meta.append("}");
        return meta.toString();
    }

    private String variantJson(int variant) {
        Measurements m = this.measurements;
        StringBuilder json = new StringBuilder("{");
        for (int part = 0; part < Measurements.PART_NAMES.length; ++part) {
            json.append(quote("ns_per_sample_" + Measurements.PART_NAMES[part])).append(": ").append(array(m.nsPerSample()[variant][part])).append(", ");
        }
        json.append("\"ns_per_call\": ").append(array(m.nsPerCall()[variant])).append("}");
        return json.toString();
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
