package net.kindlingmc.doctor;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HostReaderTest {

    @Test
    void aChunkWorkerIsRecognisedByTheFirstFifteenCharactersOfItsName() {
        assertEquals("Leaf Common Wor", HostReader.chunkWorkerNamePrefix("Leaf"));
        assertEquals("Kindling Common", HostReader.chunkWorkerNamePrefix("Kindling"));
    }

    @Test
    void aShortBrandKeepsTheWholeNameUpToTheNumber() {
        assertEquals("X Common Worker", HostReader.chunkWorkerNamePrefix("X"));
    }

    @Test
    void onLinuxTheReaderSeesTheMachineAndThisThread() {
        assumeTrue(LinuxFiles.runsOnLinux());
        final Optional<HostReader> reader = HostReader.forCurrentThread("Leaf");
        assumeTrue(reader.isPresent());

        final HostCounters counters = reader.get().read();

        assertTrue(counters.cpu().isPresent(), "no /proc/stat");
        assertTrue(counters.cpu().get().totalTicks() > 0);
    }

    @Test
    void offLinuxThereIsNoReaderForTheCurrentThread() {
        assumeTrue(!LinuxFiles.runsOnLinux());

        assertEquals(Optional.empty(), HostReader.forCurrentThread("Leaf"));
    }
}
