package aquifer;

import net.minecraft.world.level.levelgen.Aquifer;

/**
 * Mojang's NoiseBasedAquifer through Aquifer.Config.create. Two instances with different names are registered:
 * the reference, and an independent copy that must verify against it (the harness sanity check; in bench the
 * pair shows the noise floor between two identical variants).
 */
final class VanillaVariant implements Variant {
    private final String name;

    private VanillaVariant(String name) {
        this.name = name;
    }

    static VanillaVariant reference() {
        return new VanillaVariant("VANILLA");
    }

    static VanillaVariant twin() {
        return new VanillaVariant("VANILLA_B");
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public boolean benchmarkedByDefault() {
        return true;
    }

    @Override
    public Aquifer create(AquiferInputs inputs) {
        return inputs.vanilla();
    }
}
