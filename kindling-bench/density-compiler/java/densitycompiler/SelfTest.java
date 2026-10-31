package densitycompiler;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** Proves the verifier can fail: a variant that flips one bit must be reported, with its exact position. */
final class SelfTest {
    private static final int EXPECTED_BLOCK_Y = -64 + 57;
    private static final int EXPECTED_BLOCK_Z_OFFSET = 2;
    private static final long BENCH_HANG_LIMIT_MILLIS = 120_000L;

    private boolean ok = true;

    static boolean run(World world, Options options) {
        SelfTest test = new SelfTest();
        test.rawBitComparison();
        test.flippedBitIsReported(world, options);
        test.incompleteWritesAreReported(world, options);
        test.repeatedVolumesAreChecked(world, options);
        test.failingBenchThreadEndsTheBench(world);
        test.reportsNothingForIdenticalVariant(world, options);
        return test.ok;
    }

    private void rawBitComparison() {
        DensityVolume volume = new DensityVolume(1, 4, 1, 0, 0, 0);
        float quietNan = Float.intBitsToFloat(0x7fc00000);
        float otherNan = Float.intBitsToFloat(0x7fc00001);
        float oneUlpAbove = Math.nextUp(1.0F);
        expect("+0.0 vs -0.0 is a mismatch", mismatches(volume, new float[] {0.0F, 0, 0, 0}, new float[] {-0.0F, 0, 0, 0}) == 1);
        expect("1.0 vs next float is a mismatch", mismatches(volume, new float[] {1.0F, 0, 0, 0}, new float[] {oneUlpAbove, 0, 0, 0}) == 1);
        expect("NaN vs 1.0 is a mismatch", mismatches(volume, new float[] {quietNan, 0, 0, 0}, new float[] {1.0F, 0, 0, 0}) == 1);
        expect("1.0 vs NaN is a mismatch", mismatches(volume, new float[] {1.0F, 0, 0, 0}, new float[] {quietNan, 0, 0, 0}) == 1);
        expect("NaN vs NaN with another payload is allowed and counted", nanPayloadOnly(volume, quietNan, otherNan));
        expect("equal arrays pass", mismatches(volume, new float[] {0.5F, -0.0F, 2, 3}, new float[] {0.5F, -0.0F, 2, 3}) == 0);
    }

    private void flippedBitIsReported(World world, Options options) {
        List<Chunk> chunks = Workload.build(List.of(world), selfTestOptions(options)).chunks();
        List<Verifier.Tally> tallies = Verifier.fresh(List.of(world), Variants.reference(), List.of(new BitFlipVariant())).verify(chunks);
        Verifier.Tally tally = tallies.getFirst();
        System.out.println("  " + tally.summary());
        Chunk first = chunks.getFirst();
        Verifier.Mismatch where = tally.first();
        expect("bit flip fails the verifier", !tally.passed());
        expect("exactly one float per chunk differs", tally.mismatches() == chunks.size() && tally.chunksWithMismatch() == chunks.size());
        expect("the first mismatch is at the flipped position", where != null && where.blockX() == first.volume().minBlockX()
            && where.blockY() == EXPECTED_BLOCK_Y && where.blockZ() == first.volume().minBlockZ() + EXPECTED_BLOCK_Z_OFFSET);
    }

    private void incompleteWritesAreReported(World world, Options options) {
        List<Chunk> chunks = Workload.build(List.of(world), selfTestOptions(options)).chunks();
        long elements = chunks.stream().mapToLong(chunk -> chunk.volume().size()).sum();
        for (BrokenVariant.Kind kind : BrokenVariant.Kind.values()) {
            if (kind == BrokenVariant.Kind.SKIPS_REPEATED_VOLUME) {
                continue;
            }
            Verifier.Tally tally = Verifier.fresh(List.of(world), Variants.reference(), List.of(new BrokenVariant(kind))).verify(chunks).getFirst();
            System.out.println("  " + tally.summary());
            long expected = kind == BrokenVariant.Kind.HALF_COLUMN ? unwrittenUpperHalf(chunks) : elements;
            expect(kind + " fails the verifier on every element it did not write", tally.mismatches() == expected);
        }
    }

