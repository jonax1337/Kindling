package net.kindlingmc.chunk;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
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
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.slf4j.Logger;

/**
 * Writes the block and biome containers of a chunk section straight to NBT, the counterpart of
 * {@link SectionPaletteDecoder}. The DataFixerUpper codecs build a {@code DataResult}, an {@code Either} and a map
 * builder for every palette entry and turn every block name into a new string.
 *
 * <p>The result equals what the codecs produce: a default block state is its block's name, any other state is
 * {@code {id, properties}} with every property, a biome is its name, and {@code data} is left out when the container
 * has no storage. A palette entry the encoder does not know how to write (a biome that is not registered) leaves the
 * whole container to the codec. With {@code -Dkindling.verifyPaletteEncoder=true} every container is written both
 * ways and differences are logged, for checking the encoder after a Minecraft update.
 */
public final class SectionPaletteEncoder {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final boolean VERIFY = Boolean.getBoolean("kindling.verifyPaletteEncoder");
    private static final String PALETTE = "palette";
    private static final String DATA = "data";
    private static final String STATE_ID = "id";
    private static final String STATE_PROPERTIES = "properties";
    private static final Map<Block, BlockFormat> BLOCK_FORMATS = new ConcurrentHashMap<>();

    /**
     * A block's name and its properties in the order the codec puts them into the tag. The order matters on disk:
     * the codec chains the properties with {@code Codec.mapPair}, which writes the last one first, and keys that share
     * a hash slot are written in the order they were put.
     */
    private record BlockFormat(StringTag name, Property<?>[] propertiesInCodecOrder) {

        static BlockFormat of(final Block block) {
            final List<Property<?>> properties = new ArrayList<>(block.getStateDefinition().getProperties());
            Collections.reverse(properties);
            return new BlockFormat(StringTag.valueOf(BuiltInRegistries.BLOCK.getKey(block).toString()), properties.toArray(new Property<?>[0]));
        }
    }

    private SectionPaletteEncoder() {
    }

    public static Tag writeBlockStates(
        final PalettedContainerRO<BlockState> container, final Strategy<BlockState> strategy, final Supplier<Tag> codec
    ) {
        return orCodec(write(container.pack(strategy), SectionPaletteEncoder::encodeBlockState), codec);
    }

    public static Tag writeBiomes(
        final PalettedContainerRO<Holder<Biome>> container, final Strategy<Holder<Biome>> strategy, final Supplier<Tag> codec
    ) {
        return orCodec(write(container.pack(strategy), SectionPaletteEncoder::encodeBiome), codec);
    }

    private static <T> Optional<Tag> write(final PalettedContainerRO.PackedData<T> packed, final Function<T, Optional<Tag>> entryEncoder) {
        final ListTag palette = new ListTag();
        for (final T entry : packed.paletteEntries()) {
            final Optional<Tag> encoded = entryEncoder.apply(entry);
            if (encoded.isEmpty()) {
                return Optional.empty();
            }
            palette.add(encoded.get());
        }
        final CompoundTag container = new CompoundTag();
        container.put(PALETTE, palette);
        packed.storage().ifPresent(storage -> container.put(DATA, new LongArrayTag(storage.toArray())));
        return Optional.of(container);
    }

    /** {@code BlockState.CODEC}: the default state as the block's name, any other state with all of its properties. */
    private static Optional<Tag> encodeBlockState(final BlockState state) {
        final Block block = state.getBlock();
        final BlockFormat format = BLOCK_FORMATS.computeIfAbsent(block, BlockFormat::of);
        if (state == block.defaultBlockState()) {
            return Optional.of(format.name());
        }
        final CompoundTag properties = new CompoundTag();
        for (final Property<?> property : format.propertiesInCodecOrder()) {
            properties.putString(property.getName(), valueName(state, property));
        }
        final CompoundTag tag = new CompoundTag();
        tag.put(STATE_PROPERTIES, properties); // the codec puts the dispatched fields first and the "id" key last
        tag.put(STATE_ID, format.name());
        return Optional.of(tag);
    }

    private static <V extends Comparable<V>> String valueName(final BlockState state, final Property<V> property) {
        return property.getName(state.getValue(property));
    }

    /** {@code Registry#holderByNameCodec()}: a registered biome as its name; anything else is left to the codec. */
    private static Optional<Tag> encodeBiome(final Holder<Biome> biome) {
        return biome instanceof Holder.Reference<Biome> reference && reference.unwrapKey().isPresent()
            ? Optional.of(StringTag.valueOf(reference.key().identifier().toString()))
            : Optional.empty();
    }

    private static Tag orCodec(final Optional<Tag> encoded, final Supplier<Tag> codec) {
        if (VERIFY) {
            final Tag expected = codec.get();
            encoded.filter(actual -> !actual.equals(expected))
                .ifPresent(actual -> LOGGER.error("Palette encoder differs from the codec: {} instead of {}", actual, expected));
            return encoded.orElse(expected);
        }
        return encoded.orElseGet(codec);
    }
}
