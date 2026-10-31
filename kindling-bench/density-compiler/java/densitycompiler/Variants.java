package densitycompiler;

import java.util.ArrayList;
import java.util.List;
import densitycompiler.eval.Tuning;

/** The registry of variants. The first one is the reference every other variant is verified against. */
final class Variants {
    private static List<Variant> registered = registered(Tuning.DEFAULT);

    private Variants() {
    }

    /** Sets the cost model of the registered COMPILED variants (--tuning); call before anything else uses them. */
    static void configure(Tuning tuning) {
        registered = registered(tuning);
    }

    /** The COMPILED variants that depend on the tuning: only pruning decides between box and run leaf calls. */
    static List<CompiledVariant> compiled(Tuning tuning) {
        return List.of(CompiledVariant.dense(tuning), CompiledVariant.pruned(tuning), CompiledVariant.tiled(tuning), CompiledVariant.full(tuning));
    }

    private static List<Variant> registered(Tuning tuning) {
        List<Variant> variants = new ArrayList<>(List.of(PaperVariant.cached(), PaperVariant.uncached()));
        variants.addAll(compiled(tuning));
        return List.copyOf(variants);
    }

    static Variant reference() {
        return registered.getFirst();
    }

    /** Everything that is checked against the reference. */
    static List<Variant> candidates() {
        return registered.subList(1, registered.size());
    }

    /** The variants bench times: the --variants selection, or the registered ones that are benchmarked by default. */
    static List<Variant> benchmarked(List<String> names) {
        if (names == null) {
            return registered.stream().filter(Variant::benchmarkedByDefault).toList();
        }
        return names.stream().map(Variants::byName).toList();
    }

    private static Variant byName(String name) {
        return registered.stream()
            .filter(variant -> variant.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown variant " + name));
    }
}
