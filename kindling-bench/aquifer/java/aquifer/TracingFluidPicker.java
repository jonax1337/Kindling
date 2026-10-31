package aquifer;

import net.minecraft.world.level.levelgen.Aquifer;

/** Logs every fluid picker call: position, level and the identity of the fluid state it returned. */
record TracingFluidPicker(Aquifer.FluidPicker real, EventLog log) implements Aquifer.FluidPicker {
    @Override
    public Aquifer.FluidStatus computeFluid(int blockX, int blockY, int blockZ) {
        Aquifer.FluidStatus status = this.real.computeFluid(blockX, blockY, blockZ);
        this.log.add(TracingSamplerSet.pack(blockX, blockY), TracingSamplerSet.pack(blockZ, status.fluidLevel()),
            System.identityHashCode(status.fluidType()));
        return status;
    }
}
