package net.kindlingmc.doctor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns findings into the text of {@code /kindling doctor}: warnings first, then infos, then what is fine; what the
 * host monitor read live comes last, in a section of its own.
 */
public final class DoctorReport {

    private static final String LIVE_HEADING = "LIVE";

    private DoctorReport() {
    }

    public static List<String> lines(final List<Finding> findings) {
        final List<Finding> settings = findings.stream().filter(finding -> !isLive(finding)).toList();
        final List<Finding> live = findings.stream().filter(DoctorReport::isLive).sorted(Comparator.comparing(Finding::severity)).toList();
        final List<String> lines = new ArrayList<>();
        lines.add(summary(findings));
        for (final Severity severity : Severity.values()) {
            final List<Finding> group = settings.stream().filter(finding -> finding.severity() == severity).toList();
            if (!group.isEmpty()) {
                lines.add(severity.name());
                group.forEach(finding -> lines.addAll(describe(finding)));
            }
        }
        if (!live.isEmpty()) {
            lines.add(LIVE_HEADING);
            live.forEach(finding -> lines.addAll(describe(finding)));
        }
        return lines;
    }

    private static boolean isLive(final Finding finding) {
        return finding.basis() == Basis.LIVE_READING;
    }

    private static String summary(final List<Finding> findings) {
        return "Kindling doctor: " + count(findings, Severity.WARN) + " warning(s), "
            + count(findings, Severity.INFO) + " hint(s), " + count(findings, Severity.OK) + " fine";
    }

    private static long count(final List<Finding> findings, final Severity severity) {
        return findings.stream().filter(finding -> finding.severity() == severity).count();
    }

    private static List<String> describe(final Finding finding) {
        if (finding.fix().isBlank()) {
            return List.of("  - " + finding.what());
        }
        return List.of(
            "  - " + finding.what() + " [" + finding.basis().label() + "]",
            "    Fix: " + finding.fix());
    }
}
