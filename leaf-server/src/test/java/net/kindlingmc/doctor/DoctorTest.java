package net.kindlingmc.doctor;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoctorTest {

    private final Scenario scenario = new Scenario();

    @Test
    void aWellTunedServerHasOnlyFineFindings() {
        final List<Finding> findings = Doctor.examine(scenario.facts());

        assertEquals(List.of(), findings.stream().filter(finding -> finding.severity() != Severity.OK).toList());
        assertEquals(11, findings.size());
    }

    @Test
    void everyWarningAndHintNamesItsFix() {
        scenario.processors = 2;
        scenario.maxHeapBytes = 512 * ByteSize.MIB;
        scenario.mallocArenaMax = Optional.empty();
        scenario.transparentHugePages = Optional.of("always");
        scenario.compactObjectHeaders = Optional.of(false);
        scenario.garbageCollector = Optional.of(GarbageCollector.SERIAL);
        scenario.behindProxy = true;

        final List<Finding> notFine = Doctor.examine(scenario.facts()).stream()
            .filter(finding -> finding.severity() != Severity.OK)
            .toList();

        assertTrue(notFine.size() >= 7, "found " + notFine.size());
        notFine.forEach(finding -> assertFalse(finding.fix().isBlank(), finding.what()));
    }

    @Test
    void everythingOnlyLinuxKnowsDropsOutOnOtherSystems() {
        scenario.cgroupCpuLimit = OptionalDouble.empty();
        scenario.glibc = Optional.empty();
        scenario.mallocArenaMax = Optional.empty();
        scenario.transparentHugePages = Optional.empty();
        scenario.trimNativeHeapIntervalMillis = OptionalLong.empty();
        scenario.garbageCollector = Optional.empty();
        scenario.compactObjectHeaders = Optional.empty();

        final List<Finding> findings = Doctor.examine(scenario.facts());

        assertTrue(findings.stream().allMatch(finding -> finding.severity() == Severity.OK));
        assertTrue(findings.stream().noneMatch(finding -> finding.what().contains("MALLOC_ARENA_MAX")));
        assertTrue(findings.stream().noneMatch(finding -> finding.what().contains("huge pages")));
    }
}
