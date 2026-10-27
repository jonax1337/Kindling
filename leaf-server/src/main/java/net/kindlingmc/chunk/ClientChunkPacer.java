package net.kindlingmc.chunk;

/**
 * Paces one connection's chunk sends by the client's answers to chunk batches, with the rules and starting values of
 * vanilla's {@code PlayerChunkSender}: after each batch the client reports how many chunks per tick it can take; every
 * tick that rate is added to a quota of at most one tick's worth (at least one chunk); a batch carries the quota's whole
 * chunks; and nothing is sent while too many batches are unanswered (one until the client first answers, ten after).
 * <p>
 * A client that has not answered its first batch within {@link #FIRST_ANSWER_TIMEOUT_TICKS} (an old, modded or fake
 * client) is not paced until it answers, so it gets Moonrise's fixed rate instead of waiting forever.
 * <p>
 * Answers are handled on the server thread and the sends happen while the player's world ticks; with parallel world
 * ticking the server thread waits for the world ticks to finish before it handles packets, so the two never overlap,
 * as for vanilla's own chunk sender.
 */
public final class ClientChunkPacer {

    public static final float MIN_CHUNKS_PER_TICK = 0.01F;
    public static final float MAX_CHUNKS_PER_TICK = 64.0F;
    static final float START_CHUNKS_PER_TICK = 9.0F;
    static final long FIRST_ANSWER_TIMEOUT_TICKS = 60L;
    private static final int MAX_UNANSWERED_BATCHES_BEFORE_FIRST_ANSWER = 1;
    private static final int MAX_UNANSWERED_BATCHES = 10;
    private static final float QUOTA_AFTER_ALL_ANSWERED = 1.0F;
    private static final long NO_BATCH_SENT = Long.MIN_VALUE;

    private float desiredChunksPerTick = START_CHUNKS_PER_TICK;
    private float batchQuota;
    private int unansweredBatches;
    private int maxUnansweredBatches = MAX_UNANSWERED_BATCHES_BEFORE_FIRST_ANSWER;
    private boolean answered;
    private long firstBatchTick = NO_BATCH_SENT;

    public boolean isPacing(final long tick) {
        return this.answered || this.firstBatchTick == NO_BATCH_SENT || tick - this.firstBatchTick < FIRST_ANSWER_TIMEOUT_TICKS;
    }

    /**
     * Adds one tick of the client's rate, at most {@code maxChunksPerTick}, to the quota; call once per tick.
     */
    public void refillQuota(final float maxChunksPerTick) {
        if (this.isWaitingForAnswers()) {
            return;
        }
        final float chunksPerTick = Math.min(this.desiredChunksPerTick, maxChunksPerTick);
        this.batchQuota = Math.min(this.batchQuota + chunksPerTick, Math.max(1.0F, chunksPerTick));
    }

    public int chunksAllowed() {
        return this.isWaitingForAnswers() ? 0 : (int) this.batchQuota;
    }

    public void onBatchSent(final int chunks, final long tick) {
        this.unansweredBatches++;
        this.batchQuota -= chunks;
        if (this.firstBatchTick == NO_BATCH_SENT) {
            this.firstBatchTick = tick;
        }
    }

    public void onBatchAnswered(final float desiredChunksPerTick) {
        this.unansweredBatches = Math.max(0, this.unansweredBatches - 1);
        this.desiredChunksPerTick = Float.isNaN(desiredChunksPerTick)
            ? MIN_CHUNKS_PER_TICK
            : Math.clamp(desiredChunksPerTick, MIN_CHUNKS_PER_TICK, MAX_CHUNKS_PER_TICK);
        if (this.unansweredBatches == 0) {
            this.batchQuota = QUOTA_AFTER_ALL_ANSWERED;
        }
        this.maxUnansweredBatches = MAX_UNANSWERED_BATCHES;
        this.answered = true;
    }

    private boolean isWaitingForAnswers() {
        return this.unansweredBatches >= this.maxUnansweredBatches;
    }
}
