package noisekernel;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Entry point: --verify checks bit-exactness of every variant, --bench times them. */
public final class Main {
    private static final int EXIT_FAILED = 1;
    private static final int EXIT_USAGE = 2;
    private static final int AVX2_LEVEL = 2;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(EXIT_USAGE);
            return;
        }
        Integer simdLevel = options.noNative ? null : probeNative();
        System.out.printf("java %s, vector species %s, kn_simd_level %s%n",
            System.getProperty("java.version"), VectorKernel.speciesName(), simdLevel == null ? "n/a (native skipped)" : simdLevel);

        // BlendedNoise implements DensityFunction, whose class initialisation needs the built-in registries.
        bootstrapServerClasses();

        Workload workload = Workload.build(options.seed, options.chunks);
        System.out.printf("workload: %d chunks, %d groups, %d leaf calls, %d distinct leaves%n",
            workload.chunkCount, workload.groups.size(), workload.calls.length, workload.leaves.size());

        boolean ok = options.verify ? verify(workload, options) : bench(workload, options, simdLevel);
        System.exit(ok ? 0 : EXIT_FAILED);
    }

    private static boolean verify(Workload workload, Options options) {
        List<Variant> candidates = new ArrayList<>(List.of(new VanillaVariant(), new VectorVariant()));
        NativeVariant scalar = new NativeVariant(NativeVariant.Mode.SCALAR);
        NativeVariant simd = new NativeVariant(NativeVariant.Mode.SIMD);
        if (!options.noNative) {
            candidates.add(scalar);
            candidates.add(simd);
        }
        KernelCall[] fuzzCalls = Fuzz.calls(options.fuzz, options.seed, workload.leaves);
        Verifier verifier = new Verifier(candidates, options.seed);
        boolean ok = verifier.verify(workload, fuzzCalls);
        if (!options.noNative) {
            ok &= verifier.checkNativeStatus(simd, scalar, workload.calls[0]);
        }
        System.out.println(ok ? "VERIFY PASSED" : "VERIFY FAILED");
        return ok;
    }

    private static boolean bench(Workload workload, Options options, Integer simdLevel) throws Exception {
        if (options.rounds < 30) {
            System.out.println("note: fewer than 30 measured rounds; numbers are a smoke test only");
        }
        List<Variant> variants = new ArrayList<>(List.of(new PaperVariant(), new VectorVariant()));
        if (!options.noNative) {
            variants.add(new NativeVariant(NativeVariant.Mode.SCALAR));
            variants.add(new NativeVariant(NativeVariant.Mode.SIMD));
            variants.add(new NativeVariant(NativeVariant.Mode.NOOP));
        }
        if (simdLevel != null && simdLevel != AVX2_LEVEL) {
            System.err.println("kn_simd_level is " + simdLevel + ", so NATIVE_SIMD would silently run scalar code; use --no-native");
            return false;
        }
        Measurements measurements = new Bench(workload, variants, options).run();
        Report report = new Report(measurements, options, workload, simdLevel);
        report.print();
        if (options.out != null) {
            report.writeJson(options.out);
        }
        return verifyWarm(workload, variants, options.seed);
    }

    /** Re-checks the JIT-compiled variants after the timed rounds, so a fast but wrong variant cannot pass as a win. */
    private static boolean verifyWarm(Workload workload, List<Variant> benched, long seed) {
        List<Variant> candidates = new ArrayList<>();
        for (Variant variant : benched) {
            if (!(variant instanceof PaperVariant) && !variant.name().equals(NativeVariant.Mode.NOOP.label)) {
                candidates.add(variant);
            }
        }
        boolean ok = new Verifier(candidates, seed).verify(workload, new KernelCall[0]);
        System.out.println(ok ? "WARM VERIFY PASSED" : "WARM VERIFY FAILED");
        return ok;
    }

    /** Bootstrap wraps System.out/err into the logger, which can lose the last lines at exit: restore them. */
    private static void bootstrapServerClasses() {
        PrintStream out = System.out;
        PrintStream err = System.err;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        System.setOut(out);
        System.setErr(err);
    }

    /** Loads the native library and returns kn_simd_level; exits with a hint when it cannot be loaded. */
    private static Integer probeNative() {
        try {
            return NativeLib.simdLevel();
        } catch (Throwable t) {
            System.err.println("Cannot load the native library from KN_LIB (use --no-native to skip native variants): " + t);
            System.exit(EXIT_USAGE);
            return null;
        }
    }
}
