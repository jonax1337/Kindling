package net.kindlingmc.doctor;

/** Everything the checks look at, collected once so that the checks themselves stay pure. */
public record DoctorFacts(MachineFacts machine, JvmFacts jvm, ServerSettings server) {
}
