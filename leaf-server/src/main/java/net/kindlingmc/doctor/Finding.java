package net.kindlingmc.doctor;

/**
 * The result of one check.
 *
 * @param what   the setting that was looked at and what was found
 * @param fix    what to change; empty for {@link Severity#OK}
 * @param basis  whether the advice rests on a Kindling measurement
 */
public record Finding(Severity severity, String what, String fix, Basis basis) {

    public static Finding ok(final String what) {
        return new Finding(Severity.OK, what, "", Basis.GENERAL_ADVICE);
    }

    public static Finding warn(final String what, final String fix, final Basis basis) {
        return new Finding(Severity.WARN, what, fix, basis);
    }

    public static Finding info(final String what, final String fix, final Basis basis) {
        return new Finding(Severity.INFO, what, fix, basis);
    }
}
