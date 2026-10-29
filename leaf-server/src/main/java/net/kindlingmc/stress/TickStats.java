package net.kindlingmc.stress;

/** The tick times and TPS of a stress test, sampled once a second, summed up for the report at its end. */
final class TickStats {

    private long samples;
    private double msptSum;
    private double peakMspt;
    private double lowestTps = Double.MAX_VALUE;

    void add(final double mspt, final double tps) {
        this.samples++;
        this.msptSum += mspt;
        this.peakMspt = Math.max(this.peakMspt, mspt);
        this.lowestTps = Math.min(this.lowestTps, tps);
    }

    boolean isEmpty() {
        return this.samples == 0;
    }

    double averageMspt() {
        return this.samples == 0 ? 0 : this.msptSum / this.samples;
    }

    double peakMspt() {
        return this.peakMspt;
    }

    double lowestTps() {
        return this.samples == 0 ? 0 : this.lowestTps;
    }
}
