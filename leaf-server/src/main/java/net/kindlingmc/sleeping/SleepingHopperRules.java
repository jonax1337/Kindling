package net.kindlingmc.sleeping;

/**
 * What Leaf's sleeping hoppers (a port of Lithium) need to keep Paper's hopper behaviour: when Lithium's transfer
 * gives Paper's result, when a hopper may sleep at all, and which cooldown an item landing in a hopper starts.
 */
public final class SleepingHopperRules {

    private static final int VANILLA_HOPPER_AMOUNT = 1;
    private static final int VANILLA_HOPPER_CHECK = 1;

    private SleepingHopperRules() {
    }

    /**
     * Lithium's transfer moves exactly one item and fires no {@code InventoryMoveItemEvent}. It ends like Paper's only
     * when Paper moves one item as well ({@code hopper-amount}) and fires no event either: with a listener, Paper hands
     * it the moved item, counted by {@code hopper-amount}, and takes from the hopper what was moved, even when the
     * listener replaced the item.
     */
    public static boolean lithiumTransferMatchesPaper(final boolean moveEventsSkipped, final int hopperAmount) {
        return moveEventsSkipped && hopperAmount == VANILLA_HOPPER_AMOUNT;
    }

    /**
     * A sleeping hopper wakes as soon as something changes and moves at once. With {@code ticks-per.hopper-check}
     * above 1, Paper's hopper only looks again after that many ticks, so a hopper keeps Paper's timing only awake.
     */
    public static boolean mayFallAsleep(final int hopperCheck) {
        return hopperCheck <= VANILLA_HOPPER_CHECK;
    }

    /**
     * The cooldown Paper gives an empty hopper an item lands in: {@code ticks-per.hopper-transfer}, one tick less
     * when the hopper has already ticked in this game tick and so will not count down this tick.
     */
    public static int receivingCooldown(final int hopperTransfer, final boolean receiverTickedThisTick) {
        return receiverTickedThisTick ? hopperTransfer - 1 : hopperTransfer;
    }

    /**
     * Whether a cooldown is the one a sleeping hopper gets when an item lands in it. A sleeping hopper counts as
     * having ticked, so it gets the shorter cooldown and must then skip the current tick; otherwise, later in the
     * tick order, it would count down once more and move a tick earlier than on Paper. A cooldown of 0
     * ({@code ticks-per.hopper-transfer: 1}) is over either way, so there is no tick to skip.
     */
    public static boolean isReceivingCooldownOfSleeper(final int cooldown, final int hopperTransfer) {
        return cooldown > 0 && cooldown == receivingCooldown(hopperTransfer, true);
    }
}
