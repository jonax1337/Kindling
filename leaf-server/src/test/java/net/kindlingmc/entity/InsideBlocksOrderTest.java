package net.kindlingmc.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@VanillaFeature
class InsideBlocksOrderTest {

    private static final AABB COLUMN_AT_TARGET = new AABB(0.2, 0.5, 0.2, 0.8, 2.3, 0.8);

    @Test
    void visitsTheBlocksOfAFallingEntityFromTopToBottom() {
        final Vec3 from = new Vec3(0.5, 3.5, 0.5);
        final Vec3 to = new Vec3(0.5, 0.5, 0.5);

        assertEquals(column(5, 4, 3, 2, 1, 0), visitedBetween(from, to, COLUMN_AT_TARGET));
    }

    @Test
    void visitsTheBlocksOfARisingEntityFromBottomToTop() {
        final Vec3 from = new Vec3(0.5, -2.5, 0.5);
        final Vec3 to = new Vec3(0.5, 0.5, 0.5);

        assertEquals(column(-3, -2, -1, 0, 1, 2), visitedBetween(from, to, COLUMN_AT_TARGET));
    }

    @Test
    void visitsTheBlocksOfAStillEntityWithXChangingFastest() {
        final Vec3 position = new Vec3(1.5, 0.5, 1.0);
        final AABB box = new AABB(0.5, 0.5, 0.5, 2.5, 1.5, 1.5);

        final List<BlockPos> visited = visitedBetween(position, position, box);

        assertEquals(
            List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0), new BlockPos(0, 1, 0)),
            visited.subList(0, 4)
        );
    }

    private static List<BlockPos> column(final int... ys) {
        final List<BlockPos> column = new ArrayList<>();
        for (final int y : ys) {
            column.add(new BlockPos(0, y, 0));
        }
        return column;
    }

    private static List<BlockPos> visitedBetween(final Vec3 from, final Vec3 to, final AABB boxAtTarget) {
        final List<BlockPos> visited = new ArrayList<>();
        BlockGetter.forEachBlockIntersectedBetween(from, to, boxAtTarget, (pos, iteration) -> visited.add(pos.immutable()));
        return visited;
    }
}
