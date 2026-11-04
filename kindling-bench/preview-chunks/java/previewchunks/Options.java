package previewchunks;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.level.ChunkPos;

/** The command line: a command followed by --name value pairs. */
record Options(String command, Map<String, String> values) {

    static Options parse(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("usage: verify | bench | report | packets [--name value ...]");
        }
        Map<String, String> values = new HashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("expected --name value, got " + String.join(" ", Arrays.copyOfRange(args, i, args.length)));
            }
            values.put(args[i].substring(2), args[i + 1]);
        }
        return new Options(args[0], Map.copyOf(values));
    }

    long[] seeds() {
        return Arrays.stream(text("seeds", "1,2,3").split(",")).mapToLong(Long::parseLong).toArray();
    }

    int chunksPerSeed() {
        return number("chunks", 200);
    }

    /** Fixed chunk positions instead of the random sample, as "x,z;x,z". */
    Optional<List<ChunkPos>> at() {
        return Optional.ofNullable(this.values.get("at")).map(text -> Arrays.stream(text.split(";"))
            .map(pair -> pair.split(","))
            .map(pair -> new ChunkPos(Integer.parseInt(pair[0].trim()), Integer.parseInt(pair[1].trim())))
            .toList());
    }

    /** Chunk positions are drawn from a square of this many chunks around 0, 0 in each direction. */
    int radius() {
        return number("radius", 2000);
    }

    Margins margins() {
        Margins d = Margins.DEFAULT;
        return new Margins(number("above", d.above()), number("below", d.below()), number("skin", d.skinDepth()), number("grow", d.growStep()));
    }

    Terrain.PossibleBiomes possibleBiomes() {
        return Terrain.PossibleBiomes.valueOf(text("possible-biomes", "none").toUpperCase(Locale.ROOT));
    }

    int threads() {
        return number("threads", 1);
    }

    int warmupRounds() {
        return number("warmup-rounds", 3);
    }

    int rounds() {
        return number("rounds", 30);
    }

    Path out() {
        return Path.of(text("out", "results/fork.json"));
    }

    Path dir() {
        return Path.of(text("dir", "results"));
    }

    Optional<Path> world() {
        return Optional.ofNullable(this.values.get("world")).map(Path::of);
    }

    long worldSeed() {
        return Long.parseLong(required("world-seed"));
    }

    int limit() {
        return number("limit", 500);
    }

    private int number(String name, int fallback) {
        return Integer.parseInt(text(name, Integer.toString(fallback)));
    }

    private String text(String name, String fallback) {
        return this.values.getOrDefault(name, fallback);
    }

    private String required(String name) {
        String value = this.values.get(name);
        if (value == null) {
            throw new IllegalArgumentException("--" + name + " is required");
        }
        return value;
    }
}
