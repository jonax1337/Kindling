package net.kindlingmc.worldgen.density;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.densityfunction.CachingDensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import org.slf4j.Logger;

/**
 * Runs the compiled sampler on a copy of the context (same user fields and arena, its own cache cells) and Paper's on
 * the real one, compares every output float and every cache cell the compiled graph keeps bit for bit, logs where they
 * differ and keeps Paper's result and Paper's context state. Turned on with {@code -Dkindling.verifyDensity=true}.
 */
final class CheckedDensitySampler implements DensitySampler {
    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final long DIFFERENCES_LOGGED = 20;
    private static final long SUMMARY_EVERY_CALLS = 10_000;
    private static final AtomicLong CHECKED = new AtomicLong();
    private static final AtomicLong DIFFERENT = new AtomicLong();

    private final CompiledFinalDensity compiled;

    CheckedDensitySampler(CompiledFinalDensity compiled) {
        this.compiled = compiled;
    }

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        SamplerContext copy = context.kindling$copy(context.kindling$cachesEnabled());
        DensityVolume[] before = this.cellVolumes(context);
        try (ScopedDensityBuffer mine = context.acquireBuffer(volume)) {
            this.compiled.sampleVolume(copy, mine, volume);
            this.compiled.paper.sampleVolume(context, outputBuffer, volume);
            boolean same = compareOutput(volume, mine, outputBuffer) & this.compareCells(volume, copy, context, before);
            if (!same) {
                DIFFERENT.incrementAndGet();
            }
        }
        long checked = CHECKED.incrementAndGet();
        if (checked % SUMMARY_EVERY_CALLS == 0) {
            LOGGER.info("Density check: {} calls, {} with differences", checked, DIFFERENT.get());
        }
    }

    @Override
    public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
        return this.compiled.paper.sampleValue(context, blockX, blockY, blockZ);
    }

    private static boolean compareOutput(DensityVolume volume, DensityBuffer compiled, DensityBuffer paper) {
        for (int i = 0; i < volume.size(); ++i) {
            if (!sameValue(compiled.values[i], paper.values[i])) {
                report("Density differs at {} in {}: compiled {}, vanilla {}", position(volume, i), volume, compiled.values[i], paper.values[i]);
                return false;
            }
        }
        return true;
    }

    private DensityVolume[] cellVolumes(SamplerContext context) {
        DensityVolume[] volumes = new DensityVolume[this.compiled.caches.length];
        for (int i = 0; i < volumes.length; ++i) {
            volumes[i] = context.kindling$cellVolume(this.compiled.caches[i].id());
        }
        return volumes;
    }

    /**
     * The compiled call starts on a copy without cache cells, Paper's on the real context, which may already hold cells
     * from earlier calls (the aquifer's, the surface's). A cell Paper's call left as it was must be one the compiled call
     * did not touch either; every other cell must hold the same volume and values on both sides.
     */
    private boolean compareCells(DensityVolume volume, SamplerContext compiledContext, SamplerContext paperContext, DensityVolume[] before) {
        for (int c = 0; c < before.length; ++c) {
            int id = this.compiled.caches[c].id();
            DensityVolume compiledVolume = compiledContext.kindling$cellVolume(id);
            DensityVolume paperVolume = paperContext.kindling$cellVolume(id);
            if (compiledVolume == null && paperVolume != null && paperVolume == before[c]) {
                continue;
            }
            if (!Objects.equals(compiledVolume, paperVolume)) {
                report("Density cache cell {} holds {} after the compiled call of {}, vanilla's holds {}", id, compiledVolume, volume, paperVolume);
                return false;
            }
            if (paperVolume == null) {
                continue;
            }
            DensityBuffer compiledCell = compiledContext.kindling$completedCell(id);
            DensityBuffer paperCell = paperContext.kindling$completedCell(id);
            for (int i = 0; i < paperVolume.size(); ++i) {
                if (!sameValue(compiledCell.values[i], paperCell.values[i])) {
                    report("Density cache cell {} differs at {} after the call of {}: compiled {}, vanilla {}", id, position(paperVolume, i), volume,
                        compiledCell.values[i], paperCell.values[i]);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Bit for bit; two NaNs count as equal whatever their payload, which Java leaves unspecified (the JIT may order a
     * commutative operation either way). No comparison the game makes can tell them apart.
     */
    private static boolean sameValue(float compiled, float paper) {
        return Float.floatToRawIntBits(compiled) == Float.floatToRawIntBits(paper) || Float.isNaN(compiled) && Float.isNaN(paper);
    }

    private static String position(DensityVolume volume, int index) {
        int y = index % volume.sizeY();
        int column = index / volume.sizeY();
        int x = column % volume.sizeX();
        int z = column / volume.sizeX();
        return volume.blockX(x) + " " + volume.blockY(y) + " " + volume.blockZ(z);
    }

    private static void report(String message, Object... arguments) {
        if (DIFFERENT.get() < DIFFERENCES_LOGGED) {
            LOGGER.warn(message, arguments);
        }
    }

    /** Calls checked and calls with a difference so far (tests). */
    static long[] counts() {
        return new long[] {CHECKED.get(), DIFFERENT.get()};
    }
}
