package previewchunks;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;

/**
 * Reads a uniform random sample of saved full chunks from the .mca files of a region directory, read only (Anvil:
 * zlib, gzip or uncompressed; other chunks are counted as skipped).
 */
final class RegionReader {
    private static final int SECTOR_BYTES = 4096;
    private static final int CHUNKS_PER_REGION = 1024;
    private static final int GZIP = 1;
    private static final int ZLIB = 2;
    private static final int UNCOMPRESSED = 3;
    private static final String FULL = "minecraft:full";
    private static final long SAMPLE_SEED = 0x5a3b1eL;

    /** One saved chunk: its position and its NBT. */
    record SavedChunk(ChunkPos pos, CompoundTag tag) {
        /** The chunk's sections from minSectionY on, empty where the chunk has none. */
        LevelChunkSection[] sections(PalettedContainerFactory factory, int minSectionY, int count) {
            LevelChunkSection[] sections = new LevelChunkSection[count];
            for (int index = 0; index < count; index++) {
                sections[index] = new LevelChunkSection(factory.createForBlockStates(), factory.createForBiomes());
            }
            ListTag sectionTags = this.tag.getListOrEmpty("sections");
            for (int i = 0; i < sectionTags.size(); i++) {
                Optional<CompoundTag> section = sectionTags.getCompound(i);
                int index = section.map(s -> s.getByteOr("Y", (byte) 0) - minSectionY).orElse(-1);
                if (index >= 0 && index < count) {
                    sections[index] = decode(section.get(), factory);
                }
            }
            return sections;
        }

        /** The stored light arrays of one kind (SkyLight or BlockLight) per light section: one below and one above the chunk sections. */
        byte[][] lightLayers(String name, int minSectionY, int count) {
            byte[][] layers = new byte[count + 2][];
            ListTag sectionTags = this.tag.getListOrEmpty("sections");
            for (int i = 0; i < sectionTags.size(); i++) {
                sectionTags.getCompound(i).ifPresent(section -> {
                    int index = section.getByteOr("Y", (byte) 0) - (minSectionY - 1);
                    if (index >= 0 && index < layers.length) {
                        layers[index] = section.getByteArray(name).orElse(null);
                    }
                });
            }
            return layers;
        }

        /** The three heightmaps a client is sent, as stored. */
        List<long[]> heightmaps() {
            CompoundTag heightmaps = this.tag.getCompoundOrEmpty("Heightmaps");
            return List.of(heightmaps.getLongArray("MOTION_BLOCKING").orElse(new long[0]),
                heightmaps.getLongArray("MOTION_BLOCKING_NO_LEAVES").orElse(new long[0]),
                heightmaps.getLongArray("WORLD_SURFACE").orElse(new long[0]));
        }

        private static LevelChunkSection decode(CompoundTag section, PalettedContainerFactory factory) {
            return new LevelChunkSection(
                section.getCompound("block_states").map(t -> factory.blockStatesContainerCodec().parse(NbtOps.INSTANCE, t).getOrThrow())
                    .orElseGet(factory::createForBlockStates),
                section.getCompound("biomes").map(t -> factory.biomeContainerRWCodec().parse(NbtOps.INSTANCE, t).getOrThrow())
                    .orElseGet(factory::createForBiomes));
        }
    }

    /** The sampled full chunks and, by reason, how many sampled entries were skipped on the way. */
    record Sample(List<SavedChunk> chunks, Map<String, Integer> skipped) {
    }

    private record Entry(Path file, long sector) {
    }

    private RegionReader() {
    }

    static Sample fullChunks(Path regionDir, int limit) throws IOException {
        Map<String, Integer> skipped = new TreeMap<>();
        List<Entry> entries = entries(regionDir, skipped);
        Collections.shuffle(entries, new Random(SAMPLE_SEED));
        List<SavedChunk> chunks = new ArrayList<>();
        for (Entry entry : entries) {
            if (chunks.size() >= limit) {
                break;
            }
            read(entry, skipped).ifPresent(chunks::add);
        }
        return new Sample(List.copyOf(chunks), skipped);
    }

    private static List<Entry> entries(Path regionDir, Map<String, Integer> skipped) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> files = Files.list(regionDir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".mca")).sorted().toList()) {
                try {
                    entries.addAll(header(file));
                } catch (IOException e) {
                    skipped.merge("unreadable region file", 1, Integer::sum);
                }
            }
        }
        return entries;
    }

    private static List<Entry> header(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < CHUNKS_PER_REGION; i++) {
                int location = in.readInt();
                if (location != 0) {
                    entries.add(new Entry(file, location >>> 8));
                }
            }
            return entries;
        }
    }

    private static Optional<SavedChunk> read(Entry entry, Map<String, Integer> skipped) {
        try (RandomAccessFile region = new RandomAccessFile(entry.file().toFile(), "r")) {
            region.seek(entry.sector() * SECTOR_BYTES);
            int length = region.readInt();
            int compression = region.readByte();
            byte[] payload = new byte[length - 1];
            region.readFully(payload);
            Optional<CompoundTag> tag = decompress(compression, payload);
            String reason = tag.isEmpty() ? "compression type " + compression : isFull(tag.get()) ? null : "not a full chunk";
            if (reason != null) {
                skipped.merge(reason, 1, Integer::sum);
                return Optional.empty();
            }
            return tag.map(RegionReader::saved);
        } catch (IOException | RuntimeException e) {
            skipped.merge("unreadable chunk", 1, Integer::sum);
            return Optional.empty();
        }
    }

    private static Optional<CompoundTag> decompress(int compression, byte[] payload) throws IOException {
        InputStream raw = new ByteArrayInputStream(payload);
        InputStream in = switch (compression) {
            case GZIP -> new GZIPInputStream(raw);
            case ZLIB -> new InflaterInputStream(raw);
            case UNCOMPRESSED -> raw;
            default -> null;
        };
        if (in == null) {
            return Optional.empty();
        }
        try (DataInputStream data = new DataInputStream(new BufferedInputStream(in))) {
            return Optional.of(NbtIo.read(data));
        }
    }

    private static boolean isFull(CompoundTag tag) {
        return tag.getString("Status").map(FULL::equals).orElse(false);
    }

    private static SavedChunk saved(CompoundTag tag) {
        return new SavedChunk(new ChunkPos(tag.getIntOr("xPos", 0), tag.getIntOr("zPos", 0)), tag);
    }
}
