package aquifer;

import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** A variant built from a candidate class's constructor, which takes exactly the arguments of Aquifer.Config.create. */
final class CandidateVariant implements Variant {
    /** The shape of Config.create, so a candidate is a drop-in replacement for it. */
    interface Factory {
        Aquifer create(DensitySamplerSet samplers, Aquifer.Config config, PositionalRandomFactory random, DensityVolume volume,
                       Aquifer.FluidPicker picker);
    }

    private final String name;
    private final boolean benchmarkedByDefault;
    private final Factory factory;

    private CandidateVariant(String name, boolean benchmarkedByDefault, Factory factory) {
        this.name = name;
        this.benchmarkedByDefault = benchmarkedByDefault;
        this.factory = factory;
    }

    static CandidateVariant benchmarked(String name, Factory factory) {
        return new CandidateVariant(name, true, factory);
    }

    /** Self-test only: a deliberately broken candidate. */
    static CandidateVariant broken(String name, Factory factory) {
        return new CandidateVariant(name, false, factory);
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public boolean benchmarkedByDefault() {
        return this.benchmarkedByDefault;
    }

    @Override
    public Aquifer create(AquiferInputs inputs) {
        return this.factory.create(inputs.samplers(), inputs.config(), inputs.random(), inputs.volume(), inputs.picker());
    }
}
