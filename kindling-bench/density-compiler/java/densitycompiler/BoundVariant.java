package densitycompiler;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;

/** A variant with its samplers built for every world, ready to run any chunk. One instance per thread. */
final class BoundVariant {
    private final Variant variant;
    private final Map<World, DensitySampler> samplers = new IdentityHashMap<>();

    BoundVariant(Variant variant, List<World> worlds) {
        this.variant = variant;
        for (World world : worlds) {
            this.samplers.put(world, variant.sampler(world));
        }
    }

    String name() {
        return this.variant.name();
    }

    int run(Chunk chunk) {
        return ChunkCall.run(this.samplers.get(chunk.world()), this.variant.cachesEnabled(), chunk);
    }

    void runInto(Chunk chunk, float[] target, float @Nullable [] probeTarget) {
        ChunkCall.runInto(this.samplers.get(chunk.world()), this.variant.cachesEnabled(), chunk, target, probeTarget);
    }

    DensitySampler sampler(World world) {
        return this.samplers.get(world);
    }
}
