package noisekernel;

/** The Java Vector API port of the kernel. */
final class VectorVariant implements Variant {
    @Override
    public String name() {
        return "VECTOR";
    }

    @Override
    public void run(KernelCall call, Work work) {
        work.vector.addToVolume(call, work.values);
    }

    @Override
    public void runAll(KernelCall[] calls, Work work) {
        for (KernelCall call : calls) {
            this.run(call, work);
        }
    }
}
