package net.kindlingmc.pregen;

import java.util.Optional;

/** The arguments of {@code /pregen start <world> <radius in blocks> [<x> <z>]}, checked. */
record StartArguments(int radiusBlocks, Optional<Center> center) {

    /** A position in blocks. */
    record Center(int x, int z) {
    }

    private static final int RADIUS_AT = 2;
    private static final int CENTER_X_AT = 3;
    private static final int CENTER_Z_AT = 4;

    /** @param args the whole command: {@code start}, the world, the radius and optionally x and z */
    static StartArguments parse(final String[] args) {
        if (args.length <= RADIUS_AT) {
            throw new IllegalArgumentException("Give the radius in blocks.");
        }
        if (args.length == CENTER_X_AT + 1) {
            throw new IllegalArgumentException("Give both x and z for the centre, or neither to use the spawn.");
        }
        if (args.length > CENTER_Z_AT + 1) {
            throw new IllegalArgumentException("Too many arguments.");
        }
        final int radius = wholeNumber("radius", args[RADIUS_AT]);
        if (radius < 0) {
            throw new IllegalArgumentException("The radius cannot be negative: " + radius);
        }
        final Optional<Center> center = args.length == CENTER_Z_AT + 1
            ? Optional.of(new Center(wholeNumber("x", args[CENTER_X_AT]), wholeNumber("z", args[CENTER_Z_AT])))
            : Optional.empty();
        return new StartArguments(radius, center);
    }

    private static int wholeNumber(final String name, final String text) {
        try {
            return Integer.parseInt(text);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("The " + name + " must be a whole number: '" + text + "'");
        }
    }
}
