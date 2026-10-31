package aquifer;

/**
 * Everything an aquifer asked its environment for: the sampler calls (which the shared sampler context turns into
 * cache state) and the fluid picker calls. Two aquifers are equivalent only if both logs match.
 */
record TraceLogs(EventLog samplers, EventLog picker) {
    static TraceLogs create() {
        return new TraceLogs(new EventLog(), new EventLog());
    }
}
