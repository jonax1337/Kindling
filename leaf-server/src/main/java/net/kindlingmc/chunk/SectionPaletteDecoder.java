package net.kindlingmc.chunk;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Reads the block and biome containers of a saved chunk section straight from NBT. The DataFixerUpper codecs that
 * vanilla uses build several {@code DataResult}s and parse an identifier for every palette entry, which made them
 * the largest single cost of loading a chunk from disk.
 *
 * <p>The result is exactly what the codecs produce. Everything the codecs would report as an error (unknown or
 * invalid names, unexpected tag types, a broken container) is left to them: the decoder then falls back to the
 * codec, so such chunks are logged and repaired as in vanilla. With {@code -Dkindling.verifyPaletteDecoder=true} every
 * section is decoded both ways and differences are logged, for checking the decoder after a Minecraft update.
 */
public final class SectionPaletteDecoder {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final boolean VERIFY = Boolean.getBoolean("kindling.verifyPaletteDecoder");
    private static final String PALETTE = "palette";
    private static final String DATA = "data";
    private static final String STATE_ID = "id";
    private static final String STATE_PROPERTIES = "properties";
    private static final int SECTION_BLOCKS_PER_AXIS = 16;
    private static final int SECTION_BIOMES_PER_AXIS = 4;
    private static final RegistryNames<Block> BLOCKS = RegistryNames.of(BuiltInRegistries.BLOCK);

    private SectionPaletteDecoder() {
    }

    public static PalettedContainer<BlockState> readBlockStates(
        final CompoundTag container, final PalettedContainerFactory factory, final BlockState @Nullable [] presetValues,
        final Supplier<PalettedContainer<BlockState>> codec
    ) {
        final Optional<PalettedContainer<BlockState>> decoded = readPacked(container, SectionPaletteDecoder::decodeBlockState)
            .flatMap(packed -> PalettedContainer.unpack(factory.blockStatesStrategy(), packed, factory.defaultBlockState(), presetValues).result());
        return orCodec(decoded, codec, SECTION_BLOCKS_PER_AXIS);
    }

    public static PalettedContainer<Holder<Biome>> readBiomes(
        final CompoundTag container, final PalettedContainerFactory factory, final Registry<Biome> biomes,
        final Supplier<PalettedContainer<Holder<Biome>>> codec
    ) {
        final RegistryNames<Biome> names = RegistryNames.of(biomes);
        final Optional<PalettedContainer<Holder<Biome>>> decoded = readPacked(container, entry -> decodeBiome(names, entry))
            .flatMap(packed -> PalettedContainer.unpack(factory.biomeStrategy(), packed, factory.defaultBiome(), null).result());
        return orCodec(decoded, codec, SECTION_BIOMES_PER_AXIS);
    }

    /** The palette and storage as the container codec reads them, or empty where the codec would not succeed cleanly. */
    private static <T> Optional<PalettedContainerRO.PackedData<T>> readPacked(
        final CompoundTag container, final Function<Tag, Optional<T>> entryDecoder
    ) {
        final Tag data = container.get(DATA);
        if (!(container.get(PALETTE) instanceof ListTag entries) || (data != null && !(data instanceof LongArrayTag))) {
            return Optional.empty();
        }
        final Optional<LongStream> storage = data instanceof LongArrayTag longs ? Optional.of(Arrays.stream(longs.getAsLongArray())) : Optional.empty();
        return decodeEntries(entries, entryDecoder).map(palette -> new PalettedContainerRO.PackedData<>(palette, storage));
    }

    private static <T> Optional<List<T>> decodeEntries(final ListTag entries, final Function<Tag, Optional<T>> entryDecoder) {
        final List<T> palette = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            final Optional<T> value = entryDecoder.apply(entries.get(i));
            if (value.isEmpty()) {
                return Optional.empty();
            }
            palette.add(value.get());
        }
        return Optional.of(palette);
    }

    /**
     * {@code BlockState.CODEC}: a bare name stands for the block's default state, a compound for a block with its
     * properties. Missing, non-string or unknown property values keep the default value, as in the codec.
     */
    private static Optional<BlockState> decodeBlockState(final Tag entry) {
        if (entry instanceof StringTag(String name)) {
            return BLOCKS.lookup(name).map(block -> block.value().defaultBlockState());
        }
        if (entry instanceof CompoundTag state && state.get(STATE_ID) instanceof StringTag(String name)) {
            final Tag properties = state.get(STATE_PROPERTIES);
            return BLOCKS.lookup(name).map(block -> withProperties(block.value(), properties));
        }
        return Optional.empty();
    }

    private static BlockState withProperties(final Block block, final @Nullable Tag properties) {
        BlockState state = block.defaultBlockState();
        if (!(properties instanceof CompoundTag values)) {
            return state;
        }
        for (final Property<?> property : block.getStateDefinition().getProperties()) {
            state = withValue(state, property, values.get(property.getName()));
        }
        return state;
    }

    private static <V extends Comparable<V>> BlockState withValue(final BlockState state, final Property<V> property, final @Nullable Tag value) {
        if (value instanceof StringTag(String name)) {
            final Optional<V> parsed = property.getValue(name);
            if (parsed.isPresent()) {
                return state.setValue(property, parsed.get());
            }
        }
        return state;
    }

    private static Optional<Holder<Biome>> decodeBiome(final RegistryNames<Biome> names, final Tag entry) {
        return entry instanceof StringTag(String name) ? names.lookup(name).map(biome -> biome) : Optional.empty();
    }

    private static <T> PalettedContainer<T> orCodec(
        final Optional<PalettedContainer<T>> decoded, final Supplier<PalettedContainer<T>> codec, final int perAxis
    ) {
        if (VERIFY) {
            final PalettedContainer<T> expected = codec.get();
            verify(decoded, expected, perAxis);
            return decoded.orElse(expected);
        }
        return decoded.orElseGet(codec);
    }

    private static <T> void verify(final Optional<PalettedContainer<T>> decoded, final PalettedContainer<T> expected, final int perAxis) {
        if (decoded.isEmpty()) {
            return;
        }
        final PalettedContainer<T> actual = decoded.get();
        for (int y = 0; y < perAxis; y++) {
            for (int z = 0; z < perAxis; z++) {
                for (int x = 0; x < perAxis; x++) {
                    if (actual.get(x, y, z) != expected.get(x, y, z)) {
                        LOGGER.error("Palette decoder differs from the codec at {} {} {}: {} instead of {}", x, y, z, actual.get(x, y, z), expected.get(x, y, z));
                        return;
                    }
                }
            }
        }
    }
}
