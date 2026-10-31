package aquifer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;

/**
 * Replays every case on the reference and on each candidate, each on its own aquifer, sampler context and logs,
 * and compares per call the returned BlockState (by identity, null included) and the update flag, and per phase
 * the sampler and fluid picker traces. Tallies are kept per candidate and label.
 */
final class Verifier {
    private final Variant reference;
    private final List<Variant> candidates;
    private final Map<String, Tally> tallies = new LinkedHashMap<>();

    Verifier(Variant reference, List<Variant> candidates) {
        this.reference = reference;
        this.candidates = candidates;
    }

    static boolean allPassed(List<Tally> tallies) {
        return !tallies.isEmpty() && tallies.stream().allMatch(Tally::passed);
    }

    List<Tally> results() {
        return List.copyOf(this.tallies.values());
    }

    void verify(List<ChunkCase> cases) {
        for (ChunkCase chunkCase : cases) {
            this.verify(chunkCase);
        }
    }

    void verify(ChunkCase chunkCase) {
        List<ChunkCase.Phase> phases = chunkCase.phases();
        Outcome expected = Outcome.play(this.reference, chunkCase, phases);
        for (Variant candidate : this.candidates) {
            this.compare(candidate, chunkCase, phases, expected);
        }
    }

    private void compare(Variant candidate, ChunkCase chunkCase, List<ChunkCase.Phase> phases, Outcome expected) {
        Outcome actual;
        try {
            actual = Outcome.play(candidate, chunkCase, phases);
        } catch (RuntimeException e) {
            for (ChunkCase.Phase phase : phases) {
                this.tally(candidate, chunkCase, phase).error(chunkCase + " threw " + e);
            }
            return;
        }
        for (int p = 0; p < phases.size(); ++p) {
            this.comparePhase(this.tally(candidate, chunkCase, phases.get(p)), chunkCase, phases.get(p).calls(), expected, actual, p);
        }
    }

    private void comparePhase(Tally tally, ChunkCase chunkCase, CallList calls, Outcome expected, Outcome actual, int p) {
        BlockState[] expectedStates = expected.states.get(p);
        BlockState[] actualStates = actual.states.get(p);
        boolean[] expectedFlags = expected.flags.get(p);
        boolean[] actualFlags = actual.flags.get(p);
        for (int i = 0; i < calls.size; ++i) {
            tally.countReference(expectedStates[i], expectedFlags[i]);
            if (expectedStates[i] != actualStates[i]) {
                tally.resultMismatch(describe(chunkCase, calls, i) + ": expected " + expectedStates[i] + " got " + actualStates[i]);
            }
            if (expectedFlags[i] != actualFlags[i]) {
                tally.flagMismatch(describe(chunkCase, calls, i) + ": expected flag " + expectedFlags[i] + " got " + actualFlags[i]);
            }
        }
        int[] expectedSamplerRange = segment(expected.samplerEnds, p);
        int[] expectedPickerRange = segment(expected.pickerEnds, p);
        tally.countReferenceEvents(expectedSamplerRange[1] - expectedSamplerRange[0], expectedPickerRange[1] - expectedPickerRange[0]);
        LogDifference sampler = LogDifference.between(expected.logs.samplers(), expectedSamplerRange, actual.logs.samplers(),
            segment(actual.samplerEnds, p));
        LogDifference picker = LogDifference.between(expected.logs.picker(), expectedPickerRange, actual.logs.picker(),
            segment(actual.pickerEnds, p));
        tally.samplerTraceMismatches(sampler.count(), chunkCase + " " + sampler.first());
        tally.pickerTraceMismatches(picker.count(), chunkCase + " " + picker.first());
    }

    /** The event index range of one phase: from the end of the previous phase to its own end. */
    private static int[] segment(int[] ends, int phase) {
        return new int[] {phase == 0 ? 0 : ends[phase - 1], ends[phase]};
    }

    private Tally tally(Variant candidate, ChunkCase chunkCase, ChunkCase.Phase phase) {
        String label = "[" + phase.label() + "] " + chunkCase.world().label();
        return this.tallies.computeIfAbsent(candidate.name() + "|" + label, key -> new Tally(label, candidate.name()));
    }

    private static String describe(ChunkCase chunkCase, CallList calls, int index) {
        return String.format("%s call %d at (%d, %d, %d) density %s", chunkCase, index, calls.x[index], calls.y[index], calls.z[index],
            calls.density[index]);
    }

    /** Where two logs first differ and how many events differ in the compared ranges (a length difference counts per missing event). */
    private record LogDifference(long count, String first) {
        static LogDifference between(EventLog expected, int[] expectedRange, EventLog actual, int[] actualRange) {
            int expectedLength = expectedRange[1] - expectedRange[0];
            int actualLength = actualRange[1] - actualRange[0];
            long count = Math.abs(expectedLength - actualLength);
            String first = count > 0 ? "length " + expectedLength + " expected, " + actualLength + " got" : "";
            for (int i = 0; i < Math.min(expectedLength, actualLength); ++i) {
                if (!expected.sameEvent(expectedRange[0] + i, actual, actualRange[0] + i)) {
                    if (first.isEmpty()) {
                        first = "event " + i + " expected " + expected.describe(expectedRange[0] + i) + " got " + actual.describe(actualRange[0] + i);
                    }
                    count++;
                }
            }
            return new LogDifference(count, first);
        }
    }

    /** What one variant did on a case: per phase the states and flags after every call, and where each phase ends in the logs. */
    private static final class Outcome {
        private final List<BlockState[]> states = new ArrayList<>();
        private final List<boolean[]> flags = new ArrayList<>();
        private final TraceLogs logs;
        private final int[] samplerEnds;
        private final int[] pickerEnds;

        private Outcome(TraceLogs logs, int phases) {
            this.logs = logs;
            this.samplerEnds = new int[phases];
            this.pickerEnds = new int[phases];
        }

        static Outcome play(Variant variant, ChunkCase chunkCase, List<ChunkCase.Phase> phases) {
            try (AquiferRun run = AquiferRun.open(chunkCase.world(), true)) {
                Aquifer aquifer = run.construct(variant, chunkCase.volume());
                if (!chunkCase.syntheticDensities()) {
                    run.fillDensityCaches(chunkCase.volume());
                }
                TraceLogs logs = run.trace();
                Outcome outcome = new Outcome(logs, phases.size());
                for (int p = 0; p < phases.size(); ++p) {
                    CallList calls = phases.get(p).calls();
                    BlockState[] states = new BlockState[calls.size];
                    boolean[] flags = new boolean[calls.size];
                    Replay.record(aquifer, calls, states, flags);
                    outcome.states.add(states);
                    outcome.flags.add(flags);
                    outcome.samplerEnds[p] = logs.samplers().count();
                    outcome.pickerEnds[p] = logs.picker().count();
                }
                return outcome;
            }
        }
    }
}
