package net.kindlingmc.memory;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResidentMemoryTest {

    @Test
    void readsTheResidentSetFromTheStatusFileInBytes() {
        final String status = "Name:\tjava\nVmPeak:\t 4000000 kB\nVmRSS:\t  1536 kB\nRssAnon:\t 1000 kB\n";

        assertEquals(OptionalLong.of(1536L * 1024), ResidentMemory.parseBytes(status));
    }

    @Test
    void aStatusFileWithoutTheResidentSetGivesNothing() {
        assertEquals(OptionalLong.empty(), ResidentMemory.parseBytes("Name:\tjava\nVmPeak:\t 4000000 kB\n"));
    }
}
