package net.kindlingmc.spawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The cluster steps draw exactly what Leaf's spawn loop draws from the same random words. */
class ClusterStepsTest {

    private static final int OPERATIONS = 20_000;

    @Test
    void drawsLeafsGroupSizesAndStepsInAnyOrderOfCalls() {
        for (long seed = 0; seed < 50; seed++) {
            final boolean[] operations = randomOperations(seed);
            assertEquals(leafDraws(seed, operations), clusterStepDraws(seed, operations), "seed " + seed);
        }
    }

    private static boolean[] randomOperations(final long seed) {
        final Random order = new Random(~seed);
        final boolean[] groupSizeAt = new boolean[OPERATIONS];
        for (int i = 0; i < OPERATIONS; i++) {
            groupSizeAt[i] = order.nextInt(5) == 0;
        }
        return groupSizeAt;
    }

    private static List<Integer> clusterStepDraws(final long seed, final boolean[] groupSizeAt) {
        final ClusterSteps steps = new ClusterSteps();
        steps.start(RandomSource.create(seed));
        final List<Integer> drawn = new ArrayList<>();
        for (final boolean groupSize : groupSizeAt) {
            if (groupSize) {
                drawn.add(steps.nextGroupSize());
            } else {
                steps.nextStep();
                drawn.add(steps.stepX());
                drawn.add(steps.stepZ());
            }
        }
        return drawn;
    }

    /** The bit handling of Leaf's NaturalSpawner.spawnCategoryForPosition, copied without changes. */
    private static List<Integer> leafDraws(final long seed, final boolean[] groupSizeAt) {
        final RandomSource random = RandomSource.create(seed);
        final List<Integer> drawn = new ArrayList<>();
        long rand = random.nextLong();
        int bits = 0;
        for (final boolean groupSize : groupSizeAt) {
            if (groupSize) {
                int max = (int) (((rand >>> bits) & 0x3L) + 1);
                bits += 2;
                if (bits >= 62) {
                    rand = random.nextLong();
                    bits = 0;
                }
                drawn.add(max);
                continue;
            }
            int rand1 = 0, rand2 = 0, rand3 = 0, rand4 = 0, valuesNeeded = 4;
            while (valuesNeeded > 0) {
                int threeBits = (int) ((rand >>> bits) & 0x7L);
                bits += 3;
                if (threeBits != 7 && threeBits != 6) {
                    switch (valuesNeeded) {
                        case 1 -> rand4 = threeBits;
                        case 2 -> rand3 = threeBits;
                        case 3 -> rand2 = threeBits;
                        default -> rand1 = threeBits;
                    }
                    valuesNeeded--;
                }
                if (bits >= 62) {
                    rand = random.nextLong();
                    bits = 0;
                }
            }
            drawn.add(rand1 - rand2);
            drawn.add(rand3 - rand4);
        }
        return drawn;
    }
}
