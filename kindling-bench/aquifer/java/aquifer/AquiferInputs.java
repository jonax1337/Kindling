package aquifer;

import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** The arguments of Aquifer.Config.create (NoiseChunk, A:65-72), the single place an aquifer is built. */
record AquiferInputs(DensitySamplerSet samplers, Aquifer.Config config, PositionalRandomFactory random, DensityVolume volume,
                     Aquifer.FluidPicker picker) {

    /** Mojang's class, exactly as the server builds it. */
    Aquifer vanilla() {
        return this.config.create(this.samplers, this.random, this.volume, this.picker);
    }
}
