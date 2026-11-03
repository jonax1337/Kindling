package previewchunks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.level.ChunkPos;

/** The chunks a run generates: for each seed, the same fixed pseudo-random sample of chunk positions within a square. */
record Workload(List<Task> tasks) {
    private static final long SAMPLE_SEED = 0x5eed_c0deL;

    /** One chunk of one world, with that world's generators. */
    record Task(Terrain terrain, Preview preview, ChunkPos pos) {
        @Override
        public String toString() {
            return "seed " + this.terrain.world().seed() + " chunk (" + this.pos.x() + ", " + this.pos.z() + ")";
        }
    }

    static Workload create(Vanilla vanilla, Options options) {
        List<Task> tasks = new ArrayList<>();
        for (long seed : options.seeds()) {
            Terrain terrain = new Terrain(World.create(vanilla, seed), options.possibleBiomes());
            Preview preview = new Preview(terrain, options.margins());
            for (ChunkPos pos : options.at().orElseGet(() -> sample(seed, options))) {
                tasks.add(new Task(terrain, preview, pos));
            }
        }
        return new Workload(List.copyOf(tasks));
    }

    private static List<ChunkPos> sample(long seed, Options options) {
        Random random = new Random(SAMPLE_SEED ^ seed);
        int span = 2 * options.radius() + 1;
        List<ChunkPos> positions = new ArrayList<>();
        for (int i = 0; i < options.chunksPerSeed(); i++) {
            positions.add(new ChunkPos(random.nextInt(span) - options.radius(), random.nextInt(span) - options.radius()));
        }
        return positions;
    }
}
