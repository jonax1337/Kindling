package noisekernel;

/**
 * One implementation of the batched volume kernel.
 * runAll holds its own loop in every implementation so each variant's call site stays monomorphic.
 */
interface Variant {
    String name();

    void run(KernelCall call, Work work);

    void runAll(KernelCall[] calls, Work work);
}
