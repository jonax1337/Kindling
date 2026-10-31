package densitycompiler.eval;

/** RangeChoiceFunction.ConstSampler per column, with ConstRangeChoiceNode's choose(). */
final class ConstRangeChoiceTile extends Tile {
    private final Tile input;
    private final float minInclusive;
    private final float maxExclusive;
    private final float whenInRange;
    private final float whenOutOfRange;

    ConstRangeChoiceTile(Tile input, float minInclusive, float maxExclusive, float whenInRange, float whenOutOfRange) {
        this.input = input;
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
        this.whenInRange = whenInRange;
        this.whenOutOfRange = whenOutOfRange;
    }

    @Override
    void fill(TileFrame frame, float[] out, int x, int z) {
        this.input.fill(frame, out, x, z);
        ConstRangeChoiceNode.choose(out, frame.volume().sizeY(), this.minInclusive, this.maxExclusive, this.whenInRange, this.whenOutOfRange);
    }
}
