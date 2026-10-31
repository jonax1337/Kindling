package noisekernel;

import java.util.List;

/**
 * Raw per-round results of one benchmark run.
 * nsPerSample[variant][part][round] with part 0 = perlin, 1 = smeared, 2 = combined.
 */
record Measurements(
    List<String> variants,
    long perlinSamples,
    long smearedSamples,
    long perlinCalls,
    long smearedCalls,
    int threads,
    int warmupRounds,
    double[][][] nsPerSample,
    double[][] nsPerCall,
    double checksum) {

    static final String[] PART_NAMES = {"perlin", "smeared", "combined"};

    int rounds() {
        return this.nsPerCall[0].length;
    }
}
