package noisekernel;

/** The real addToVolume of the server jar (Paper's optimised version). */
final class PaperVariant implements Variant {
    @Override
    public String name() {
        return "PAPER";
    }

    @Override
    public void run(KernelCall call, Work work) {
        call.leaf().noise.addToVolume(work.buffer, call.volume(), call.xzScale(), call.yScale(), call.amplitude());
    }

    @Override
    public void runAll(KernelCall[] calls, Work work) {
        for (KernelCall call : calls) {
            this.run(call, work);
        }
    }
}
