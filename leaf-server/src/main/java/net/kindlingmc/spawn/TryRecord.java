package net.kindlingmc.spawn;

import java.util.Arrays;

/**
 * What a pre-check found at each step of one spawn try: the pick of a group's first step and whether the placement
 * checks passed. Every entry keeps the step it belongs to (group, position, distance to the nearest player), so the
 * replay uses an entry only for the very same step. Steps the pre-check could not finish have no entry.
 */
final class TryRecord {

    private static final byte HAS_PICK = 1;
    private static final byte HAS_VERDICT = 2;
    private static final byte PASSED = 4;
    private static final int INITIAL_CAPACITY = 16;

    private boolean recorded;
    private int length;
    private int[] groups = new int[INITIAL_CAPACITY];
    private int[] xs = new int[INITIAL_CAPACITY];
    private int[] zs = new int[INITIAL_CAPACITY];
    private double[] distances = new double[INITIAL_CAPACITY];
    private byte[] flags = new byte[INITIAL_CAPACITY];
    private Pick[] picks = new Pick[INITIAL_CAPACITY];

    void forget() {
        this.recorded = false;
        this.length = 0;
        Arrays.fill(this.picks, null);
    }

    void startRecording() {
        this.forget();
        this.recorded = true;
    }

    boolean isRecorded() {
        return this.recorded;
    }

    /** A record is replayed once: its random values must not be used for a second try. */
    void markReplayed() {
        this.recorded = false;
    }

    void putPick(final SpawnTry.Step step, final Pick pick) {
        final int attempt = this.entryFor(step);
        this.flags[attempt] |= HAS_PICK;
        this.picks[attempt] = pick;
    }

    void putVerdict(final SpawnTry.Step step, final boolean passed) {
        final int attempt = this.entryFor(step);
        this.flags[attempt] |= passed ? (byte) (HAS_VERDICT | PASSED) : HAS_VERDICT;
    }

    boolean hasPickFor(final SpawnTry.Step step) {
        return this.matches(step) && (this.flags[step.attempt()] & HAS_PICK) != 0;
    }

    boolean hasVerdictFor(final SpawnTry.Step step) {
        return this.matches(step) && (this.flags[step.attempt()] & HAS_VERDICT) != 0;
    }

    Pick pick(final int attempt) {
        return this.picks[attempt];
    }

    boolean passed(final int attempt) {
        return (this.flags[attempt] & PASSED) != 0;
    }

    private boolean matches(final SpawnTry.Step step) {
        final int attempt = step.attempt();
        return attempt < this.length
            && this.groups[attempt] == step.group()
            && this.xs[attempt] == step.pos().getX()
            && this.zs[attempt] == step.pos().getZ()
            && this.distances[attempt] == step.nearestPlayerDistanceSqr();
    }

    private int entryFor(final SpawnTry.Step step) {
        final int attempt = step.attempt();
        if (attempt < this.length) {
            return attempt;
        }
        this.ensureCapacity(attempt + 1);
        this.groups[attempt] = step.group();
        this.xs[attempt] = step.pos().getX();
        this.zs[attempt] = step.pos().getZ();
        this.distances[attempt] = step.nearestPlayerDistanceSqr();
        this.flags[attempt] = 0;
        this.picks[attempt] = null;
        this.length = attempt + 1;
        return attempt;
    }

    private void ensureCapacity(final int capacity) {
        if (capacity <= this.flags.length) {
            return;
        }
        final int grown = Math.max(capacity, this.flags.length * 2);
        this.groups = Arrays.copyOf(this.groups, grown);
        this.xs = Arrays.copyOf(this.xs, grown);
        this.zs = Arrays.copyOf(this.zs, grown);
        this.distances = Arrays.copyOf(this.distances, grown);
        this.flags = Arrays.copyOf(this.flags, grown);
        this.picks = Arrays.copyOf(this.picks, grown);
    }
}
