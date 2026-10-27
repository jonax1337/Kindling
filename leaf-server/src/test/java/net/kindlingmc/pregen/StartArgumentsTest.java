package net.kindlingmc.pregen;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartArgumentsTest {

    @Test
    void withoutACentreTheSpawnIsMeant() {
        final StartArguments arguments = StartArguments.parse(args("start", "world", "5000"));

        assertEquals(5000, arguments.radiusBlocks());
        assertTrue(arguments.center().isEmpty());
    }

    @Test
    void aCentreNeedsBothCoordinates() {
        final StartArguments arguments = StartArguments.parse(args("start", "world", "5000", "-120", "64"));

        assertEquals(Optional.of(new StartArguments.Center(-120, 64)), arguments.center());
    }

    @Test
    void xWithoutZIsRefused() {
        final IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> StartArguments.parse(args("start", "world", "5000", "100")));

        assertTrue(refused.getMessage().contains("both x and z"));
    }

    @Test
    void aNegativeRadiusIsRefused() {
        final IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> StartArguments.parse(args("start", "world", "-5")));

        assertTrue(refused.getMessage().contains("negative"));
    }

    @Test
    void aMissingRadiusIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> StartArguments.parse(args("start", "world")));
    }

    @Test
    void textInsteadOfANumberNamesTheArgument() {
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> StartArguments.parse(args("start", "world", "big"))).getMessage().contains("radius"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> StartArguments.parse(args("start", "world", "10", "x", "5"))).getMessage().contains("The x"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> StartArguments.parse(args("start", "world", "10", "5", "z"))).getMessage().contains("The z"));
    }

    @Test
    void extraArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> StartArguments.parse(args("start", "world", "10", "1", "2", "3")));
    }

    @Test
    void aRadiusOfZeroIsAllowed() {
        assertEquals(0, StartArguments.parse(args("start", "world", "0")).radiusBlocks());
    }

    private static String[] args(final String... values) {
        return values;
    }
}
