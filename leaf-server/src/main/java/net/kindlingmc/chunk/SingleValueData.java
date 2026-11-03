package net.kindlingmc.chunk;

import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.chunk.Configuration;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;

/**
 * One read-only {@link PalettedContainer.Data} per value for the containers of one {@link Strategy} that hold a single
 * value: a section of air, or a section of one biome. Paper gives every container its own hash palette, so each of
 * these carried a palette with three small arrays and a storage of its own. Sharing them per value leaves every block
 * and biome as it was.
 *
 * <p>Shared data is never written. Its palette is full with its one value at zero bits, so a second value resizes the
 * container onto new data of its own, and its storage holds no bits. {@code PalettedContainer} never reuses shared data
 * as the target of a resize or a read.
 *
 * <p>Lookups read an immutable snapshot without locking; the few values that are new get added under the lock. Every
 * cache starts on one shared empty snapshot, because a strategy is made for every chunk that is promoted to full.
 */
public final class SingleValueData<T> {

    private static final IdentityHashMap<Object, PalettedContainer.Data<?>> NO_VALUES = new IdentityHashMap<>();

    @SuppressWarnings("unchecked")
    private volatile IdentityHashMap<T, PalettedContainer.Data<T>> byValue = (IdentityHashMap<T, PalettedContainer.Data<T>>) (IdentityHashMap<?, ?>) NO_VALUES;

    /**
     * The shared data holding only {@code value}, made on the first request.
     *
     * @param singleValue the strategy's configuration for zero bits per entry
     */
    public PalettedContainer.Data<T> of(final Strategy<T> strategy, final Configuration singleValue, final T value) {
        final PalettedContainer.Data<T> known = this.byValue.get(value);
        return known != null ? known : this.add(strategy, singleValue, value);
    }

    /** Whether {@code data} is the shared data of its value, which must never be written. */
    public boolean isShared(final PalettedContainer.Data<T> data) {
        return data.storage().getBits() == 0 && data.palette().getSize() == 1 && this.byValue.get(data.palette().valueFor(0)) == data;
    }

    private synchronized PalettedContainer.Data<T> add(final Strategy<T> strategy, final Configuration singleValue, final T value) {
        final PalettedContainer.Data<T> known = this.byValue.get(value);
        if (known != null) {
            return known;
        }
        if (singleValue.bitsInMemory() != 0) {
            throw new IllegalArgumentException("Only a zero-bit configuration holds a single value, not " + singleValue);
        }
        final PalettedContainer.Data<T> data = new PalettedContainer.Data<>(
            singleValue, new ZeroBitStorage(strategy.entryCount()), singleValue.createPalette(strategy, List.of(value))
        );
        final IdentityHashMap<T, PalettedContainer.Data<T>> next = new IdentityHashMap<>(this.byValue);
        next.put(value, data);
        this.byValue = next;
        return data;
    }
}
