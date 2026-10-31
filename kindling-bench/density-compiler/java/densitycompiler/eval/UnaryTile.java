package densitycompiler.eval;

/** UnaryFunction's samplers and ClampFunction.Sampler per column, with UnaryNode's element loops. */
final class UnaryTile extends Tile {
    private final UnaryNode.Kind kind;
    private final Tile input;
    private final float first;
    private final float second;

    /** first and second as for UnaryNode. */
    UnaryTile(UnaryNode.Kind kind, Tile input, float first, float second) {
        this.kind = kind;
        this.input = input;
        this.first = first;
        this.second = second;
    }

    @Override
    void fill(TileFrame frame, float[] out, int x, int z) {
        this.input.fill(frame, out, x, z);
        UnaryNode.apply(this.kind, out, frame.volume().sizeY(), this.first, this.second);
    }
}
