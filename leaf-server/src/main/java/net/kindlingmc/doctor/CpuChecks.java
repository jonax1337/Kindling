package net.kindlingmc.doctor;

import java.util.Optional;

/** Checks of the processors the server can use. */
final class CpuChecks {

    private static final int VERY_FEW_PROCESSORS = 2;

    private CpuChecks() {
    }

    static Optional<Finding> containerLimit(final MachineFacts machine) {
        if (machine.cgroupCpuLimit().isEmpty()) {
            return Optional.empty();
        }
        final int usable = machine.usableProcessors();
        if (machine.availableProcessors() <= usable) {
            return Optional.of(Finding.ok("The JVM's processor count (" + machine.availableProcessors()
                + ") agrees with the container's CPU limit"));
        }
        return Optional.of(Finding.warn(
            "The JVM sees " + machine.availableProcessors() + " processors but the container may only use "
                + machine.cgroupCpuLimit().getAsDouble() + ": thread pools are sized for cores that throttle",
            "Start Java with -XX:ActiveProcessorCount=" + usable + " and do not disable -XX:+UseContainerSupport",
            Basis.GENERAL_ADVICE));
    }

    static Optional<Finding> processorCount(final MachineFacts machine) {
        final int usable = machine.usableProcessors();
        if (usable > VERY_FEW_PROCESSORS) {
            return Optional.of(Finding.ok(usable + " processors available"));
        }
        return Optional.of(Finding.warn(
            "Only " + usable + " processor(s) available: the main thread, chunk workers and the garbage collector compete for them",
            "Give the server at least 4 processors, or lower view-distance and simulation-distance",
            Basis.GENERAL_ADVICE));
    }
}
