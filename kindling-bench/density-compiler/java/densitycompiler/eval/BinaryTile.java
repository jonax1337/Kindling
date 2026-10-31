package densitycompiler.eval;

/** BinaryFunction's two-input samplers per column: left into out, right into a tile, BinaryNode's element loops. */
final class BinaryTile extends Tile {
    private final BinaryNode.Kind kind;
    private final Tile left;
    private final Tile right;

    BinaryTile(BinaryNode.Kind kind, Tile left, Tile right) {
        this.kind = kind;
        this.left = left;
        this.right = right;
    }

    @Override
    void fill(TileFrame frame, float[] out, int x, int z) {
        this.left.fill(frame, out, x, z);
        float[] rightValues = frame.pushTile();
        this.right.fill(frame, rightValues, x, z);
        BinaryNode.combine(this.kind, out, rightValues, frame.volume().sizeY());
        frame.popTile();
    }
}
