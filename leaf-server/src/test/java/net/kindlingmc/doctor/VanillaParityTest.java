package net.kindlingmc.doctor;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VanillaParityTest {

    private static VanillaParity.Values vanilla() {
        return new VanillaParity.Values(-1, -1, 1, 1, -1, 0, false, false, false, false);
    }

    private static List<String> deviatingSettings(final VanillaParity.Values values) {
        return VanillaParity.deviations(values).stream().map(VanillaParity.Deviation::setting).toList();
    }

    @Test
    void vanillaValuesDeviateInNothing() {
        assertEquals(List.of(), VanillaParity.deviations(vanilla()));
    }

    @Test
    void theOldGaleDefaultsAndTheOldLeafDefaultsDeviateInEverySetting() {
        final VanillaParity.Values oldDefaults = new VanillaParity.Values(10, 10, 10, 4, 100, 0.2, true, true, true, true);

        assertEquals(10, VanillaParity.deviations(oldDefaults).size());
    }

    @Test
    void aDeviationNamesTheRunningAndTheVanillaValue() {
        final VanillaParity.Values values = new VanillaParity.Values(-1, -1, 10, 1, -1, 0, false, false, false, false);

        assertEquals(
            List.of(new VanillaParity.Deviation("gale-world-defaults.yml or gale-world.yml: small-optimizations.reduced-intervals.check-stuck-in-wall", "10", "1")),
            VanillaParity.deviations(values));
    }

    @Test
    void anyNonNegativeProjectileChunkLoadLimitDeviates() {
        assertEquals(1, deviatingSettings(new VanillaParity.Values(0, -1, 1, 1, -1, 0, false, false, false, false)).size());
        assertEquals(1, deviatingSettings(new VanillaParity.Values(-1, 0, 1, 1, -1, 0, false, false, false, false)).size());
        assertEquals(1, deviatingSettings(new VanillaParity.Values(-5, 7, 1, 1, -1, 0, false, false, false, false)).size());
    }

    @Test
    void anIntervalOfOneOrLessChecksEveryTickLikeVanilla() {
        assertEquals(List.of(), VanillaParity.deviations(new VanillaParity.Values(-1, -1, 0, -3, -1, 0, false, false, false, false)));
        assertEquals(2, deviatingSettings(new VanillaParity.Values(-1, -1, 2, 2, -1, 0, false, false, false, false)).size());
    }

    @Test
    void aVillagerPickupDelayOfTenIsVanillasOwnDelay() {
        assertEquals(List.of(), VanillaParity.deviations(new VanillaParity.Values(-1, -1, 1, 1, 10, 0, false, false, false, false)));
        assertEquals(1, deviatingSettings(new VanillaParity.Values(-1, -1, 1, 1, 9, 0, false, false, false, false)).size());
        assertEquals(1, deviatingSettings(new VanillaParity.Values(-1, -1, 1, 1, 100, 0, false, false, false, false)).size());
    }

    @Test
    void aNonPositiveWakeUpDeviationKeepsPapersFixedDuration() {
        assertEquals(List.of(), VanillaParity.deviations(new VanillaParity.Values(-1, -1, 1, 1, -1, -0.5, false, false, false, false)));
        assertEquals(1, deviatingSettings(new VanillaParity.Values(-1, -1, 1, 1, -1, 0.01, false, false, false, false)).size());
    }

    @Test
    void everySwitchedOnLeafOptimisationDeviatesOnItsOwn() {
        assertEquals(1, deviatingSettings(new VanillaParity.Values(-1, -1, 1, 1, -1, 0, true, false, false, false)).size());
        assertEquals(1, deviatingSettings(new VanillaParity.Values(-1, -1, 1, 1, -1, 0, false, true, false, false)).size());
        assertEquals(1, deviatingSettings(new VanillaParity.Values(-1, -1, 1, 1, -1, 0, false, false, true, false)).size());
        assertEquals(1, deviatingSettings(new VanillaParity.Values(-1, -1, 1, 1, -1, 0, false, false, false, true)).size());
    }
}
