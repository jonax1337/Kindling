package aquifer;

import aquifer.candidate.C2meAquifer;
import aquifer.candidate.C2meBranchyAquifer;
import aquifer.candidate.PortAquifer;
import java.util.List;

/** The registry of variants. Adding a variant is one line in CANDIDATES. */
final class Variants {
    private static final Variant REFERENCE = VanillaVariant.reference();
    private static final List<Variant> CANDIDATES = List.of(
        VanillaVariant.twin(),
        CandidateVariant.benchmarked("PORT", PortAquifer::new),
        CandidateVariant.benchmarked("C2ME", C2meAquifer::filledEagerly),
        CandidateVariant.benchmarked("C2ME_LAZY", C2meAquifer::filledLazily),
        CandidateVariant.benchmarked("C2ME_BRANCHY", C2meBranchyAquifer::filledEagerly)
    );

    private Variants() {
    }

    static Variant reference() {
        return REFERENCE;
    }

    /** Everything verified against the reference. */
    static List<Variant> candidates() {
        return CANDIDATES;
    }

    /** The variants bench runs: the given names, or the default set. Names may include the reference. */
    static List<Variant> benchmarked(List<String> names) {
        List<Variant> all = new java.util.ArrayList<>();
        all.add(REFERENCE);
        all.addAll(CANDIDATES);
        if (names == null) {
            return all.stream().filter(Variant::benchmarkedByDefault).toList();
        }
        return names.stream().map(name -> all.stream().filter(v -> v.name().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown variant " + name))).toList();
    }
}
