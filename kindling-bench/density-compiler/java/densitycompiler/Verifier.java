package densitycompiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/**
 * Compares every candidate with the reference, float for float on the raw bits, per volume shape. NaN is allowed
 * only where the reference is NaN as well; such pairs are counted, and pairs with different NaN payloads separately.
 * Each call also yields the Probe values of its context, which are compared in a tally of their own.
 * Buffers are poisoned (see PoisonedArena), and the order alternates per chunk: even chunks run the reference first,
 * odd chunks last. A second pass repeats every volume on the same instances (see repeat).
 */
final class Verifier {
    private static final int NAN_BOUND = 0x7f800000;
    private static final int REPEATS_PER_VOLUME = 2;

    private final BoundVariant reference;
    private final List<BoundVariant> candidates;
    private final String scope;
    private final boolean repeatPass;
    private final Map<String, Tally> tallies = new LinkedHashMap<>();
    private float[] expected;
    private float[] expectedProbe;
    private float[][] actual;
    private float[][] actualProbe;

    /**
     * Takes bound variants so a bench thread can verify the very instances it timed; scope is added to the tally
     * names. With repeatPass, verify runs the list a second time on the same instances (see repeat).
     */
    Verifier(BoundVariant reference, List<BoundVariant> candidates, String scope, boolean repeatPass) {
        this.reference = reference;
        this.candidates = candidates;
        this.scope = scope;
        this.repeatPass = repeatPass;
    }

    /** Fresh instances of all variants, owned by the calling thread. */
    static Verifier fresh(List<World> worlds, Variant reference, List<Variant> candidates) {
        return fresh(worlds, reference, candidates, "");
    }

    static Verifier fresh(List<World> worlds, Variant reference, List<Variant> candidates, String scope) {
        return new Verifier(new BoundVariant(reference, worlds), candidates.stream().map(candidate -> new BoundVariant(candidate, worlds)).toList(), scope, true);
    }

    List<Tally> verify(List<Chunk> chunks) {
        this.allocate(chunks);
        this.tallies.clear();
        this.firstPass(chunks);
        if (this.repeatPass) {
            this.repeat(chunks);
        }
        return new ArrayList<>(this.tallies.values());
    }

    private void allocate(List<Chunk> chunks) {
        int candidateCount = this.candidates.size();
        int maxSize = chunks.stream().mapToInt(chunk -> chunk.volume().size()).max().orElse(0);
        int maxProbe = chunks.stream().mapToInt(Probe::length).max().orElse(0);
        this.expected = new float[maxSize];
        this.expectedProbe = new float[maxProbe];
        this.actual = new float[candidateCount][maxSize];
        this.actualProbe = new float[candidateCount][maxProbe];
    }

    /** Every volume once, in list order, with the probe: the first time each instance sees it. */
    private void firstPass(List<Chunk> chunks) {
        int candidateCount = this.candidates.size();
        for (int i = 0; i < chunks.size(); ++i) {
            Chunk chunk = chunks.get(i);
            boolean referenceLast = (i & 1) == 1;
            if (!referenceLast) {
                this.reference.runInto(chunk, this.expected, this.expectedProbe);
            }
            for (int c = 0; c < candidateCount; ++c) {
                this.candidates.get(c).runInto(chunk, this.actual[c], this.actualProbe[c]);
            }
            if (referenceLast) {
                this.reference.runInto(chunk, this.expected, this.expectedProbe);
            }
            for (int c = 0; c < candidateCount; ++c) {
                String name = this.candidates.get(c).name() + this.scope;
                this.tally(name + " [" + chunk.shape() + "]").add(chunk.toString(), chunk.volume(), this.expected, this.actual[c]);
                this.tally(name + " [probe]").add(chunk.toString(), Probe.layout(chunk), this.expectedProbe, this.actualProbe[c]);
            }
        }
    }

