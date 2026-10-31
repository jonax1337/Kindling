package densitycompiler.eval;

/**
 * What one branch of a selector or one Paper leaf did: points Paper would evaluate (region) against points
 * requested by demand and points actually computed. Per node and thread, so plain fields.
 */
public final class PruneCounter {
    enum Plan { SKIP, FULL, BOX, RUNS }

    private final String label;
    private final boolean leaf;
    private long calls;
    private long regionPoints;
    private long requestedPoints;
    private long evaluatedPoints;
    private final long[] plans = new long[Plan.values().length];
    private long subCalls;

    private PruneCounter(String label, boolean leaf) {
        this.label = label;
        this.leaf = leaf;
    }

    static PruneCounter branch(String label) {
        return new PruneCounter(label, false);
    }

    static PruneCounter leaf(String label) {
        return new PruneCounter(label, true);
    }

    void recordBranch(int regionSize, int requested) {
        this.recordLeaf(regionSize, requested, requested == 0 ? Plan.SKIP : Plan.FULL, requested, requested == 0 ? 0 : 1);
    }

    /** One column of a block-level branch: the whole column is evaluated unless pruning skipped it. */
    void recordColumn(int columnSize, int requested, boolean pruning) {
        boolean skipped = pruning && requested == 0;
        this.recordLeaf(columnSize, requested, skipped ? Plan.SKIP : Plan.FULL, skipped ? 0 : columnSize, skipped ? 0 : 1);
    }

    void recordLeaf(int regionSize, int requested, Plan plan, int evaluated, int calls) {
        ++this.calls;
        this.regionPoints += regionSize;
        this.requestedPoints += requested;
        this.evaluatedPoints += evaluated;
        ++this.plans[plan.ordinal()];
        this.subCalls += calls;
    }

    public String label() {
        return this.label;
    }

    public long regionPoints() {
        return this.regionPoints;
    }

    public long evaluatedPoints() {
        return this.evaluatedPoints;
    }

    public boolean isLeaf() {
        return this.leaf;
    }

    public void addTo(PruneCounter total) {
        total.calls += this.calls;
        total.regionPoints += this.regionPoints;
        total.requestedPoints += this.requestedPoints;
        total.evaluatedPoints += this.evaluatedPoints;
        total.subCalls += this.subCalls;
        for (int i = 0; i < this.plans.length; ++i) {
            total.plans[i] += this.plans[i];
        }
    }

    public PruneCounter emptyCopy() {
        return new PruneCounter(this.label, this.leaf);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "%-58s calls %8d  region %11d  requested %5.1f%%  evaluated %5.1f%%  plans skip/full/box/runs %d/%d/%d/%d  sub-calls %d",
            this.label, this.calls, this.regionPoints, percent(this.requestedPoints), percent(this.evaluatedPoints),
            this.plans[0], this.plans[1], this.plans[2], this.plans[3], this.subCalls);
    }

    private double percent(long points) {
        return this.regionPoints == 0 ? 0.0 : 100.0 * points / this.regionPoints;
    }
}
