package net.kindlingmc.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticCommandsTest {

    @Test
    void runsTheFullCollectionCommand() {
        assertTrue(DiagnosticCommands.run("gcRun"));
    }

    @Test
    void anUnknownCommandIsReportedNotThrown() {
        assertFalse(DiagnosticCommands.run("noSuchCommand"));
    }
}
