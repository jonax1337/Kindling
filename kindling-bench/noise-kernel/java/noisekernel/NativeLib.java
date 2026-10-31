package noisekernel;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/**
 * Downcalls into the kindling_noise library (library path from env KN_LIB).
 * Handles are static finals so the JIT can treat the critical downcalls as constants.
 * Every call returns the native status: 0 done, -1 size above the native cap, -2 bad argument.
 */
final class NativeLib {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = SymbolLookup.libraryLookup(libraryPath(), Arena.global());

    private static final MemoryLayout[] PERLIN_ARGUMENTS = {
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_FLOAT
    };
    private static final FunctionDescriptor PERLIN = FunctionDescriptor.of(ValueLayout.JAVA_INT, PERLIN_ARGUMENTS);
    private static final FunctionDescriptor SMEARED = PERLIN.appendArgumentLayouts(ValueLayout.JAVA_DOUBLE);

    private static final MethodHandle PERLIN_SCALAR = bind("kn_perlin_add_to_volume_scalar", PERLIN);
    private static final MethodHandle PERLIN_SIMD = bind("kn_perlin_add_to_volume_simd", PERLIN);
    private static final MethodHandle SMEARED_SCALAR = bind("kn_smeared_add_to_volume_scalar", SMEARED);
    private static final MethodHandle SMEARED_SIMD = bind("kn_smeared_add_to_volume_simd", SMEARED);
    private static final MethodHandle NOOP = bind("kn_noop", PERLIN);
    private static final MethodHandle SIMD_LEVEL = bind("kn_simd_level", FunctionDescriptor.of(ValueLayout.JAVA_INT));

    private NativeLib() {
    }

    static String libraryPathText() {
        return libraryPath().toString();
    }

    /** Forces class initialisation and returns kn_simd_level (0 scalar only, 2 AVX2). */
    static int simdLevel() {
        try {
            return (int)SIMD_LEVEL.invokeExact();
        } catch (Throwable t) {
            throw new IllegalStateException("kn_simd_level failed", t);
        }
    }

    static int perlinScalar(KernelCall c, Work w, int valuesLen) {
        DensityVolume v = c.volume();
        Leaf l = c.leaf();
        try {
            return (int)PERLIN_SCALAR.invokeExact(w.valuesSegment, valuesLen, l.pAdjSegment,
                l.offsetX, l.offsetY, l.offsetZ,
                v.sizeX(), v.sizeY(), v.sizeZ(), v.minBlockX(), v.minBlockY(), v.minBlockZ(),
                v.stepBlockX(), v.stepBlockY(), v.stepBlockZ(),
                c.xzScale(), c.yScale(), c.amplitude());
        } catch (Throwable t) {
            throw new IllegalStateException("kn_perlin_add_to_volume_scalar failed", t);
        }
    }

    static int perlinSimd(KernelCall c, Work w, int valuesLen) {
        DensityVolume v = c.volume();
        Leaf l = c.leaf();
        try {
            return (int)PERLIN_SIMD.invokeExact(w.valuesSegment, valuesLen, l.pAdjSegment,
                l.offsetX, l.offsetY, l.offsetZ,
                v.sizeX(), v.sizeY(), v.sizeZ(), v.minBlockX(), v.minBlockY(), v.minBlockZ(),
                v.stepBlockX(), v.stepBlockY(), v.stepBlockZ(),
                c.xzScale(), c.yScale(), c.amplitude());
        } catch (Throwable t) {
            throw new IllegalStateException("kn_perlin_add_to_volume_simd failed", t);
        }
    }

    static int smearedScalar(KernelCall c, Work w, int valuesLen) {
        DensityVolume v = c.volume();
        Leaf l = c.leaf();
        try {
            return (int)SMEARED_SCALAR.invokeExact(w.valuesSegment, valuesLen, l.pAdjSegment,
                l.offsetX, l.offsetY, l.offsetZ,
                v.sizeX(), v.sizeY(), v.sizeZ(), v.minBlockX(), v.minBlockY(), v.minBlockZ(),
                v.stepBlockX(), v.stepBlockY(), v.stepBlockZ(),
                c.xzScale(), c.yScale(), c.amplitude(), l.fudgeYScale);
        } catch (Throwable t) {
            throw new IllegalStateException("kn_smeared_add_to_volume_scalar failed", t);
        }
    }

    static int smearedSimd(KernelCall c, Work w, int valuesLen) {
        DensityVolume v = c.volume();
        Leaf l = c.leaf();
        try {
            return (int)SMEARED_SIMD.invokeExact(w.valuesSegment, valuesLen, l.pAdjSegment,
                l.offsetX, l.offsetY, l.offsetZ,
                v.sizeX(), v.sizeY(), v.sizeZ(), v.minBlockX(), v.minBlockY(), v.minBlockZ(),
                v.stepBlockX(), v.stepBlockY(), v.stepBlockZ(),
                c.xzScale(), c.yScale(), c.amplitude(), l.fudgeYScale);
        } catch (Throwable t) {
            throw new IllegalStateException("kn_smeared_add_to_volume_simd failed", t);
        }
    }

    static int noop(KernelCall c, Work w, int valuesLen) {
        DensityVolume v = c.volume();
        Leaf l = c.leaf();
        try {
            return (int)NOOP.invokeExact(w.valuesSegment, valuesLen, l.pAdjSegment,
                l.offsetX, l.offsetY, l.offsetZ,
                v.sizeX(), v.sizeY(), v.sizeZ(), v.minBlockX(), v.minBlockY(), v.minBlockZ(),
                v.stepBlockX(), v.stepBlockY(), v.stepBlockZ(),
                c.xzScale(), c.yScale(), c.amplitude());
        } catch (Throwable t) {
            throw new IllegalStateException("kn_noop failed", t);
        }
    }

    private static MethodHandle bind(String symbol, FunctionDescriptor descriptor) {
        MemorySegment address = LOOKUP.find(symbol)
            .orElseThrow(() -> new IllegalStateException("Symbol not found in " + libraryPath() + ": " + symbol));
        return LINKER.downcallHandle(address, descriptor, Linker.Option.critical(true));
    }

    private static Path libraryPath() {
        String path = System.getenv("KN_LIB");
        if (path == null || path.isEmpty()) {
            throw new IllegalStateException("KN_LIB is not set (path of the kindling_noise library)");
        }
        return Path.of(path);
    }
}
