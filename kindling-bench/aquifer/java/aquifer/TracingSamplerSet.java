package aquifer;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * A sampler set that logs every sampleValue and sampleVolume call with its arguments and result bits, then
 * forwards it. Functions get a fixed id from their position in the Aquifer.Config, so ids do not depend on the
 * order an aquifer asks for them.
 */
final class TracingSamplerSet implements DensitySamplerSet {
    private static final long VOLUME_EVENT = 1L << 40;
    private static final int UNKNOWN_FUNCTION_ID = 99;

    private final DensitySamplerSet inner;
    private final EventLog log;
    private final Map<DensityFunction, Integer> ids = new IdentityHashMap<>();

    TracingSamplerSet(DensitySamplerSet inner, Aquifer.Config config, EventLog log) {
        this.inner = inner;
        this.log = log;
        this.ids.put(config.barrierNoise(), 0);
        this.ids.put(config.fluidLevelFloodednessNoise(), 1);
        this.ids.put(config.fluidLevelSpreadNoise(), 2);
        this.ids.put(config.lavaNoise(), 3);
        this.ids.put(config.exclusion(), 4);
        this.ids.put(config.surfaceLevel(), 5);
    }

    @Override
    public DensitySampler.Bound get(DensityFunction function) {
        DensitySampler.Bound real = this.inner.get(function);
        int id = this.ids.getOrDefault(function, UNKNOWN_FUNCTION_ID);
        return new DensitySampler.Bound(new TracingSampler(id, real.sampler(), this.log), real.context());
    }

    private record TracingSampler(int id, DensitySampler real, EventLog log) implements DensitySampler {
        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float value = this.real.sampleValue(context, blockX, blockY, blockZ);
            this.log.add(this.id, pack(blockX, blockY), pack(blockZ, Float.floatToRawIntBits(value)));
            return value;
        }

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            this.real.sampleVolume(context, outputBuffer, volume);
            long checksum = 0L;
            for (int i = 0; i < volume.size(); ++i) {
                checksum = checksum * 31L + Float.floatToRawIntBits(outputBuffer.get(i));
            }
            this.log.add(VOLUME_EVENT | this.id, pack(volume.minBlockX(), volume.minBlockZ()), pack(volume.size(), 0) ^ checksum);
        }
    }

    static long pack(int high, int low) {
        return (long)high << 32 | (low & 0xFFFFFFFFL);
    }
}
