package noisekernel;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.synth.Noise;

/**
 * The recorded leaf calls of the overworld volume noises over a fixed set of chunk positions.
 * Every variant is applied to exactly this list.
 */
final class Workload {
    private static final int SPREAD_RANGE_CHUNKS = 1250;
    private static final int FAR_BLOCK = 30_000_016;

    /** One root noise sampled over one volume; its leaf calls are calls[firstCall, endCall). */
    record Group(String name, Noise root, double xzScale, double yScale, DensityVolume volume, int firstCall, int endCall) {
    }

    final KernelCall[] calls;
    final List<Group> groups;
    final List<Leaf> leaves;
    final int chunkCount;

    private Workload(KernelCall[] calls, List<Group> groups, List<Leaf> leaves, int chunkCount) {
        this.calls = calls;
        this.groups = groups;
        this.leaves = leaves;
        this.chunkCount = chunkCount;
    }

    static Workload build(long seed, int spreadChunks) {
        List<NoiseSources.Source> sources = NoiseSources.overworld(seed);
        List<int[]> origins = chunkOrigins(seed, spreadChunks);
        LeafWalker walker = new LeafWalker();
        List<KernelCall> calls = new ArrayList<>();
        List<Group> groups = new ArrayList<>();
        for (int[] origin : origins) {
            DensityVolume volume = new DensityVolume(5, 49, 5, origin[0], -64, origin[1], 4, 8, 4);
            for (NoiseSources.Source source : sources) {
                int first = calls.size();
                walker.record(source.noise(), volume, source.xzScale(), source.yScale(), 1.0F, calls);
                groups.add(new Group(source.name(), source.noise(), source.xzScale(), source.yScale(), volume, first, calls.size()));
            }
        }
        return new Workload(calls.toArray(KernelCall[]::new), List.copyOf(groups), walker.leaves(), origins.size());
    }

    KernelCall[] callsOf(Kind kind) {
        return java.util.Arrays.stream(this.calls).filter(call -> call.kind() == kind).toArray(KernelCall[]::new);
    }

    /** Chunk-aligned block origins: spread chunks within +/-20,000 blocks plus four chunks beyond +/-30,000,000. */
    private static List<int[]> chunkOrigins(long seed, int spreadChunks) {
        SplittableRandom random = new SplittableRandom(seed);
        List<int[]> origins = new ArrayList<>();
        for (int i = 0; i < spreadChunks; ++i) {
            int chunkX = random.nextInt(-SPREAD_RANGE_CHUNKS, SPREAD_RANGE_CHUNKS + 1);
            int chunkZ = random.nextInt(-SPREAD_RANGE_CHUNKS, SPREAD_RANGE_CHUNKS + 1);
            origins.add(new int[] {chunkX * 16, chunkZ * 16});
        }
        origins.add(new int[] {FAR_BLOCK, FAR_BLOCK});
        origins.add(new int[] {-FAR_BLOCK, FAR_BLOCK});
        origins.add(new int[] {FAR_BLOCK, -FAR_BLOCK});
        origins.add(new int[] {-FAR_BLOCK, -FAR_BLOCK});
        return origins;
    }
}
