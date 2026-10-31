package aquifer;

import net.minecraft.world.level.levelgen.Aquifer;

/** A variant that builds vanilla's aquifer and breaks it in one way. Only the self-test uses it. */
final class BrokenVariant implements Variant {
    private final BrokenAquifer.Defect defect;

    BrokenVariant(BrokenAquifer.Defect defect) {
        this.defect = defect;
    }

    BrokenAquifer.Defect defect() {
        return this.defect;
    }

    @Override
    public String name() {
        return this.defect.name();
    }

    @Override
    public boolean benchmarkedByDefault() {
        return false;
    }

    @Override
    public Aquifer create(AquiferInputs inputs) {
        return new BrokenAquifer(inputs.vanilla(), inputs, this.defect);
    }
}