    /** An instance that trusts what it left behind for a volume it has just seen passes the first pass and must fail the repeat pass. */
    private void repeatedVolumesAreChecked(World world, Options options) {
        List<Chunk> chunks = Workload.build(List.of(world), selfTestOptions(options)).chunks();
        List<Verifier.Tally> tallies = Verifier.fresh(List.of(world), Variants.reference(), List.of(new BrokenVariant(BrokenVariant.Kind.SKIPS_REPEATED_VOLUME))).verify(chunks);
        tallies.forEach(tally -> System.out.println("  " + tally.summary()));
        expect("a variant that skips a repeated volume passes the first pass", tallies.stream()
            .filter(tally -> tally.candidate().contains("[CHUNK]")).allMatch(Verifier.Tally::passed));
        expect("a variant that skips a repeated volume fails the repeat pass", tallies.stream()
            .anyMatch(tally -> tally.candidate().endsWith("[repeat]") && !tally.passed()));
    }

    /** One of two bench threads throws while the other may be anywhere between two barriers: the bench must end with the error. */
    private void failingBenchThreadEndsTheBench(World world) {
        Options options = Options.parse(new String[] {"--bench", "--threads", "2", "--warmup-sec", "0", "--rounds", "2", "--chunks", "1",
            "--square", "0", "--far", "0"});
        List<Chunk> chunks = Workload.build(List.of(world), options).chunks();
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                new Bench(List.of(world), List.of(PaperVariant.cached(), new FailingVariant("bench-1")), chunks, chunks, options).run();
            } catch (Throwable t) {
                outcome.set(t);
            }
        }, "self-test-bench");
        runner.setDaemon(true);
        runner.start();
        try {
            runner.join(BENCH_HANG_LIMIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        expect("a failing bench thread ends the bench instead of hanging it", !runner.isAlive());
        expect("the bench reports the failing thread's error", outcome.get() != null && outcome.get().getCause() != null
            && outcome.get().getCause().getMessage().contains("self-test failure"));
    }

    private static long unwrittenUpperHalf(List<Chunk> chunks) {
        long unwritten = 0;
        for (Chunk chunk : chunks) {
            DensityVolume volume = chunk.volume();
            unwritten += (long)volume.sizeX() * volume.sizeZ() * (volume.sizeY() - volume.sizeY() / 2);
        }
        return unwritten;
    }

    private void reportsNothingForIdenticalVariant(World world, Options options) {
        List<Chunk> chunks = Workload.build(List.of(world), selfTestOptions(options)).chunks();
        List<Verifier.Tally> tallies = Verifier.fresh(List.of(world), Variants.reference(), Variants.candidates()).verify(chunks);
        tallies.forEach(tally -> System.out.println("  " + tally.summary()));
        expect("the registered candidates pass on the same chunks", Verifier.allPassed(tallies));
        expect("the probe compared values", tallies.stream().anyMatch(tally -> tally.candidate().endsWith("[probe]") && tally.elements() > 0));
    }

    private static Options selfTestOptions(Options base) {
        Options options = Options.parse(new String[] {"--self-test", "--chunks", "2", "--square", "0", "--far", "1", "--beard-every", "2"});
        options.workloadSeed = base.workloadSeed;
        return options;
    }

    private static long mismatches(DensityVolume volume, float[] expected, float[] actual) {
        Verifier.Tally tally = new Verifier.Tally("test", "test");
        tally.add("test", volume, expected, actual);
        return tally.mismatches();
    }

    private static boolean nanPayloadOnly(DensityVolume volume, float expectedNan, float actualNan) {
        Verifier.Tally tally = new Verifier.Tally("test", "test");
        tally.add("test", volume, new float[] {expectedNan, 0, 0, 0}, new float[] {actualNan, 0, 0, 0});
        return tally.passed() && tally.bothNan() == 1 && tally.nanPayloadDiffers() == 1;
    }

    private void expect(String what, boolean condition) {
        System.out.println((condition ? "  ok      " : "  FAILED  ") + what);
        this.ok &= condition;
    }
}
