package net.kindlingmc.doctor;

import java.util.Optional;

/** Where the proxy in front of this server runs, as far as the server can tell. */
enum ProxyLocation {
    SAME_HOST,
    OTHER_HOST,
    UNKNOWN;

    /** From the connections of the players who are online, which all come from the proxy; empty when nobody is online. */
    static Optional<ProxyLocation> fromPeers(final int peersOnThisHost, final int peersOnOtherHosts) {
        if (peersOnThisHost > 0) {
            return Optional.of(SAME_HOST);
        }
        return peersOnOtherHosts > 0 ? Optional.of(OTHER_HOST) : Optional.empty();
    }
}
