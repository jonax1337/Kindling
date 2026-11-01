package net.kindlingmc.preview;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The sky light a preview is sent with: light falling straight down, 15 through air, one less per fluid block, 0 from
 * the first other block on; no sideways spread and no block light. Sections above the highest non-empty one get no
 * array, as the light engine stores none there. An estimate, not the light a client would compute.
 */
final class SkyLight {
    private static final int MAX_LIGHT = 15;

    /** The light layers of one kind: which light sections carry an array, which are all zero, and the arrays. */
    record Layers(BitSet present, BitSet empty, List<byte[]> arrays) {
        /** Light sections are the chunk sections plus one below and one above; a null layer sends nothing. */
        static Layers of(byte[][] layers) {
            BitSet present = new BitSet();
            BitSet empty = new BitSet();
            List<byte[]> arrays = new ArrayList<>();
            for (int index = 0; index < layers.length; index++) {
                if (layers[index] == null) {
                    continue;
                }
                if (isAllZero(layers[index])) {
                    empty.set(index);
                } else {
                    present.set(index);
                    arrays.add(layers[index]);
                }
            }
            return new Layers(present, empty, arrays);
        }

        private static boolean isAllZero(byte[] layer) {
            for (byte value : layer) {
                if (value != 0) {
                    return false;
                }
            }
            return true;
        }
    }

    record Light(Layers sky, Layers block) {
    }

    private SkyLight() {
    }

    static Light fromAbove(LevelChunkSection[] sections) {
        int highest = highestNonEmpty(sections);
        DataLayer[] layers = new DataLayer[sections.length];
        for (int index = 0; index <= Math.min(highest + 1, sections.length - 1); index++) {
            layers[index] = new DataLayer();
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                lightColumn(sections, layers, x, z);
            }
        }
        byte[][] sky = new byte[sections.length + 2][];
        for (int index = 0; index < sections.length; index++) {
            sky[index + 1] = layers[index] == null ? null : layers[index].getData();
        }
        return new Light(Layers.of(sky), Layers.of(new byte[sections.length + 2][]));
    }

    private static void lightColumn(LevelChunkSection[] sections, DataLayer[] layers, int x, int z) {
        int light = MAX_LIGHT;
        for (int index = sections.length - 1; index >= 0; index--) {
            for (int y = 15; y >= 0; y--) {
                light = lightBelow(light, sections[index].getBlockState(x, y, z));
                if (layers[index] != null) {
                    layers[index].set(x, y, z, light);
                }
            }
        }
    }

    private static int lightBelow(int light, BlockState state) {
        if (state.isAir()) {
            return light;
        }
        if (!state.getFluidState().isEmpty() && !PreviewGenerator.isTerrain(state)) {
            return Math.max(0, light - 1);
        }
        return 0;
    }

    private static int highestNonEmpty(LevelChunkSection[] sections) {
        for (int index = sections.length - 1; index >= 0; index--) {
            if (!sections[index].hasOnlyAir()) {
                return index;
            }
        }
        return -1;
    }
}
