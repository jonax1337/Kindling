package net.kindlingmc.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientChunkPacerTest {

    private static final float NO_CAP = ClientChunkPacer.MAX_CHUNKS_PER_TICK;
    private static final long FIRST_BATCH_TICK = 100L;

    private final ClientChunkPacer pacer = new ClientChunkPacer();

    @Test
    void theFirstBatchCarriesVanillasStartingRate() {
        assertEquals(9, allowedAfterRefill(NO_CAP));
    }

    @Test
    void theCapLimitsTheStartingRate() {
        assertEquals(4, allowedAfterRefill(4.0F));
    }

    @Test
    void nothingMoreIsSentUntilTheFirstBatchIsAnswered() {
        sendAllowed(NO_CAP);
        assertEquals(0, allowedAfterRefill(NO_CAP));
    }

    @Test
    void anAnswerWithNothingOutstandingStartsTheQuotaAtOneChunk() {
        sendAllowed(NO_CAP);
        pacer.onBatchAnswered(0.5F);
        assertEquals(1, allowedAfterRefill(NO_CAP));
    }

    @Test
    void aFractionalRateSendsOneChunkEveryOtherTick() {
        answeredWith(0.5F);
        assertEquals(1, sendAllowed(NO_CAP));
        assertEquals(0, allowedAfterRefill(NO_CAP));
        assertEquals(1, allowedAfterRefill(NO_CAP));
    }

    @Test
    void aBatchIsAtMostOneTickOfTheClientsRate() {
        answeredWith(5.0F);
        for (int tick = 0; tick < 5; ++tick) {
            pacer.refillQuota(NO_CAP);
        }
        assertEquals(5, pacer.chunksAllowed());
    }

    @Test
    void afterTheFirstAnswerTenBatchesMayBeUnanswered() {
        answeredWith(2.0F);
        for (int batch = 0; batch < 10; ++batch) {
            assertEquals(2, sendAllowed(NO_CAP));
        }
        assertEquals(0, allowedAfterRefill(NO_CAP));
    }

    @Test
    void theCapLimitsAFastClient() {
        answeredWith(20.0F);
        assertEquals(8, allowedAfterRefill(8.0F));
    }

    @Test
    void anAnsweredRateIsClampedToVanillasMaximum() {
        answeredWith(1000.0F);
        assertEquals(64, allowedAfterRefill(NO_CAP));
    }

    @Test
    void aMissingRateBecomesVanillasMinimum() {
        assertMinimumRateAfterAnswer(Float.NaN);
    }

    @Test
    void aNegativeRateBecomesVanillasMinimum() {
        assertMinimumRateAfterAnswer(-5.0F);
    }

    private void assertMinimumRateAfterAnswer(final float desiredChunksPerTick) {
        answeredWith(desiredChunksPerTick);
        assertEquals(1, sendAllowed(NO_CAP));
        for (int tick = 0; tick < 10; ++tick) {
            assertEquals(0, allowedAfterRefill(NO_CAP));
        }
    }

    @Test
    void aClientIsPacedBeforeItsFirstBatch() {
        assertTrue(pacer.isPacing(0L));
    }

    @Test
    void aClientThatNeverAnswersIsNoLongerPacedAfterTheTimeout() {
        pacer.onBatchSent(9, FIRST_BATCH_TICK);
        assertTrue(pacer.isPacing(FIRST_BATCH_TICK + ClientChunkPacer.FIRST_ANSWER_TIMEOUT_TICKS - 1));
        assertFalse(pacer.isPacing(FIRST_BATCH_TICK + ClientChunkPacer.FIRST_ANSWER_TIMEOUT_TICKS));
    }

    @Test
    void aLateAnswerPacesTheClientAgain() {
        pacer.onBatchSent(9, FIRST_BATCH_TICK);
        pacer.onBatchAnswered(4.0F);
        assertTrue(pacer.isPacing(FIRST_BATCH_TICK + 10 * ClientChunkPacer.FIRST_ANSWER_TIMEOUT_TICKS));
        assertEquals(4, allowedAfterRefill(NO_CAP));
    }

    @Test
    void anAnsweringClientStaysPacedWhileItsBatchesAreOutstanding() {
        answeredWith(2.0F);
        sendAllowed(NO_CAP);
        assertTrue(pacer.isPacing(FIRST_BATCH_TICK + 10 * ClientChunkPacer.FIRST_ANSWER_TIMEOUT_TICKS));
    }

    @Test
    void moreAnswersThanBatchesDoNotOpenExtraBatches() {
        answeredWith(2.0F);
        pacer.onBatchAnswered(2.0F);
        pacer.onBatchAnswered(2.0F);
        for (int batch = 0; batch < 10; ++batch) {
            sendAllowed(NO_CAP);
        }
        assertEquals(0, allowedAfterRefill(NO_CAP));
    }

    private void answeredWith(final float desiredChunksPerTick) {
        pacer.onBatchSent(0, FIRST_BATCH_TICK);
        pacer.onBatchAnswered(desiredChunksPerTick);
    }

    private int allowedAfterRefill(final float maxChunksPerTick) {
        pacer.refillQuota(maxChunksPerTick);
        return pacer.chunksAllowed();
    }

    private int sendAllowed(final float maxChunksPerTick) {
        final int chunks = allowedAfterRefill(maxChunksPerTick);
        pacer.onBatchSent(chunks, FIRST_BATCH_TICK);
        return chunks;
    }
}
