package net.kindlingmc.doctor;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyLocationTest {

    @Test
    void aPlayerConnectionFromThisHostMeansTheProxyRunsHere() {
        assertEquals(Optional.of(ProxyLocation.SAME_HOST), ProxyLocation.fromPeers(1, 0));
        assertEquals(Optional.of(ProxyLocation.SAME_HOST), ProxyLocation.fromPeers(1, 3));
    }

    @Test
    void connectionsOnlyFromOtherHostsMeanTheProxyRunsElsewhere() {
        assertEquals(Optional.of(ProxyLocation.OTHER_HOST), ProxyLocation.fromPeers(0, 2));
    }

    @Test
    void withoutPlayersTheLocationIsNotKnown() {
        assertEquals(Optional.empty(), ProxyLocation.fromPeers(0, 0));
    }
}
