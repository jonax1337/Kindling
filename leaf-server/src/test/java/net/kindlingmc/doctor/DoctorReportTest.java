package net.kindlingmc.doctor;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DoctorReportTest {

    @Test
    void theReportGroupsWarningsBeforeHintsBeforeWhatIsFine() {
        final List<Finding> findings = List.of(
            Finding.ok("Heap fits"),
            Finding.info("No AOT cache", "Create one", Basis.KINDLING_MEASUREMENT),
            Finding.warn("Too few cores", "Add cores", Basis.GENERAL_ADVICE));

        assertEquals(List.of(
            "Kindling doctor: 1 warning(s), 1 hint(s), 1 fine",
            "WARN",
            "  - Too few cores [general advice, not measured by Kindling]",
            "    Fix: Add cores",
            "INFO",
            "  - No AOT cache [measured by Kindling]",
            "    Fix: Create one",
            "OK",
            "  - Heap fits"),
            DoctorReport.lines(findings));
    }

    @Test
    void emptyGroupsAreLeftOut() {
        assertEquals(List.of("Kindling doctor: 0 warning(s), 0 hint(s), 1 fine", "OK", "  - Heap fits"),
            DoctorReport.lines(List.of(Finding.ok("Heap fits"))));
    }

    @Test
    void liveFindingsGoIntoASectionOfTheirOwnAfterTheSettings() {
        final List<Finding> findings = List.of(
            Finding.ok("Heap fits"),
            Finding.note("Main thread waited for a CPU 2.0 % of the time", Basis.LIVE_READING),
            Finding.warn("Host took 9 % of the CPU time", "Move", Basis.LIVE_READING));

        assertEquals(List.of(
            "Kindling doctor: 1 warning(s), 1 hint(s), 1 fine",
            "OK",
            "  - Heap fits",
            "LIVE",
            "  - Host took 9 % of the CPU time [read live from this machine]",
            "    Fix: Move",
            "  - Main thread waited for a CPU 2.0 % of the time"),
            DoctorReport.lines(findings));
    }
}
