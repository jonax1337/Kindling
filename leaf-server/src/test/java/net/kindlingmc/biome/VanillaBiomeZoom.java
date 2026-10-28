package net.kindlingmc.biome;

import net.minecraft.core.Holder;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;

/**
 * Vanilla's {@code BiomeManager.getBiome}, copied from the Minecraft sources and only split into choosing the corner and
 * reading its cell: every corner's fiddled distance in full, summed z + y + x, the first minimum wins.
 */
final class VanillaBiomeZoom {

    private VanillaBiomeZoom() {
    }

    static Holder<Biome> getBiome(final long biomeZoomSeed, final BiomeResolver noiseBiomeSource, final int x, final int y, final int z) {
        final int minI = nearestCorner(biomeZoomSeed, x, y, z);
        final int parentX = (x - 2) >> 2;
        final int parentY = (y - 2) >> 2;
        final int parentZ = (z - 2) >> 2;
        final int biomeX = (minI & 4) == 0 ? parentX : parentX + 1;
        final int biomeY = (minI & 2) == 0 ? parentY : parentY + 1;
        final int biomeZ = (minI & 1) == 0 ? parentZ : parentZ + 1;
        return noiseBiomeSource.getNoiseBiome(biomeX, biomeY, biomeZ);
    }

    static int nearestCorner(final long biomeZoomSeed, final int x, final int y, final int z) {
        int absX = x - 2;
        int absY = y - 2;
        int absZ = z - 2;
        int parentX = absX >> 2;
        int parentY = absY >> 2;
        int parentZ = absZ >> 2;
        double fractX = (absX & 3) / 4.0;
        double fractY = (absY & 3) / 4.0;
        double fractZ = (absZ & 3) / 4.0;
        int minI = 0;
        double minFiddledDistance = Double.POSITIVE_INFINITY;

        for (int i = 0; i < 8; i++) {
            boolean xEven = (i & 4) == 0;
            boolean yEven = (i & 2) == 0;
            boolean zEven = (i & 1) == 0;
            int cornerX = xEven ? parentX : parentX + 1;
            int cornerY = yEven ? parentY : parentY + 1;
            int cornerZ = zEven ? parentZ : parentZ + 1;
            double distanceX = xEven ? fractX : fractX - 1.0;
            double distanceY = yEven ? fractY : fractY - 1.0;
            double distanceZ = zEven ? fractZ : fractZ - 1.0;
            double next = getFiddledDistance(biomeZoomSeed, cornerX, cornerY, cornerZ, distanceX, distanceY, distanceZ);
            if (minFiddledDistance > next) {
                minI = i;
                minFiddledDistance = next;
            }
        }
        return minI;
    }

    private static double getFiddledDistance(
        final long seed, final int xRandom, final int yRandom, final int zRandom, final double distanceX, final double distanceY, final double distanceZ
    ) {
        long rval = seed;
        rval = LinearCongruentialGenerator.next(rval, xRandom);
        rval = LinearCongruentialGenerator.next(rval, yRandom);
        rval = LinearCongruentialGenerator.next(rval, zRandom);
        rval = LinearCongruentialGenerator.next(rval, xRandom);
        rval = LinearCongruentialGenerator.next(rval, yRandom);
        rval = LinearCongruentialGenerator.next(rval, zRandom);
        double fiddleX = getFiddle(rval);
        rval = LinearCongruentialGenerator.next(rval, seed);
        double fiddleY = getFiddle(rval);
        rval = LinearCongruentialGenerator.next(rval, seed);
        double fiddleZ = getFiddle(rval);
        return Mth.square(distanceZ + fiddleZ) + Mth.square(distanceY + fiddleY) + Mth.square(distanceX + fiddleX);
    }

    private static double getFiddle(final long rval) {
        double uniform = Math.floorMod(rval >> 24, 1024) / 1024.0;
        return (uniform - 0.5) * 0.9;
    }
}
