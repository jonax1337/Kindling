package densitycompiler.eval;

import java.util.Arrays;

/** ConstantFunction.Sampler: fill(value). */
final class ConstTile extends Tile {
    private final float value;

    ConstTile(float value) {
        this.value = value;
    }

    @Override
    void fill(TileFrame frame, float[] out, int x, int z) {
        Arrays.fill(out, 0, frame.volume().sizeY(), this.value);
    }
}
