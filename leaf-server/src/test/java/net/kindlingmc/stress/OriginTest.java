package net.kindlingmc.stress;

import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginTest {

    @Test
    void anOriginIsReadBackAsItWasWritten() {
        final Origin origin = new Origin(NamespacedKey.minecraft("overworld"), 12.5, 64, -3.25, 90.5f, -12f);

        assertEquals(Optional.of(origin), Origin.parse(origin.write()));
    }

    @Test
    void aCustomWorldKeepsItsNamespace() {
        final Origin origin = new Origin(new NamespacedKey("my_plugin", "lobby"), 0, 100, 0, 0, 0);

        assertEquals(new NamespacedKey("my_plugin", "lobby"), Origin.parse(origin.write()).orElseThrow().world());
    }

    @Test
    void damagedDataIsNoOrigin() {
        assertTrue(Origin.parse("").isEmpty());
        assertTrue(Origin.parse("minecraft:overworld;1;2;3").isEmpty());
        assertTrue(Origin.parse("minecraft:overworld;1;two;3;0;0").isEmpty());
        assertTrue(Origin.parse("Not A Key;1;2;3;0;0").isEmpty());
    }
}
