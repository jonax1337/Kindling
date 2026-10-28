package net.kindlingmc.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VanillaVec3iHashTest {

    private static final int[] COORDINATES = {0, 1, -1, 7, -64, 319, 29_999_983, -29_999_984, Integer.MAX_VALUE, Integer.MIN_VALUE};

    @Test
    void hashesWithTheVanillaFormula() {
        for (final int x : COORDINATES) {
            for (final int y : COORDINATES) {
                for (final int z : COORDINATES) {
                    assertEquals(vanillaHash(x, y, z), new Vec3i(x, y, z).hashCode(), () -> x + " " + y + " " + z);
                }
            }
        }
    }

    @Test
    void blockPositionsUseTheSameHash() {
        assertEquals(1, new BlockPos(1, 0, 0).hashCode());
        assertEquals(31, new BlockPos(0, 1, 0).hashCode());
        assertEquals(961, new BlockPos(0, 0, 1).hashCode());
        assertEquals(vanillaHash(-12, 64, 345), new BlockPos(-12, 64, 345).hashCode());
    }

    private static int vanillaHash(final int x, final int y, final int z) {
        return (y + z * 31) * 31 + x;
    }
}
