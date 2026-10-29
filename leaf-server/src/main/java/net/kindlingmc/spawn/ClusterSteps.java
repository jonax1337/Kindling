package net.kindlingmc.spawn;

import net.minecraft.util.RandomSource;

/**
 * Draws the group sizes and the steps of a natural spawn try the way Leaf's {@code NaturalSpawner
 * .spawnCategoryForPosition} does: from the bits of 64-bit random words instead of one {@code nextInt} per value. A
 * group starts with a size of 1 to 4 from two bits; each step takes four values of 0 to 5 from three bits each,
 * skipping 6 and 7, and moves by the difference of the first two in x and of the last two in z. The words come from
 * the random given to {@link #start}, so a pre-check and its replay can draw the same steps.
 */
final class ClusterSteps {

    private static final int USABLE_BITS = 62;

    private RandomSource random;
    private long word;
    private int usedBits;
    private int stepX;
    private int stepZ;

    void start(final RandomSource random) {
        this.random = random;
        this.word = random.nextLong();
        this.usedBits = 0;
    }

    int nextGroupSize() {
        final int size = (int) (((this.word >>> this.usedBits) & 0x3L) + 1);
        this.usedBits += 2;
        this.refillWhenSpent();
        return size;
    }

    void nextStep() {
        int first = 0;
        int second = 0;
        int third = 0;
        int fourth = 0;
        int valuesNeeded = 4;
        while (valuesNeeded > 0) {
            final int threeBits = (int) ((this.word >>> this.usedBits) & 0x7L);
            this.usedBits += 3;
            if (threeBits != 7 && threeBits != 6) {
                switch (valuesNeeded) {
                    case 1 -> fourth = threeBits;
                    case 2 -> third = threeBits;
                    case 3 -> second = threeBits;
                    default -> first = threeBits;
                }
                valuesNeeded--;
            }
            this.refillWhenSpent();
        }
        this.stepX = first - second;
        this.stepZ = third - fourth;
    }

    int stepX() {
        return this.stepX;
    }

    int stepZ() {
        return this.stepZ;
    }

    private void refillWhenSpent() {
        if (this.usedBits >= USABLE_BITS) {
            this.word = this.random.nextLong();
            this.usedBits = 0;
        }
    }
}
