package net.kindlingmc.doctor;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StartupCheckTest {

    @Test
    void onlyWarningsAreLoggedWithTheirBasisAndFix() {
        final List<String> lines = StartupCheck.warningLines(List.of(
            Finding.ok("fine"),
            Finding.info("hint", "do it", Basis.GENERAL_ADVICE),
            Finding.warn("bad", "fix it", Basis.KINDLING_MEASUREMENT)));

        assertEquals(List.of("Kindling doctor: bad [" + Basis.KINDLING_MEASUREMENT.label() + "] Fix: fix it"), lines);
    }
}
