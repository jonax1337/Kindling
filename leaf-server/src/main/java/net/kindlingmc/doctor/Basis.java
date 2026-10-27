package net.kindlingmc.doctor;

/** Where a finding's advice comes from, so an operator can tell numbers Kindling measured from rules of thumb. */
public enum Basis {
    KINDLING_MEASUREMENT("measured by Kindling"),
    GENERAL_ADVICE("general advice, not measured by Kindling"),
    LIVE_READING("read live from this machine");

    private final String label;

    Basis(final String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }
}
