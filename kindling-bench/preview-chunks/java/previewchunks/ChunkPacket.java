package previewchunks;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.zip.Deflater;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The body of a chunk-with-light packet laid out as ClientboundLevelChunkWithLightPacket writes it (position,
 * heightmaps, section data, no block entities, light masks and arrays) and its size after zlib, as the network
 * compression sends it. Only the sizes matter here: ids and heightmap types are stand-ins of the same shape.
 */
final class ChunkPacket {
    private static final int PACKET_ID = 0x27;
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

    record Size(int raw, int compressed) {
    }

    private ChunkPacket() {
    }

    static Size measure(ChunkPos pos, LevelChunkSection[] sections, List<long[]> heightmaps, Light light) {
        byte[] raw = encode(pos, sections, heightmaps, light);
        return new Size(raw.length, compressedLength(raw));
    }

    /**
     * Sky light falling straight down: 15 through air, one less per fluid block, 0 from the first other block on; no
     * sideways spread and no block light. Sections above the highest non-empty one get no array, as the light engine
     * stores none there. An estimate for packet sizes, not the light a client would compute.
     */
    static Light skyFromAbove(LevelChunkSection[] sections) {
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
        if (!state.getFluidState().isEmpty() && !Preview.isTerrain(state)) {
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

    private static byte[] encode(ChunkPos pos, LevelChunkSection[] sections, List<long[]> heightmaps, Light light) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(PACKET_ID);
            buffer.writeInt(pos.x());
            buffer.writeInt(pos.z());
            writeHeightmaps(buffer, heightmaps);
            writeSections(buffer, sections);
            buffer.writeVarInt(0);
            writeLight(buffer, light);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static void writeHeightmaps(FriendlyByteBuf buffer, List<long[]> heightmaps) {
        buffer.writeVarInt(heightmaps.size());
        for (int type = 0; type < heightmaps.size(); type++) {
            buffer.writeVarInt(type);
            buffer.writeLongArray(heightmaps.get(type));
        }
    }

    private static void writeSections(FriendlyByteBuf buffer, LevelChunkSection[] sections) {
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int index = 0; index < sections.length; index++) {
                sections[index].write(data, null, index);
            }
            buffer.writeVarInt(data.readableBytes());
            buffer.writeBytes(data);
        } finally {
            data.release();
        }
    }

    private static void writeLight(FriendlyByteBuf buffer, Light light) {
        buffer.writeBitSet(light.sky().present());
        buffer.writeBitSet(light.block().present());
        buffer.writeBitSet(light.sky().empty());
        buffer.writeBitSet(light.block().empty());
        writeArrays(buffer, light.sky().arrays());
        writeArrays(buffer, light.block().arrays());
    }

    private static void writeArrays(FriendlyByteBuf buffer, List<byte[]> arrays) {
        buffer.writeVarInt(arrays.size());
        for (byte[] array : arrays) {
            buffer.writeByteArray(array);
        }
    }

    private static int compressedLength(byte[] raw) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(raw);
            deflater.finish();
            byte[] chunk = new byte[8192];
            int length = 0;
            while (!deflater.finished()) {
                length += deflater.deflate(chunk);
            }
            return length;
        } finally {
            deflater.end();
        }
    }
}
