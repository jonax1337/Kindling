package previewchunks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Combines the forks of a bench run: per fork the medians and the per-round ratio, then the median and range over forks. */
final class Report {
    private record Fork(String name, int threads, double terrain, double preview, double ratio) {
    }

    private Report() {
    }

    static void run(Path dir) throws IOException {
        List<Fork> forks = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().matches("fork-.*\\.json")).sorted().toList()) {
                forks.add(read(file));
            }
        }
        if (forks.isEmpty()) {
            throw new IllegalArgumentException("no fork-*.json in " + dir);
        }
        forks.forEach(fork -> System.out.printf("%-16s threads %d  TERRAIN %.3f ms  PREVIEW %.3f ms  ratio %.3f%n",
            fork.name(), fork.threads(), fork.terrain(), fork.preview(), fork.ratio()));
        double[] ratios = forks.stream().mapToDouble(Fork::ratio).toArray();
        System.out.printf("over %d forks: TERRAIN %.3f ms, PREVIEW %.3f ms, TERRAIN / PREVIEW %.3f (range %.3f to %.3f)%n", forks.size(),
            Stats.median(forks.stream().mapToDouble(Fork::terrain).toArray()), Stats.median(forks.stream().mapToDouble(Fork::preview).toArray()),
            Stats.median(ratios), Stats.percentile(ratios, 0), Stats.percentile(ratios, 100));
    }

    private static Fork read(Path file) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        List<Double> terrain = new ArrayList<>();
        List<Double> preview = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("rounds")) {
            JsonObject round = element.getAsJsonObject();
            (round.get("variant").getAsString().equals("TERRAIN") ? terrain : preview).add(round.get("msPerChunk").getAsDouble());
        }
        double[] ratios = new double[terrain.size()];
        for (int i = 0; i < ratios.length; i++) {
            ratios[i] = terrain.get(i) / preview.get(i);
        }
        return new Fork(file.getFileName().toString(), json.get("threads").getAsInt(), Stats.median(toArray(terrain)),
            Stats.median(toArray(preview)), Stats.median(ratios));
    }

    private static double[] toArray(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
