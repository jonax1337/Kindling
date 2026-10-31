package noisekernel;

/** FFM downcall variants: scalar kernel, SIMD kernel, and the empty call that measures FFM overhead. */
final class NativeVariant implements Variant {
    enum Mode {
        SCALAR("NATIVE_SCALAR"),
        SIMD("NATIVE_SIMD"),
        NOOP("NATIVE_NOOP");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private final Mode mode;

    NativeVariant(Mode mode) {
        this.mode = mode;
    }

    @Override
    public String name() {
        return this.mode.label;
    }

    @Override
    public void run(KernelCall call, Work work) {
        int status = status(call, work, work.values.length);
        if (status != 0) {
            throw new IllegalStateException(this.name() + " returned " + status);
        }
    }

    /** The raw native status for the call, with an explicit values_len. */
    int status(KernelCall call, Work work, int valuesLen) {
        boolean smeared = call.kind() == Kind.SMEARED;
        return switch (this.mode) {
            case SCALAR -> smeared ? NativeLib.smearedScalar(call, work, valuesLen) : NativeLib.perlinScalar(call, work, valuesLen);
            case SIMD -> smeared ? NativeLib.smearedSimd(call, work, valuesLen) : NativeLib.perlinSimd(call, work, valuesLen);
            case NOOP -> NativeLib.noop(call, work, valuesLen);
        };
    }

    @Override
    public void runAll(KernelCall[] calls, Work work) {
        for (KernelCall call : calls) {
            this.run(call, work);
        }
    }
}
