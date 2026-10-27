package net.kindlingmc.os;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The nice value in a thread's stat file, with lines as the kernel writes them. */
class ThreadNiceTest {

    private static String stat(final String name, final int nice) {
        return "12345 (" + name + ") S 1 12300 12300 0 -1 4194368 100 0 0 0 50 10 0 0 " + (20 + nice) + " " + nice
            + " 60 0 12345678 4096000 2000 18446744073709551615 1 1 0 0 0 0 0 4096 17630 0 0 0 -1 3 0 0 0 0 0\n";
    }

    @Test
    void theNiceValueIsTheSeventeenthFieldAfterTheName() {
        assertEquals(OptionalInt.of(10), ThreadNice.parse(stat("Kindling Common", 10)));
        assertEquals(OptionalInt.of(-5), ThreadNice.parse(stat("Server thread", -5)));
    }

    @Test
    void aNameWithSpacesAndBracketsDoesNotShiftTheFields() {
        assertEquals(OptionalInt.of(7), ThreadNice.parse(stat("odd) (name ) x", 7)));
    }

    @Test
    void aShortOrBrokenLineHasNoNiceValue() {
        assertEquals(OptionalInt.empty(), ThreadNice.parse(""));
        assertEquals(OptionalInt.empty(), ThreadNice.parse("12345 (name) S 1 2 3"));
        assertEquals(OptionalInt.empty(), ThreadNice.parse("12345 name S 1 12300 12300 0 -1 4194368 100 0 0 0 50 10 0 0 30 10 60"));
        assertEquals(OptionalInt.empty(), ThreadNice.parse(stat("name", 0).replace(" 20 0 60 ", " 20 x 60 ")));
    }
}
