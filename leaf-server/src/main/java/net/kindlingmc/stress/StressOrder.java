package net.kindlingmc.stress;

import java.util.Locale;

/** What one {@code /kindling stress ...} asks for, read from the arguments after {@code stress}. */
sealed interface StressOrder {

    int MAX_PLAYERS = 100;
    int MAX_MOBS = 5000;
    int MAX_BASES = 16;

    /** Keeps {@code count} fake players in the test world, all moving as {@code motion} says. */
    record Players(int count, PlayerMotion motion) implements StressOrder {
    }

    /** Keeps {@code count} mobs alive around the centre of the test world. */
    record Mobs(int count) implements StressOrder {
    }

    /** Keeps {@code count} plots of hoppers, furnaces and redstone running above the test world. */
    record Bases(int count) implements StressOrder {
    }

    record Stop() implements StressOrder {
    }

    record Status() implements StressOrder {
    }

    static StressOrder parse(final String[] args) {
        if (args.length == 0) {
            return new Status();
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> withoutMore(args, new Status());
            case "stop" -> withoutMore(args, new Stop());
            case "players" -> players(args);
            case "mobs" -> new Mobs(countOf(args, "mobs", MAX_MOBS));
            case "bases" -> new Bases(countOf(args, "bases", MAX_BASES));
            default -> throw new IllegalArgumentException("Unknown stress action: " + args[0]);
        };
    }

    private static StressOrder players(final String[] args) {
        final int count = count(args, "players", MAX_PLAYERS);
        if (args.length == 2) {
            return new Players(count, PlayerMotion.STANDING);
        }
        if (args.length == 3) {
            return new Players(count, PlayerMotion.named(args[2]));
        }
        throw new IllegalArgumentException("Too many arguments for players.");
    }

    private static int countOf(final String[] args, final String what, final int max) {
        final int count = count(args, what, max);
        if (args.length > 2) {
            throw new IllegalArgumentException("Too many arguments for " + what + ".");
        }
        return count;
    }

    private static int count(final String[] args, final String what, final int max) {
        if (args.length < 2) {
            throw new IllegalArgumentException("How many " + what + "?");
        }
        final int count;
        try {
            count = Integer.parseInt(args[1]);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("The number of " + what + " must be a whole number, not " + args[1] + ".");
        }
        if (count < 0 || count > max) {
            throw new IllegalArgumentException("The number of " + what + " must be between 0 and " + max + ".");
        }
        return count;
    }

    private static StressOrder withoutMore(final String[] args, final StressOrder order) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Too many arguments for " + args[0] + ".");
        }
        return order;
    }
}
