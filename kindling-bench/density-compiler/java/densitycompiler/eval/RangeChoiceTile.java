package densitycompiler.eval;

/**
 * RangeChoiceFunction.Sampler per column. Paper writes whenInRange everywhere, then whenOutOfRange where
 * !(input >= min) || !(input < max). With pruning, a branch none of whose values survive in this column is skipped:
 * no whenInRange if every input is out of range (all of it is overwritten), no whenOutOfRange if none is.
 */
final class RangeChoiceTile extends Tile {
    private final Tile input;
    private final float minInclusive;
    private final float maxExclusive;
    private final Tile whenInRange;
    private final Tile whenOutOfRange;
    private final PruneCounter inRangeCounter;
    private final PruneCounter outOfRangeCounter;

    /** Branches is (whenInRange, whenOutOfRange), counters the pruning counter of each branch in the same order. */
    RangeChoiceTile(Tile input, float minInclusive, float maxExclusive, Tile[] branches, PruneCounter[] counters) {
        this.input = input;
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
        this.whenInRange = branches[0];
        this.whenOutOfRange = branches[1];
        this.inRangeCounter = counters[0];
        this.outOfRangeCounter = counters[1];
    }

    @Override
    void fill(TileFrame frame, float[] out, int x, int z) {
        int sizeY = frame.volume().sizeY();
        float[] inputValues = frame.pushTile();
        this.input.fill(frame, inputValues, x, z);
        int inRange = this.countInRange(inputValues, sizeY);
        int outOfRange = sizeY - inRange;
        boolean pruning = frame.pruning();
        this.inRangeCounter.recordColumn(sizeY, inRange, pruning);
        this.outOfRangeCounter.recordColumn(sizeY, outOfRange, pruning);
        if (inRange > 0 || !pruning) {
            this.whenInRange.fill(frame, out, x, z);
        }
        if (outOfRange > 0 || !pruning) {
            this.mergeOutOfRange(frame, out, inputValues, x, z);
        }
        frame.popTile();
    }

    private int countInRange(float[] inputValues, int sizeY) {
        int count = 0;
        for (int i = 0; i < sizeY; ++i) {
            float value = inputValues[i];
            count += value >= this.minInclusive && value < this.maxExclusive ? 1 : 0;
        }
        return count;
    }

    private void mergeOutOfRange(TileFrame frame, float[] out, float[] inputValues, int x, int z) {
        int sizeY = frame.volume().sizeY();
        float[] outOfRangeValues = frame.pushTile();
        this.whenOutOfRange.fill(frame, outOfRangeValues, x, z);
        for (int i = 0; i < sizeY; ++i) {
            float value = inputValues[i];
            if (!(value >= this.minInclusive) || !(value < this.maxExclusive)) {
                out[i] = outOfRangeValues[i];
            }
        }
        frame.popTile();
    }
}
