package densitycompiler.eval;

/** BinaryFunction's samplers with one constant side per column, with ConstBinaryNode's element loops. */
final class ConstBinaryTile extends Tile {
    private final ConstBinaryNode.Kind kind;
    private final Tile input;
    private final float constant;

    ConstBinaryTile(ConstBinaryNode.Kind kind, Tile input, float constant) {
        this.kind = kind;
        this.input = input;
        this.constant = constant;
    }

    @Override
    void fill(TileFrame frame, float[] out, int x, int z) {
        this.input.fill(frame, out, x, z);
        ConstBinaryNode.apply(this.kind, out, frame.volume().sizeY(), this.constant);
    }
}
