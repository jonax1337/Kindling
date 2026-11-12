package net.kindlingmc.preview;

import io.netty.buffer.Unpooled;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A preview chunk as the packet a vanilla client gets for a real chunk: the client's heightmaps, the section data, no
 * block entities, and the light it is given (estimated by {@link SkyLight} or as saved). rawBytes is the uncompressed size of sections and
 * light. The packet is built once and may be sent to any number of players.
 */
public record PreviewPacket(ClientboundLevelChunkWithLightPacket packet, int rawBytes) {
    private static final Set<Heightmap.Types> CLIENT_HEIGHTMAPS = clientHeightmaps();
    private static final MethodHandle CHUNK_DATA = chunkDataConstructor();
    private static final int LIGHT_ARRAY_BYTES = 2048;

    /** Builds the packet of a finished preview with the given light; primes the chunk's client heightmaps, so the chunk belongs to the caller. */
    static PreviewPacket of(ProtoChunk chunk, SkyLight.Light light) {
        byte[] sections = sectionBytes(chunk.getSections());
        ClientboundLightUpdatePacketData lightData = new ClientboundLightUpdatePacketData(light.sky().present(), light.block().present(),
            light.sky().empty(), light.block().empty(), light.sky().arrays(), light.block().arrays());
        ChunkPos pos = chunk.getPos();
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(pos.x(), pos.z(),
            chunkData(heightmaps(chunk), sections), lightData);
        return new PreviewPacket(packet, sections.length + LIGHT_ARRAY_BYTES * (light.sky().arrays().size() + light.block().arrays().size()));
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
        for (Heightmap.Types type : Heightmap.Types.values()) {
            if (type.sendToClient()) {
                types.add(type);
            }
        }
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