    /**
     * The same volumes again on the same instances, in reverse order, each one twice in a row by every candidate. The
     * server repeats volumes (iterateNoiseColumn builds the same column again and again), and an instance that
     * trusts state left by an earlier call (a cache cell of a finished context, a prepared tile) only shows up when
     * a volume it has seen before comes back.
     */
    private void repeat(List<Chunk> chunks) {
        int candidateCount = this.candidates.size();
        for (int i = chunks.size() - 1; i >= 0; --i) {
            Chunk chunk = chunks.get(i);
            this.reference.runInto(chunk, this.expected, null);
            for (int c = 0; c < candidateCount; ++c) {
                String name = this.candidates.get(c).name() + this.scope + " [repeat]";
                for (int run = 0; run < REPEATS_PER_VOLUME; ++run) {
                    this.candidates.get(c).runInto(chunk, this.actual[c], null);
                    this.tally(name).add(chunk.toString(), chunk.volume(), this.expected, this.actual[c]);
                }
            }
        }
    }

    private Tally tally(String label) {
        return this.tallies.computeIfAbsent(label, key -> new Tally(key, this.reference.name()));
    }

    static boolean allPassed(List<Tally> tallies) {
        return tallies.stream().allMatch(Tally::passed);
    }

    private static boolean isNan(int bits) {
        return (bits & 0x7fffffff) > NAN_BOUND;
    }

    /** First differing element, as block coordinates, for the error message. */
    record Mismatch(String where, int blockX, int blockY, int blockZ, int expectedBits, int actualBits) {
        @Override
        public String toString() {
            return String.format("%s block (%d, %d, %d): expected %s (0x%08x), got %s (0x%08x)", this.where, this.blockX, this.blockY,
                this.blockZ, Float.intBitsToFloat(this.expectedBits), this.expectedBits, Float.intBitsToFloat(this.actualBits), this.actualBits);
        }
    }

    /** Result of comparing one candidate over many chunks. */
    static final class Tally {
        private final String candidate;
        private final String reference;
        private int chunks;
        private int chunksWithMismatch;
        private long elements;
        private long mismatches;
        private long bothNan;
        private long nanPayloadDiffers;
        private Mismatch first;

        Tally(String candidate, String reference) {
            this.candidate = candidate;
            this.reference = reference;
        }

        void add(String where, DensityVolume volume, float[] expected, float[] actual) {
            long before = this.mismatches;
            int size = volume.size();
            for (int i = 0; i < size; ++i) {
                int expectedBits = Float.floatToRawIntBits(expected[i]);
                int actualBits = Float.floatToRawIntBits(actual[i]);
                if (expectedBits != actualBits) {
                    this.classifyDifference(where, volume, i, expectedBits, actualBits);
                } else if (isNan(expectedBits)) {
                    ++this.bothNan;
                }
            }
            ++this.chunks;
            this.elements += size;
            if (this.mismatches > before) {
                ++this.chunksWithMismatch;
            }
        }

        private void classifyDifference(String where, DensityVolume volume, int index, int expectedBits, int actualBits) {
            if (isNan(expectedBits) && isNan(actualBits)) {
                ++this.bothNan;
                ++this.nanPayloadDiffers;
                return;
            }
            if (this.mismatches++ == 0) {
                int y = index % volume.sizeY();
                int column = index / volume.sizeY();
                this.first = new Mismatch(where, volume.blockX(column % volume.sizeX()), volume.blockY(y),
                    volume.blockZ(column / volume.sizeX()), expectedBits, actualBits);
            }
        }

        boolean passed() {
            return this.mismatches == 0;
        }

        String candidate() {
            return this.candidate;
        }

        int chunks() {
            return this.chunks;
        }

        int chunksWithMismatch() {
            return this.chunksWithMismatch;
        }

        long elements() {
            return this.elements;
        }

        long mismatches() {
            return this.mismatches;
        }

        long bothNan() {
            return this.bothNan;
        }

        long nanPayloadDiffers() {
            return this.nanPayloadDiffers;
        }

        Mismatch first() {
            return this.first;
        }

        String summary() {
            String text = String.format("%s vs %s: %d chunks, %d floats, %d mismatches in %d chunks, both NaN %d (payload differs %d) -> %s",
                this.candidate, this.reference, this.chunks, this.elements, this.mismatches, this.chunksWithMismatch, this.bothNan,
                this.nanPayloadDiffers, this.passed() ? "bit-identical" : "DIFFERENT");
            return this.first == null ? text : text + "\n    first: " + this.first;
        }
    }
}
