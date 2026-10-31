package densitycompiler;

import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;

/** Paper's own sampler from its own DensityFunctionCompiler, with or without the context caches. */
final class PaperVariant implements Variant {
    private final String name;
    private final boolean caches;

    private PaperVariant(String name, boolean caches) {
        this.name = name;
        this.caches = caches;
    }

    /** The reference: real compiler, real caches, real arena. */
    static PaperVariant cached() {
        return new PaperVariant("PAPER", true);
    }

    /** Same sampler without caches; must equal PAPER bit for bit, which shows Paper's caches are value-transparent. */
    static PaperVariant uncached() {
        return new PaperVariant("PAPER_UNCACHED", false);
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public boolean cachesEnabled() {
        return this.caches;
    }

    @Override
    public boolean benchmarkedByDefault() {
        return this.caches;
    }

    @Override
    public DensitySampler sampler(World world) {
        return world.paperSampler();
    }
}
