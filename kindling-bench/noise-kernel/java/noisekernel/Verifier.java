package noisekernel;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Bit-exactness check. Every call starts from the same non-zero random buffer; PAPER is the reference
 * and every other variant (including the private vanilla path) must match it bit for bit over the whole buffer.
 */
final class Verifier {
    private final Variant reference = new PaperVariant();
    private final List<Variant> candidates;
    private final List<Tally> tallies = new ArrayList<>();
    private final long seed;
    private Work work;
    private float[] base;
    private float[] expected;

    Verifier(List<Variant> candidates, long seed) {
        this.candidates = candidates;
        this.seed = seed;
        for (Variant candidate : candidates) {
            this.tallies.add(new Tally(candidate.name()));
        }
    }

    /** Returns true when every check passed. */
    boolean verify(Workload workload, KernelCall[] fuzzCalls) {
        int capacity = Math.max(maxSamples(workload.calls), maxSamples(fuzzCalls));
        this.work = new Work(Math.max(capacity, 1100));
        this.base = new float[this.work.values.length];
        this.expected = new float[this.work.values.length];
        SplittableRandom random = new SplittableRandom(this.seed);

        boolean ok = this.checkGroupsAgainstRealStacks(workload);
        this.checkAll("workload", workload.calls, random);
        this.checkAll("fuzz", fuzzCalls, random);
        for (Tally tally : this.tallies) {
            tally.print();
            ok &= tally.badCalls == 0;
        }
        return ok;
    }

    /** Native status codes: a size above the cap must return -1, a too short buffer -2, both without writing. */
    boolean checkNativeStatus(NativeVariant simd, NativeVariant scalar, KernelCall sample) {
        boolean ok = true;
        for (NativeVariant variant : List.of(scalar, simd)) {
            ok &= this.expectStatus(variant, sample, sample.samples() - 1, -2, "values_len below size_x*size_y*size_z");
            ok &= this.expectStatus(variant, oversizedCall(sample), this.work.values.length, -1, "size above the per-axis cap of 1024");
        }
        return ok;
    }

    private boolean expectStatus(NativeVariant variant, KernelCall call, int valuesLen, int wanted, String what) {
        this.fillBase(new SplittableRandom(this.seed));
        System.arraycopy(this.base, 0, this.work.values, 0, this.base.length);
        int status = variant.status(call, this.work, valuesLen);
        boolean untouched = firstDifference(this.base, this.work.values) < 0;
        boolean ok = status == wanted && untouched;
        System.out.printf("%s: %s -> status %d (wanted %d), buffer %s%s%n", variant.name(), what, status, wanted,
            untouched ? "untouched" : "WRITTEN", ok ? "" : "   [FAIL]");
        return ok;
    }

    private static KernelCall oversizedCall(KernelCall sample) {
        var volume = new net.minecraft.world.level.levelgen.densityfunction.DensityVolume(1, 1025, 1, 0, 0, 0, 1, 1, 1);
        return new KernelCall(sample.leaf(), volume, sample.xzScale(), sample.yScale(), sample.amplitude());
    }

    /** The recorded leaf calls, replayed in order, must equal the real NoiseStack.addToVolume of each group. */
    private boolean checkGroupsAgainstRealStacks(Workload workload) {
        float[] viaStack = new float[this.work.values.length];
        int bad = 0;
        for (Workload.Group group : workload.groups) {
            this.work.clear();
            group.root().addToVolume(this.work.buffer, group.volume(), group.xzScale(), group.yScale(), 1.0F);
            System.arraycopy(this.work.values, 0, viaStack, 0, viaStack.length);
            this.work.clear();
            for (int i = group.firstCall(); i < group.endCall(); ++i) {
                this.reference.run(workload.calls[i], this.work);
            }
            if (firstDifference(viaStack, this.work.values) >= 0) {
                ++bad;
            }
        }
        System.out.printf("workload self-check: %d groups, %d differ from the real NoiseStack.addToVolume%s%n",
            workload.groups.size(), bad, bad == 0 ? "" : "   [FAIL]");
        return bad == 0;
    }

    private void checkAll(String origin, KernelCall[] calls, SplittableRandom random) {
        for (int index = 0; index < calls.length; ++index) {
            this.fillBase(random);
            this.runFrom(this.reference, calls[index], this.expected);
            for (int v = 0; v < this.candidates.size(); ++v) {
                this.checkCandidate(this.tallies.get(v), this.candidates.get(v), origin, index, calls[index]);
            }
        }
    }

    private void checkCandidate(Tally tally, Variant candidate, String origin, int index, KernelCall call) {
        this.runFrom(candidate, call, this.work.values);
        tally.calls++;
        if (call.kind() == Kind.SMEARED) {
            tally.smearedCalls++;
        } else {
            tally.perlinCalls++;
        }
        tally.floatsCompared += this.work.values.length;
        int first = firstDifference(this.expected, this.work.values);
        if (first < 0) {
            return;
        }
        tally.badCalls++;
        tally.badElements += countDifferences(this.expected, this.work.values);
        if (tally.firstMismatch == null) {
            tally.firstMismatch = String.format("%s call %d (%s) element %d: PAPER %s (%08x) != %s %s (%08x)",
                origin, index, call.kind(), first,
                Float.toString(this.expected[first]), Float.floatToRawIntBits(this.expected[first]),
                candidate.name(), Float.toString(this.work.values[first]), Float.floatToRawIntBits(this.work.values[first]));
        }
    }

    private void runFrom(Variant variant, KernelCall call, float[] out) {
        System.arraycopy(this.base, 0, this.work.values, 0, this.base.length);
        variant.run(call, this.work);
        if (out != this.work.values) {
            System.arraycopy(this.work.values, 0, out, 0, out.length);
        }
    }

    private void fillBase(SplittableRandom random) {
        for (int i = 0; i < this.base.length; ++i) {
            this.base[i] = i % 61 == 0 ? -0.0F : (float)((random.nextDouble() - 0.5) * 8.0);
        }
    }

    private static int maxSamples(KernelCall[] calls) {
        int max = 0;
        for (KernelCall call : calls) {
            max = Math.max(max, call.samples());
        }
        return max;
    }

    private static int firstDifference(float[] a, float[] b) {
        for (int i = 0; i < a.length; ++i) {
            if (Float.floatToRawIntBits(a[i]) != Float.floatToRawIntBits(b[i])) {
                return i;
            }
        }
        return -1;
    }

    private static long countDifferences(float[] a, float[] b) {
        long count = 0;
        for (int i = 0; i < a.length; ++i) {
            if (Float.floatToRawIntBits(a[i]) != Float.floatToRawIntBits(b[i])) {
                ++count;
            }
        }
        return count;
    }

    private static final class Tally {
        final String name;
        long calls;
        long perlinCalls;
        long smearedCalls;
        long floatsCompared;
        long badCalls;
        long badElements;
        String firstMismatch;

        Tally(String name) {
            this.name = name;
        }

        void print() {
            System.out.printf("%-14s %d calls checked, %d calls with mismatches (%d elements)%s%n",
                this.name, this.calls, this.badCalls, this.badElements, this.badCalls == 0 ? "   [OK]" : "   [FAIL]");
            System.out.printf("%-14s   of those %d PERLIN, %d SMEARED; %d floats compared bit for bit (whole buffer per call)%n",
                "", this.perlinCalls, this.smearedCalls, this.floatsCompared);
            if (this.firstMismatch != null) {
                System.out.println("  first mismatch: " + this.firstMismatch);
            }
        }
    }
}
