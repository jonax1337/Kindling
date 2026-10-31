package aquifer;

import java.util.List;

/**
 * Raw bench result, each array indexed [variant][round] and averaged over threads: nanoseconds per chunk to
 * construct the aquifer, nanoseconds per chunk to replay the fill, nanoseconds per column for construction plus
 * the whole column replay (getBaseColumn), and the same up to the first block that is not air (getBaseHeight). warmVerify is what each thread found when it verified the variants it had timed.
 */
record Measurements(List<String> variants, int chunkCount, int columnCount, int threads, int warmupRounds, double[][] constructNsPerChunk,
                    double[][] fillNsPerChunk, double[][] nsPerColumn, double[][] nsPerColumnEarly, int sink,
                    List<Tally> warmVerify) {
}
