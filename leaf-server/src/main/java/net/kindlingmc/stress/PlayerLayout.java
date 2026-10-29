package net.kindlingmc.stress;

/**
 * Where the fake players are: spread out on a sunflower spiral around the centre, so each loads chunks of its own as
 * players on a real server do. A flying player moves along a line straight outward from its start, up to
 * {@value #FLIGHT_RANGE} blocks; its {@link Flight} says how far along the line it is.
 */
final class PlayerLayout {

    static final double HEIGHT = 160;
    static final double FLIGHT_RANGE = 1024;

    private static final double SPACING = 160;
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    record Spot(double x, double z) {
    }

    private PlayerLayout() {
    }

    /**
     * Where player number {@code index} is when it is {@code offset} blocks out along its line. A player's spot does not
     * depend on how many others there are, so adding players leaves the others where they are.
     */
    static Spot spot(final int index, final double offset) {
        final double angle = index * GOLDEN_ANGLE;
        final double radius = startRadius(index) + offset;
        return new Spot(Math.cos(angle) * radius, Math.sin(angle) * radius);
    }

    /** How far out along its line player number {@code index} is when it stands at {@code x}, {@code z}. */
    static double offsetAt(final int index, final double x, final double z) {
        return Math.clamp(Math.hypot(x, z) - startRadius(index), 0, FLIGHT_RANGE);
    }

    private static double startRadius(final int index) {
        return SPACING * Math.sqrt(index + 1);
    }
}
