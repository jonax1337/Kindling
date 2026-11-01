package net.kindlingmc.preview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The sky light a preview is sent with, computed by the rules of the light engine (Moonrise's SkyStarLightEngine):
 * the sky is 15 above the chunk and falls straight down through every block that dampens no light; light then
 * spreads in all six directions and loses {@code max(1, dampening)} per step into a block. So ground under leaves and
 * overhangs is lit from the open columns next to them, and water darkens one level per block.
 *
 * <p>Left out, as a preview has no neighbour chunks and the cost must stay small: light from or into neighbouring
 * chunks (the chunk is lit as if the world ended at its edges, so columns at an edge are a little darker than
 * in the full chunk, where light flows in from outside), and the directional occlusion of slabs and stairs. Block
 * light is empty. Sections above the highest non-empty one get no array but the first one, as the light engine
 * stores none.
 *
 * <p>Thread safety: each thread works in its own reusable buffers; nothing is shared.
 */
final class SkyLight {
    private static final int MAX_LIGHT = 15;
    private static final int OPAQUE_DAMPENING = 15;
    private static final int SECTION_CELLS = 4096;
    private static final int LIGHT_ARRAY_BYTES = SECTION_CELLS / 2;
    private static final int CELLS_PER_LAYER = 256;
    private static final int X_MASK = 15;
    private static final int Z_SHIFT = 4;
    private static final int Y_SHIFT = 8;
    private static final int NO_LIGHT_RUN = Integer.MAX_VALUE;
    private static final Predicate<BlockState> LETS_LIGHT_THROUGH = state -> state.getLightDampening() < OPAQUE_DAMPENING;
    private static final ThreadLocal<Workspace> WORKSPACE = ThreadLocal.withInitial(Workspace::new);

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
        int litSections = Math.min(highestNonEmpty(sections) + 2, sections.length);
        Workspace workspace = WORKSPACE.get();
        byte[] levels = workspace.lightLevels(litSections * SECTION_CELLS);
        new Spreader(sections, litSections, levels, workspace).spread();
        byte[][] sky = new byte[sections.length + 2][];
        for (int index = 0; index < litSections; index++) {
            sky[index + 1] = packSection(levels, index * SECTION_CELLS);
        }
        return new Light(Layers.of(sky), Layers.of(new byte[sections.length + 2][]));
    }

    /** Two cells per byte, the lower cell index in the low nibble, as DataLayer stores them. */
    private static byte[] packSection(byte[] levels, int firstCell) {
        byte[] packed = new byte[LIGHT_ARRAY_BYTES];
        for (int i = 0; i < LIGHT_ARRAY_BYTES; i++) {
            packed[i] = (byte) (levels[firstCell + 2 * i] | levels[firstCell + 2 * i + 1] << 4);
        }
        return packed;
    }

    private static int highestNonEmpty(LevelChunkSection[] sections) {
        for (int index = sections.length - 1; index >= 0; index--) {
            if (!sections[index].hasOnlyAir()) {
                return index;
            }
        }
        return -1;
    }

    /** The buffers one thread reuses: the light level of every cell and the queue of cells whose light spreads on. */
    private static final class Workspace {
        private byte[] levels = new byte[0];
        private int[] queue = new int[1024];

        /** The first cells of the level array, all 0. */
        byte[] lightLevels(int cells) {
            if (this.levels.length < cells) {
                this.levels = new byte[cells];
            } else {
                Arrays.fill(this.levels, 0, cells, (byte) 0);
            }
            return this.levels;
        }

        int[] queue() {
            return this.queue;
        }

        void append(int length, int cell) {
            if (this.queue.length <= length) {
                this.queue = Arrays.copyOf(this.queue, this.queue.length * 2);
            }
            this.queue[length] = cell;
        }
    }

    /**
     * One lighting pass over the sections of one chunk. A cell is numbered {@code y << 8 | z << 4 | x}, with y counted
     * from the bottom of the world, which is also the order of the sections' light arrays.
     */
    private static final class Spreader {
        private final LevelChunkSection[] sections;
        private final int height;
        private final byte[] levels;
        private final Workspace workspace;
        private final boolean[] airOnly;
        /** Per section: false when every block in it blocks all light, so it stays dark. */
        private final boolean[] lets;
        private int queued;

        Spreader(LevelChunkSection[] sections, int sectionCount, byte[] levels, Workspace workspace) {
            this.sections = sections;
            this.height = sectionCount * 16;
            this.levels = levels;
            this.workspace = workspace;
            this.airOnly = new boolean[sectionCount];
            this.lets = new boolean[sectionCount];
            for (int index = 0; index < sectionCount; index++) {
                this.airOnly[index] = sections[index].hasOnlyAir();
                this.lets[index] = this.airOnly[index] || sections[index].maybeHas(LETS_LIGHT_THROUGH);
            }
        }

        void spread() {
            int[] lowestFullLight = fallStraightDown();
            queueFullLightBesideDarkerCells(lowestFullLight);
            spreadQueued();
        }

        /**
         * The sky falls through blocks that dampen nothing, keeping 15. Returns the lowest cell with 15 of every
         * column (NO_LIGHT_RUN when the column's top is already blocked).
         */
        private int[] fallStraightDown() {
            int[] lowest = new int[CELLS_PER_LAYER];
            for (int column = 0; column < CELLS_PER_LAYER; column++) {
                int y = this.height - 1;
                while (y >= 0 && dampeningAt(column, y) == 0) {
                    this.levels[y << Y_SHIFT | column] = MAX_LIGHT;
                    y--;
                }
                lowest[column] = y == this.height - 1 ? NO_LIGHT_RUN : y + 1;
            }
            return lowest;
        }

        /**
         * Light of 15 is what spreads from. A cell of the straight run only has something to give where a neighbour
         * column's run starts higher (the neighbour is darker there), and the lowest cell of every run gives down.
         */
        private void queueFullLightBesideDarkerCells(int[] lowest) {
            for (int column = 0; column < CELLS_PER_LAYER; column++) {
                if (lowest[column] == NO_LIGHT_RUN) {
                    continue;
                }
                int x = column & X_MASK;
                int z = column >> Z_SHIFT;
                int darkerBelow = Math.max(
                    Math.max(neighbourLowest(lowest, x - 1, z), neighbourLowest(lowest, x + 1, z)),
                    Math.max(neighbourLowest(lowest, x, z - 1), neighbourLowest(lowest, x, z + 1)));
                for (int y = lowest[column]; y <= Math.max(lowest[column], darkerBelow); y++) {
                    enqueue(y << Y_SHIFT | column);
                }
            }
        }

        /** The lowest 15 of a neighbour column; 0 beyond the chunk's edge, and the top of the chunk for a column without any. */
        private int neighbourLowest(int[] lowest, int x, int z) {
            if (x < 0 || x > X_MASK || z < 0 || z > X_MASK) {
                return 0;
            }
            int neighbour = lowest[z << Z_SHIFT | x];
            return neighbour == NO_LIGHT_RUN ? this.height - 1 : neighbour;
        }

        private void spreadQueued() {
            for (int head = 0; head < this.queued; head++) {
                int cell = this.workspace.queue()[head];
                int level = this.levels[cell];
                if (level > 1) {
                    spreadFrom(cell, level);
                }
            }
        }

        private void spreadFrom(int cell, int level) {
            int x = cell & X_MASK;
            int z = cell >> Z_SHIFT & X_MASK;
            int y = cell >> Y_SHIFT;
            if (x > 0) {
                lightNeighbour(cell - 1, level);
            }
            if (x < X_MASK) {
                lightNeighbour(cell + 1, level);
            }
            if (z > 0) {
                lightNeighbour(cell - (1 << Z_SHIFT), level);
            }
            if (z < X_MASK) {
                lightNeighbour(cell + (1 << Z_SHIFT), level);
            }
            if (y > 0) {
                lightNeighbour(cell - (1 << Y_SHIFT), level);
            }
            if (y < this.height - 1) {
                lightNeighbour(cell + (1 << Y_SHIFT), level);
            }
        }

        /** Lights a cell next to a lit one, when that gives it more light than it has, and lets it spread on. */
        private void lightNeighbour(int cell, int fromLevel) {
            int reachable = fromLevel - 1;
            if (reachable <= this.levels[cell] || !this.lets[cell >> 12]) {
                return;
            }
            int lit = fromLevel - Math.max(1, dampeningAt(cell & (CELLS_PER_LAYER - 1), cell >> Y_SHIFT));
            if (lit > this.levels[cell]) {
                this.levels[cell] = (byte) lit;
                enqueue(cell);
            }
        }

        private void enqueue(int cell) {
            this.workspace.append(this.queued++, cell);
        }

        private int dampeningAt(int column, int y) {
            int index = y >> 4;
            if (this.airOnly[index]) {
                return 0;
            }
            if (!this.lets[index]) {
                return OPAQUE_DAMPENING;
            }
            return this.sections[index].getBlockState(column & X_MASK, y & 15, column >> Z_SHIFT).getLightDampening();
        }
    }
}
