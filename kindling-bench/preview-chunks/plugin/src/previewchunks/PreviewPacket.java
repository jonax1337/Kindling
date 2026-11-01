package previewchunks;

import io.netty.buffer.Unpooled;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A preview chunk as the packet a vanilla client gets for a real chunk: the client's heightmaps, the section data, no
 * block entities, and the estimated sky light of {@link ChunkPacket#skyFromAbove}. rawBytes is the uncompressed size of
 * sections and light.
 */
record PreviewPacket(ClientboundLevelChunkWithLightPacket packet, int rawBytes) {
    private static final Set<Heightmap.Types> CLIENT_HEIGHTMAPS = clientHeightmaps();
    private static final MethodHandle CHUNK_DATA = chunkDataConstructor();
    private static final int LIGHT_ARRAY_BYTES = 2048;

    static PreviewPacket of(ProtoChunk chunk) {
        byte[] sections = sectionBytes(chunk.getSections());
        ChunkPacket.Light light = ChunkPacket.skyFromAbove(chunk.getSections());
        ClientboundLightUpdatePacketData lightData = new ClientboundLightUpdatePacketData(light.sky().present(), light.block().present(),
            light.sky().empty(), light.block().empty(), light.sky().arrays(), light.block().arrays());
        ChunkPos pos = chunk.getPos();
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(pos.x(), pos.z(),
            chunkData(heightmaps(chunk), sections), lightData);
        return new PreviewPacket(packet, sections.length + LIGHT_ARRAY_BYTES * (light.sky().arrays().size() + light.block().arrays().size()));
    }

    /** Replaces the highest non-air block of every column (terrain or water) with the given block. */
    static void paintSurface(ProtoChunk chunk, BlockState block) {
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE));
        ChunkPos pos = chunk.getPos();
        BlockPos.MutableBlockPos top = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int topY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                chunk.setBlockState(top.set(pos.getMinBlockX() + x, topY, pos.getMinBlockZ() + z), block);
            }
        }
    }

    private static Map<Heightmap.Types, long[]> heightmaps(ProtoChunk chunk) {
        Heightmap.primeHeightmaps(chunk, CLIENT_HEIGHTMAPS);
        Map<Heightmap.Types, long[]> heightmaps = new EnumMap<>(Heightmap.Types.class);
        for (Heightmap.Types type : CLIENT_HEIGHTMAPS) {
            heightmaps.put(type, chunk.getOrCreateHeightmapUnprimed(type).getRawData().clone());
        }
        return heightmaps;
    }

    private static byte[] sectionBytes(LevelChunkSection[] sections) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int index = 0; index < sections.length; index++) {
                sections[index].write(buffer, null, index);
            }
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static ClientboundLevelChunkPacketData chunkData(Map<Heightmap.Types, long[]> heightmaps, byte[] sections) {
        try {
            return (ClientboundLevelChunkPacketData) CHUNK_DATA.invoke(heightmaps, sections, List.of());
        } catch (Throwable t) {
            throw new IllegalStateException("cannot build ClientboundLevelChunkPacketData", t);
        }
    }

    private static Set<Heightmap.Types> clientHeightmaps() {
        Set<Heightmap.Types> types = EnumSet.noneOf(Heightmap.Types.class);
        Arrays.stream(Heightmap.Types.values()).filter(Heightmap.Types::sendToClient).forEach(types::add);
        return types;
    }

    /** The private constructor the packet's stream codec decodes into: heightmaps, section bytes, block entities. */
    private static MethodHandle chunkDataConstructor() {
        try {
            return MethodHandles.privateLookupIn(ClientboundLevelChunkPacketData.class, MethodHandles.lookup())
                .findConstructor(ClientboundLevelChunkPacketData.class, MethodType.methodType(void.class, Map.class, byte[].class, List.class));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ClientboundLevelChunkPacketData(Map, byte[], List) not found", e);
        }
    }
}
