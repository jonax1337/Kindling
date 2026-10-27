package net.kindlingmc.pregen;

import net.kindlingmc.doctor.ByteSize;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskGuardTest {

    private static final long RESERVE = 2 * ByteSize.GIB;

    @Test
    void untilEnoughChunksArrivedTheSizeOfAChunkIsAssumed() {
        final DiskGuard guard = new DiskGuard(100 * ByteSize.GIB, 0L);

        final DiskForecast forecast = guard.forecast(99 * ByteSize.GIB, DiskGuard.CHUNKS_TO_MEASURE - 1, 1000L, RESERVE);

        assertFalse(forecast.measured());
        assertEquals(DiskGuard.ASSUMED_BYTES_PER_CHUNK, forecast.bytesPerChunk());
        assertEquals(1000L * DiskGuard.ASSUMED_BYTES_PER_CHUNK, forecast.neededBytes());
    }

    @Test
    void afterEnoughChunksTheSizeIsWhatTheDiskLostPerChunk() {
        final DiskGuard guard = new DiskGuard(100 * ByteSize.GIB, 0L);
        final long chunks = 10_000L;

        final DiskForecast forecast = guard.forecast(100 * ByteSize.GIB - chunks * 8192L, chunks, 5000L, RESERVE);

        assertTrue(forecast.measured());
        assertEquals(8192L, forecast.bytesPerChunk());
        assertEquals(5000L * 8192L, forecast.neededBytes());
    }

    @Test
    void aChunkIsNeverSmallerThanASectorOfARegionFile() {
        final DiskGuard guard = new DiskGuard(100 * ByteSize.GIB, 0L);

        final DiskForecast forecast = guard.forecast(100 * ByteSize.GIB - 10L, 10_000L, 1L, RESERVE);

        assertEquals(DiskGuard.SECTOR_BYTES, forecast.bytesPerChunk());
    }

    @Test
    void aDiskThatGainedSpaceFallsBackToTheAssumedSize() {
        final DiskGuard guard = new DiskGuard(50 * ByteSize.GIB, 0L);

        final DiskForecast forecast = guard.forecast(60 * ByteSize.GIB, 10_000L, 1L, RESERVE);

        assertFalse(forecast.measured());
    }

    @Test
    void rebasingStartsTheMeasurementAnew() {
        final DiskGuard guard = new DiskGuard(100 * ByteSize.GIB, 0L);
        guard.rebase(90 * ByteSize.GIB, 5000L);

        final DiskForecast forecast = guard.forecast(90 * ByteSize.GIB - 4096L * 1000L, 6000L, 1L, RESERVE);

        assertFalse(forecast.measured());
    }

    @Test
    void theRunStopsWhenWhatIsLeftAfterItWouldBeBelowTheReserve() {
        final DiskForecast fits = new DiskForecast(10 * ByteSize.GIB, 7 * ByteSize.GIB, 4096L, false, RESERVE);
        final DiskForecast exactly = new DiskForecast(10 * ByteSize.GIB, 8 * ByteSize.GIB, 4096L, false, RESERVE);
        final DiskForecast runsOut = new DiskForecast(10 * ByteSize.GIB, 8 * ByteSize.GIB + 1, 4096L, false, RESERVE);

        assertFalse(fits.runsOut());
        assertFalse(exactly.runsOut());
        assertTrue(runsOut.runsOut());
    }

    @Test
    void aReserveOfZeroOnlyStopsTheRunThatDoesNotFit() {
        assertFalse(new DiskForecast(5L, 5L, 1L, false, 0L).runsOut());
        assertTrue(new DiskForecast(5L, 6L, 1L, false, 0L).runsOut());
    }

    @Test
    void aDiskOfUnknownSizeNeverRunsOut() {
        assertFalse(new DiskForecast(PregenWorld.UNKNOWN_FREE_BYTES, Long.MAX_VALUE, 4096L, false, RESERVE).runsOut());
    }

    @Test
    void aHugeRunSaturatesInsteadOfOverflowing() {
        final DiskGuard guard = new DiskGuard(100 * ByteSize.GIB, 0L);

        final DiskForecast forecast = guard.forecast(100 * ByteSize.GIB, 0L, Long.MAX_VALUE / 2, RESERVE);

        assertEquals(Long.MAX_VALUE, forecast.neededBytes());
        assertTrue(forecast.runsOut());
    }

    @Test
    void theDescriptionNamesFreeSpaceNeedAndReserve() {
        final DiskForecast forecast = new DiskForecast(41 * ByteSize.GIB, 38 * ByteSize.GIB, 14 * 1024L, true, RESERVE);

        assertEquals("41.0 GiB free, about 38.0 GiB still needed (14 KiB per chunk, measured), reserve 2.0 GiB", forecast.describe());
    }
}
