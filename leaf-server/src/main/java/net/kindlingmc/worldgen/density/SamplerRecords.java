package net.kindlingmc.worldgen.density;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;

/**
 * Reads and rebuilds Paper's sampler records generically. Several of them are private nested records, so the
 * accessors and the canonical constructor are opened with reflection; this happens at build time only.
 */
final class SamplerRecords {
    private static final ClassValue<Shape> SHAPES = new ClassValue<>() {
        @Override
        protected Shape computeValue(Class<?> type) {
            return Shape.of(type);
        }
    };

    private SamplerRecords() {
    }

    /** A Paper sampler class by name; fails loudly when Paper renames it. */
    static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Paper sampler class not found: " + name, e);
        }
    }

    static boolean isRecord(DensitySampler sampler) {
        return sampler.getClass().isRecord();
    }

    /** The record components of a sampler in declaration order, or an empty list for a non-record sampler. */
    static List<Object> components(DensitySampler sampler) {
        if (!isRecord(sampler)) {
            return List.of();
        }
        return SHAPES.get(sampler.getClass()).values(sampler);
    }

    /** One record component by name; fails loudly when Paper renames it, so the builder never guesses. */
    static Object component(DensitySampler sampler, String name) {
        Shape shape = SHAPES.get(sampler.getClass());
        int index = shape.names.indexOf(name);
        if (index < 0) {
            throw new IllegalStateException(sampler.getClass().getName() + " has no component " + name + ", only " + shape.names);
        }
        return shape.values(sampler).get(index);
    }

    static String componentName(DensitySampler sampler, int index) {
        return SHAPES.get(sampler.getClass()).names.get(index);
    }

    /** The direct child samplers of a record sampler (components typed as DensitySampler). */
    static List<DensitySampler> children(DensitySampler sampler) {
        List<DensitySampler> children = new ArrayList<>();
        for (Object value : components(sampler)) {
            if (value instanceof DensitySampler child) {
                children.add(child);
            }
        }
        return children;
    }

    static boolean hasSamplerArray(DensitySampler sampler) {
        return isRecord(sampler) && SHAPES.get(sampler.getClass()).hasSamplerArray;
    }

    /** The same record with every child sampler replaced by rewrite(child); returns the original if nothing changed. */
    static DensitySampler rebuild(DensitySampler sampler, UnaryOperator<DensitySampler> rewrite) {
        Shape shape = SHAPES.get(sampler.getClass());
        List<Object> values = shape.values(sampler);
        Object[] arguments = values.toArray();
        boolean changed = false;
        for (int i = 0; i < arguments.length; ++i) {
            if (arguments[i] instanceof DensitySampler child) {
                DensitySampler rewritten = rewrite.apply(child);
                changed |= rewritten != child;
                arguments[i] = rewritten;
            }
        }
        return changed ? shape.construct(arguments) : sampler;
    }

    private record Shape(List<Method> accessors, List<String> names, Constructor<?> constructor, boolean hasSamplerArray) {
        static Shape of(Class<?> type) {
            if (!type.isRecord()) {
                throw new IllegalArgumentException(type.getName() + " is not a record");
            }
            RecordComponent[] components = type.getRecordComponents();
            List<Method> accessors = new ArrayList<>();
            List<String> names = new ArrayList<>();
            Class<?>[] types = new Class<?>[components.length];
            boolean hasSamplerArray = false;
            for (int i = 0; i < components.length; ++i) {
                Method accessor = components[i].getAccessor();
                accessor.setAccessible(true);
                accessors.add(accessor);
                names.add(components[i].getName());
                types[i] = components[i].getType();
                hasSamplerArray |= types[i].isArray() && DensitySampler.class.isAssignableFrom(types[i].getComponentType());
            }
            try {
                Constructor<?> constructor = type.getDeclaredConstructor(types);
                constructor.setAccessible(true);
                return new Shape(List.copyOf(accessors), List.copyOf(names), constructor, hasSamplerArray);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("No canonical constructor on " + type.getName(), e);
            }
        }

        List<Object> values(Object record) {
            List<Object> values = new ArrayList<>(this.accessors.size());
            try {
                for (Method accessor : this.accessors) {
                    values.add(accessor.invoke(record));
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read " + record.getClass().getName(), e);
            }
            return values;
        }

        DensitySampler construct(Object[] arguments) {
            try {
                return (DensitySampler)this.constructor.newInstance(arguments);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot rebuild " + this.constructor.getDeclaringClass().getName(), e);
            }
        }
    }
}
