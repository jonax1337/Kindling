package densitycompiler.eval;

/**
 * One node of the block stage, built from one of Paper's block-level samplers. It writes Paper's value of every y
 * of one column (x, z) of the frame's volume into out[0, sizeY). Every tile writes all sizeY values.
 */
abstract class Tile {
    abstract void fill(TileFrame frame, float[] out, int x, int z);
}
