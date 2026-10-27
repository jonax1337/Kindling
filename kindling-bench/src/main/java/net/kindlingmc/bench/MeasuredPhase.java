package net.kindlingmc.bench;

import java.util.Map;

/** One measured stretch of a run: the tick times and what the stretch cost the machine. */
final class MeasuredPhase {

    private final TickRecorder ticks;
    private final PhaseMeter meter;

    MeasuredPhase(TickRecorder ticks, PhaseMeter meter) {
        this.ticks = ticks;
        this.meter = meter;
    }

    void start() {
        ticks.start();
        meter.start();
    }

    /** Ends the stretch and summarises it; the machine's figures are read first, as summarising the ticks takes time. */
    Map<String, Object> stop() {
        Map<String, Object> machine = meter.stop();
        Map<String, Object> summary = ticks.stop();
        summary.putAll(machine);
        return summary;
    }
}
