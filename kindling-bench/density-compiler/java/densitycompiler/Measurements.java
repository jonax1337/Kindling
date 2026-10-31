package densitycompiler;

import java.util.List;

/**
 * Raw bench result. nsPerChunk and chunksPerSecond are indexed [variant][round]; ns per chunk is the mean over
 * threads of one thread's time for the whole chunk list divided by the list length. warmVerify is what the bench threads
 * found when they verified the instances they had timed.
 */
record Measurements(List<String> variants, int chunkCount, int threads, int warmupRounds, double[][] nsPerChunk,
                    double[][] chunksPerSecond, int sink, List<Verifier.Tally> warmVerify) {
}
