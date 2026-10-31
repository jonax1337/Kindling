package aquifer;

import aquifer.broken.BrokenC2meAquifer;
import aquifer.candidate.BaseAquifer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Proves the verifier can fail. The registered candidates must pass; every deliberately broken variant must be
 * rejected, the trace-only ones by the trace check alone. A few mutants change values so rarely that the replay may not
 * reach them: those are rejected by the part checks (exact comparison of the pure pieces against Mojang's), and the
 * replay's verdict on them is only reported. Two single-token mutants are equivalent to the original and are proven so.
 */
final class SelfTest {
    private enum Expectation { CAUGHT_BY_RESULTS, CAUGHT_BY_ANY_CHECK, CAUGHT_BY_TRACE_ONLY, CAUGHT_BY_PARTS, REPORTED }

    private record Mutant(Variant variant, Expectation expectation, PartChecks.Similarity similarity) {
        Mutant(Variant variant, Expectation expectation) {
            this(variant, expectation, BaseAquifer::similarity);
        }
    }

    private final World world;
    private final Options options;

    SelfTest(World world, Options options) {
        this.world = world;
        this.options = options;
    }

    boolean run() {
        Options small = this.smallCaseOptions();
        List<ChunkCase> cases = new ArrayList<>(Workload.build(List.of(this.world), small).cases());
        cases.addAll(Workload.synthetic(this.world, small.workloadSeed));
        System.out.printf("self-test: %d cases on %s (including the deep-sea and sea-level -10 worlds)%n", cases.size(), this.world);
        boolean ok = this.candidatesPass(cases);
        for (Mutant mutant : mutants()) {
            ok &= this.isCaught(mutant, cases);
        }
        ok &= this.equivalentMutantsProven();
        return ok;
    }

    private static List<Mutant> mutants() {
        List<Mutant> mutants = new ArrayList<>();
        for (BrokenAquifer.Defect defect : BrokenAquifer.Defect.values()) {
            mutants.add(new Mutant(new BrokenVariant(defect), defect.traceOnly() ? Expectation.CAUGHT_BY_TRACE_ONLY : Expectation.CAUGHT_BY_RESULTS));
        }
        mutants.add(candidateMutant(BrokenC2meAquifer.Defect.TIE_OLDER_WINS, Expectation.CAUGHT_BY_RESULTS));
        mutants.add(candidateMutant(BrokenC2meAquifer.Defect.C2ME_TIEBREAK, Expectation.CAUGHT_BY_ANY_CHECK));
        mutants.add(candidateMutant(BrokenC2meAquifer.Defect.C2ME_FOURTH_SLOT, Expectation.CAUGHT_BY_RESULTS));
        mutants.add(candidateMutant(BrokenC2meAquifer.Defect.STALE_BARRIER, Expectation.CAUGHT_BY_RESULTS));
        mutants.add(candidateMutant(BrokenC2meAquifer.Defect.FLAG_KEPT, Expectation.CAUGHT_BY_RESULTS));
        mutants.add(candidateMutant(BrokenC2meAquifer.Defect.STATUS3_FIRST, Expectation.CAUGHT_BY_TRACE_ONLY));
        mutants.add(candidateMutant(BrokenC2meAquifer.Defect.NO_BLOCKPOS_WRAP, Expectation.CAUGHT_BY_PARTS));
        mutants.add(candidateMutant(BrokenC2meAquifer.Defect.SPREAD_DOUBLE, Expectation.REPORTED));
        mutants.add(candidateMutant(BrokenC2meAquifer.Defect.C2ME_FLOOD_ALGEBRA, Expectation.REPORTED));
        mutants.add(new Mutant(brokenCandidate(BrokenC2meAquifer.Defect.SIMILARITY_MUL.name(), EnumSet.of(BrokenC2meAquifer.Defect.SIMILARITY_MUL)),
            Expectation.CAUGHT_BY_PARTS, BrokenC2meAquifer::similarityMultiplied));
        mutants.add(candidateMutant(BrokenC2meAquifer.Defect.LAVA_LEVEL_LT, Expectation.CAUGHT_BY_PARTS));
        mutants.add(new Mutant(brokenCandidate("C2ME_LITERAL", BrokenC2meAquifer.c2meAsWritten()), Expectation.CAUGHT_BY_RESULTS));
        return mutants;
    }

    private static Mutant candidateMutant(BrokenC2meAquifer.Defect defect, Expectation expectation) {
        return new Mutant(brokenCandidate(defect.name(), EnumSet.of(defect)), expectation);
    }

    private static Variant brokenCandidate(String name, Set<BrokenC2meAquifer.Defect> defects) {
        return CandidateVariant.broken(name, (samplers, config, random, volume, picker) ->
            BrokenC2meAquifer.create(defects, samplers, config, random, volume, picker));
    }

    private boolean candidatesPass(List<ChunkCase> cases) {
        Verifier verifier = new Verifier(Variants.reference(), Variants.candidates());
        verifier.verify(cases);
        boolean passed = Verifier.allPassed(verifier.results());
        List<String> names = Variants.candidates().stream().map(Variant::name).toList();
        System.out.printf("  candidates %s %s%n", names, passed ? "pass (expected)" : "FAILED (they must pass)");
        for (Variant candidate : Variants.candidates()) {
            PartChecks.Result parts = PartChecks.run(this.world, candidate, BaseAquifer::similarity);
            if (parts != null) {
                System.out.printf("  %-24s part checks %s%s%n", candidate.name(), parts.passed() ? "pass (expected): " : "FAILED (they must pass): ", parts.summary());
                passed &= parts.passed();
            }
        }
        return passed;
    }

    private boolean isCaught(Mutant mutant, List<ChunkCase> cases) {
        Verifier verifier = new Verifier(Variants.reference(), List.of(mutant.variant()));
        verifier.verify(cases);
        long results = verifier.results().stream().mapToLong(t -> t.resultMismatches() + t.flagMismatches() + t.errors()).sum();
        long traces = verifier.results().stream().mapToLong(t -> t.samplerTraceMismatches() + t.pickerTraceMismatches()).sum();
        long partDifferences = 0;
        if (mutant.expectation() == Expectation.CAUGHT_BY_PARTS) {
            PartChecks.Result parts = PartChecks.run(this.world, mutant.variant(), mutant.similarity());
            partDifferences = parts == null ? 0 : parts.differences();
        }
        boolean ok = switch (mutant.expectation()) {
            case CAUGHT_BY_RESULTS -> results > 0;
            case CAUGHT_BY_ANY_CHECK -> results + traces > 0;
            case CAUGHT_BY_TRACE_ONLY -> results == 0 && traces > 0;
            case CAUGHT_BY_PARTS -> partDifferences > 0;
            case REPORTED -> true;
        };
        String parts = mutant.expectation() == Expectation.CAUGHT_BY_PARTS ? String.format(", part-check differences %d", partDifferences) : "";
        System.out.printf("  %-24s %s (result/flag differences %d, trace differences %d%s)%n", mutant.variant().name(),
            verdict(mutant.expectation(), ok, results + traces > 0), results, traces, parts);
        return ok;
    }

    private boolean equivalentMutantsProven() {
        List<String> failures = PartChecks.equivalentMutantProofFailures();
        System.out.printf("  %-24s %s%n", "LAVA_NOISE_GE, GRADIENT_BOUND",
            failures.isEmpty() ? "proven equivalent to the original (no float widens to 0.3; the gradient is never exactly +-2.0)" : "NOT EQUIVALENT: " + failures);
        return failures.isEmpty();
    }

    private static String verdict(Expectation expectation, boolean ok, boolean caught) {
        if (expectation == Expectation.REPORTED) {
            return caught ? "rejected (reported only)" : "not caught by this data (reported only)";
        }
        if (ok) {
            return expectation == Expectation.CAUGHT_BY_PARTS ? "rejected by the part checks (expected)" : "rejected (expected)";
        }
        return caught ? "REJECTED BY THE WRONG CHECK" : "NOT CAUGHT";
    }

    private Options smallCaseOptions() {
        if (this.options.sizeGiven) {
            return this.options;
        }
        Options small = new Options();
        small.workloadSeed = this.options.workloadSeed;
        small.chunks = 1;
        small.columns = 3;
        small.shuffles = 1;
        small.edge = false;
        return small;
    }
}
