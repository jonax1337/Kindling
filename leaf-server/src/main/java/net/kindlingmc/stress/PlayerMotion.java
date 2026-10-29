package net.kindlingmc.stress;

import java.util.Locale;

/** How the fake players move: standing still, or flying back and forth so the server loads and generates chunks. */
enum PlayerMotion {

    STANDING("stand"),
    FLYING("fly");

    private final String keyword;

    PlayerMotion(final String keyword) {
        this.keyword = keyword;
    }

    String keyword() {
        return this.keyword;
    }

    static PlayerMotion named(final String keyword) {
        for (final PlayerMotion motion : values()) {
            if (motion.keyword.equals(keyword.toLowerCase(Locale.ROOT))) {
                return motion;
            }
        }
        throw new IllegalArgumentException("Players can " + STANDING.keyword + " or " + FLYING.keyword + ", not " + keyword + ".");
    }
}
