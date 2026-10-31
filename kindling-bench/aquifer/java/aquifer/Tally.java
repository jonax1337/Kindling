package aquifer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The verdict for one candidate on one label, e.g. "[FILL] overworld". Counts differences from the reference per
 * kind, and keeps a histogram of the reference's results to show what the data exercised.
 */
final class Tally {
    private static final int MAX_DETAILS = 3;

    private final String label;
    private final String candidate;
    private final List<String> details = new ArrayList<>();
    private long calls;
    private long resultMismatches;
    private long flagMismatches;
    private long samplerTraceMismatches;
    private long pickerTraceMismatches;
    private long errors;
    private long samplerEvents;
    private long pickerEvents;
    private long nullResults;
    private long airResults;
    private long waterResults;
    private long lavaResults;
    private long otherResults;
    private long flagsSet;

    Tally(String label, String candidate) {
        this.label = label;
        this.candidate = candidate;
    }

    String label() {
        return this.label;
    }

    String candidate() {
        return this.candidate;
    }

    boolean passed() {
        return this.calls > 0 && this.mismatches() == 0;
    }

    long mismatches() {
        return this.resultMismatches + this.flagMismatches + this.samplerTraceMismatches + this.pickerTraceMismatches + this.errors;
    }

    long resultMismatches() {
        return this.resultMismatches;
    }

    long flagMismatches() {
        return this.flagMismatches;
    }

    long samplerTraceMismatches() {
        return this.samplerTraceMismatches;
    }

    long pickerTraceMismatches() {
        return this.pickerTraceMismatches;
    }

    long errors() {
        return this.errors;
    }

    long calls() {
        return this.calls;
    }

    /** One reference call: counted in the histogram of what the data exercised. */
    void countReference(BlockState state, boolean flag) {
        this.calls++;
        if (state == null) {
            this.nullResults++;
        } else if (state.isAir()) {
            this.airResults++;
        } else if (state.is(Blocks.WATER)) {
            this.waterResults++;
        } else if (state.is(Blocks.LAVA)) {
            this.lavaResults++;
        } else {
            this.otherResults++;
        }
        if (flag) {
            this.flagsSet++;
        }
    }

    void countReferenceEvents(long samplers, long picker) {
        this.samplerEvents += samplers;
        this.pickerEvents += picker;
    }

    void resultMismatch(String detail) {
        this.resultMismatches++;
        this.addDetail(detail);
    }

    void flagMismatch(String detail) {
        this.flagMismatches++;
        this.addDetail(detail);
    }

    void samplerTraceMismatches(long count, String detail) {
        this.samplerTraceMismatches += count;
        if (count > 0) {
            this.addDetail("sampler trace: " + detail);
        }
    }

    void pickerTraceMismatches(long count, String detail) {
        this.pickerTraceMismatches += count;
        if (count > 0) {
            this.addDetail("picker trace: " + detail);
        }
    }

    void error(String detail) {
        this.errors++;
        this.addDetail(detail);
    }

    String summary() {
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "%s %-24s %-10s calls %,d | result %d, flag %d, sampler-trace %d of %,d events, picker-trace %d of %,d events, errors %d",
            this.passed() ? "PASS" : "FAIL", this.label, this.candidate, this.calls, this.resultMismatches, this.flagMismatches,
            this.samplerTraceMismatches, this.samplerEvents, this.pickerTraceMismatches, this.pickerEvents, this.errors));
        text.append(String.format(Locale.ROOT, "%n       reference results: null %,d, air %,d, water %,d, lava %,d, other %,d; update flag set %,d",
            this.nullResults, this.airResults, this.waterResults, this.lavaResults, this.otherResults, this.flagsSet));
        for (String detail : this.details) {
            text.append("\n       first difference: ").append(detail);
        }
        return text.toString();
    }

    private void addDetail(String detail) {
        if (this.details.size() < MAX_DETAILS) {
            this.details.add(detail);
        }
    }
}
