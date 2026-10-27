package net.kindlingmc.doctor;

import java.util.ArrayList;
import java.util.List;

/** Turns findings into the text of {@code /kindling doctor}: warnings first, then infos, then what is fine. */
public final class DoctorReport {

    private DoctorReport() {
    }

    public static List<String> lines(final List<Finding> findings) {
        final List<String> lines = new ArrayList<>();
        lines.add(summary(findings));
        for (final Severity severity : Severity.values()) {
            final List<Finding> group = findings.stream().filter(finding -> finding.severity() == severity).toList();
            if (!group.isEmpty()) {
                lines.add(severity.name());
                group.forEach(finding -> lines.addAll(describe(finding)));
            }
        }
        return lines;
    }

    private static String summary(final List<Finding> findings) {
        return "Kindling doctor: " + count(findings, Severity.WARN) + " warning(s), "
            + count(findings, Severity.INFO) + " hint(s), " + count(findings, Severity.OK) + " fine";
    }

    private static long count(final List<Finding> findings, final Severity severity) {
        return findings.stream().filter(finding -> finding.severity() == severity).count();
    }

    private static List<String> describe(final Finding finding) {
        if (finding.severity() == Severity.OK) {
            return List.of("  - " + finding.what());
        }
        return List.of(
            "  - " + finding.what() + " [" + finding.basis().label() + "]",
            "    Fix: " + finding.fix());
    }
}
