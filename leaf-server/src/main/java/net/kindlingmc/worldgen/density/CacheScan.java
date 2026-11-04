package net.kindlingmc.worldgen.density;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.util.BoundedFloatFunction;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.densityfunction.CachingDensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;

/**
 * Finds the caches inside a Paper sampler the evaluator does not take apart. A spline keeps its coordinate samplers
 * in private classes, so the walk reads fields reflectively, but only through samplers, splines, coordinates and
 * collections or arrays of them. Build time only.
 */
final class CacheScan {
    private CacheScan() {
    }

    /** Adds every cache below sampler (the sampler itself included) to found, keyed by cache id. */
    static void collect(Object sampler, Map<Integer, CachingDensitySampler> found) {
        walk(sampler, found, new IdentityHashMap<>());
    }

    private static void walk(Object value, Map<Integer, CachingDensitySampler> found, Map<Object, Boolean> seen) {
        if (value == null || seen.put(value, Boolean.TRUE) != null) {
            return;
        }
        if (value instanceof CachingDensitySampler cache) {
            found.putIfAbsent(cache.id(), cache);
        }
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                walk(element, found, seen);
            }
        } else if (value instanceof Object[] array) {
            for (Object element : array) {
                walk(element, found, seen);
            }
        } else if (isWalked(value)) {
            for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
                        walk(read(field, value), found, seen);
                    }
                }
            }
        }
    }

    private static boolean isWalked(Object value) {
        return value instanceof DensitySampler || value instanceof CubicSpline || value instanceof BoundedFloatFunction;
    }

    private static Object read(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Cannot read " + field + " while looking for caches", e);
        }
    }
}
