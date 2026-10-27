package net.kindlingmc.doctor;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinuxFilesTest {

    @Test
    void cpuMaxIsQuotaOverPeriod() {
        assertEquals(OptionalDouble.of(2.0), LinuxFiles.parseCpuMax("200000 100000\n"));
        assertEquals(OptionalDouble.of(0.5), LinuxFiles.parseCpuMax("50000 100000"));
    }

    @Test
    void cpuMaxWithoutALimitIsEmpty() {
        assertEquals(OptionalDouble.empty(), LinuxFiles.parseCpuMax("max 100000"));
        assertEquals(OptionalDouble.empty(), LinuxFiles.parseCpuMax(""));
        assertEquals(OptionalDouble.empty(), LinuxFiles.parseCpuMax("100000 0"));
    }

    @Test
    void memoryMaxIsTheNumberOfBytes() {
        assertEquals(OptionalLong.of(2147483648L), LinuxFiles.parseMemoryMax("2147483648\n"));
    }

    @Test
    void memoryMaxWithoutALimitIsEmpty() {
        assertEquals(OptionalLong.empty(), LinuxFiles.parseMemoryMax("max\n"));
    }

    @Test
    void theSelectedModeIsTheOneInBrackets() {
        assertEquals(Optional.of("madvise"), LinuxFiles.parseSelectedMode("always [madvise] never\n"));
        assertEquals(Optional.of("always"), LinuxFiles.parseSelectedMode("[always] madvise never"));
    }

    @Test
    void aListWithoutASelectionHasNoMode() {
        assertEquals(Optional.empty(), LinuxFiles.parseSelectedMode("always madvise never"));
    }

    @Test
    void aFileThatDoesNotExistReadsAsEmpty() {
        assertEquals(Optional.empty(), LinuxFiles.read(Path.of("/kindling/does/not/exist")));
    }
}
