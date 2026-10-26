package net.kindlingmc.chunk;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

/**
 * Resolves names to registry entries the way {@code Registry#holderByNameCodec()} does, and remembers every name it
 * resolved, so a name met again costs one map lookup instead of parsing and validating an {@link Identifier}.
 * Only names that resolve are kept, so the cache never grows beyond the size of the registry. Thread-safe.
 */
final class RegistryNames<T> {

    private static final Map<Registry<?>, RegistryNames<?>> BY_REGISTRY = new ConcurrentHashMap<>();

    private final Registry<T> registry;
    private final Map<String, Holder.Reference<T>> resolved = new ConcurrentHashMap<>();

    private RegistryNames(final Registry<T> registry) {
        this.registry = registry;
    }

    @SuppressWarnings("unchecked")
    static <T> RegistryNames<T> of(final Registry<T> registry) {
        return (RegistryNames<T>) BY_REGISTRY.computeIfAbsent(registry, RegistryNames::new);
    }

    /** The entry the codec decodes {@code name} to; empty where the codec fails (invalid or unknown name). */
    Optional<Holder.Reference<T>> lookup(final String name) {
        final Holder.Reference<T> known = this.resolved.get(name);
        if (known != null) {
            return Optional.of(known);
        }
        final Optional<Holder.Reference<T>> found = Identifier.read(name).result().flatMap(this.registry::get);
        found.ifPresent(holder -> this.resolved.put(name, holder));
        return found;
    }
}
