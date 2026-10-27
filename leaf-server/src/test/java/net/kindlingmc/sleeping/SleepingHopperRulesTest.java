package net.kindlingmc.sleeping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SleepingHopperRulesTest {

    private static final int SPIGOT_HOPPER_TRANSFER = 8;

    @Test
    void lithiumTransfersWhenPaperMovesOneItemWithoutEvents() {
        assertTrue(SleepingHopperRules.lithiumTransferMatchesPaper(true, 1));
    }

    @Test
    void paperTransfersWhenAPluginListensForMovedItems() {
        assertFalse(SleepingHopperRules.lithiumTransferMatchesPaper(false, 1));
    }

    @Test
    void paperTransfersWhenHoppersMoveMoreThanOneItem() {
        assertFalse(SleepingHopperRules.lithiumTransferMatchesPaper(true, 3));
    }

    @Test
    void hoppersSleepOnlyWhenPaperChecksEveryTick() {
        assertTrue(SleepingHopperRules.mayFallAsleep(1));
        assertFalse(SleepingHopperRules.mayFallAsleep(2));
        assertFalse(SleepingHopperRules.mayFallAsleep(8));
    }

    @Test
    void anEmptyHopperGetsTheTransferCooldownOrOneTickLessIfItAlreadyTicked() {
        assertEquals(8, SleepingHopperRules.receivingCooldown(SPIGOT_HOPPER_TRANSFER, false));
        assertEquals(7, SleepingHopperRules.receivingCooldown(SPIGOT_HOPPER_TRANSFER, true));
        assertEquals(4, SleepingHopperRules.receivingCooldown(4, false));
        assertEquals(3, SleepingHopperRules.receivingCooldown(4, true));
    }

    @Test
    void withSpigotsDefaultASleeperIsRecognisedAtSeven() {
        assertTrue(SleepingHopperRules.isReceivingCooldownOfSleeper(7, SPIGOT_HOPPER_TRANSFER));
        assertFalse(SleepingHopperRules.isReceivingCooldownOfSleeper(8, SPIGOT_HOPPER_TRANSFER));
        assertFalse(SleepingHopperRules.isReceivingCooldownOfSleeper(0, SPIGOT_HOPPER_TRANSFER));
    }

    @Test
    void aTunedTransferRateMovesTheSleepersCooldown() {
        assertTrue(SleepingHopperRules.isReceivingCooldownOfSleeper(3, 4));
        assertFalse(SleepingHopperRules.isReceivingCooldownOfSleeper(7, 4));
    }

    @Test
    void aTransferRateOfOneLeavesNoTickToSkip() {
        assertFalse(SleepingHopperRules.isReceivingCooldownOfSleeper(0, 1));
    }
}
