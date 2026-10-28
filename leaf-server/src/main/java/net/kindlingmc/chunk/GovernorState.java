package net.kindlingmc.chunk;

/**
 * The chunk worker count the governor has chosen, as a pure function of the host pressure samples it has seen. Two samples
 * of high pressure in a row take one worker away; a quiet period (every sample low) adds one back. A worker that is added
 * and taken back within one quiet period doubles the quiet period before the next try, up to {@value #MAX_BACKOFF} times,
 * so the count settles below the point where the host gets crowded instead of swinging around it; once an added worker
 * stays for a whole quiet period, the quiet period is back to normal.
 *
 * @param workers             the chosen count
 * @param pressedSamples      samples of high pressure in a row
 * @param quietSamples        samples of low pressure in a row
 * @param backoff             how many quiet periods the next step up waits for
 * @param samplesSinceStepUp  samples since a worker was added, {@link #NO_STEP_UP} when none was added or it was taken back
 */
public record GovernorState(int workers, int pressedSamples, int quietSamples, int backoff, int samplesSinceStepUp) {

    static final int PRESSED_SAMPLES_TO_STEP_DOWN = 2;
    static final int MAX_BACKOFF = 8;
    static final int NO_STEP_UP = Integer.MAX_VALUE;

    /** The state before the first sample: every configured worker runs. */
    public static GovernorState startingAt(final int workers) {
        return new GovernorState(workers, 0, 0, 1, NO_STEP_UP);
    }

    public GovernorState next(final HostPressure pressure, final GovernorLimits limits) {
        final GovernorState aged = this.within(limits).aged(limits);
        return switch (pressure) {
            case HIGH -> aged.pressed(limits);
            case LOW -> aged.quiet(limits);
            case MODERATE, UNKNOWN -> aged.holding();
        };
    }

    /** A reload can move the bounds; the count follows them. */
    private GovernorState within(final GovernorLimits limits) {
        return withWorkers(Math.clamp(this.workers, limits.floor(), limits.ceiling()));
    }

    private GovernorState aged(final GovernorLimits limits) {
        if (this.samplesSinceStepUp == NO_STEP_UP) {
            return this;
        }
        final int samples = this.samplesSinceStepUp + 1;
        final boolean stepUpHeld = samples >= limits.quietSamples();
        return new GovernorState(this.workers, this.pressedSamples, this.quietSamples, stepUpHeld ? 1 : this.backoff,
            stepUpHeld ? NO_STEP_UP : samples);
    }

    private GovernorState pressed(final GovernorLimits limits) {
        final int pressed = Math.min(this.pressedSamples + 1, PRESSED_SAMPLES_TO_STEP_DOWN);
        if (pressed < PRESSED_SAMPLES_TO_STEP_DOWN || this.workers <= limits.floor()) {
            return new GovernorState(this.workers, pressed, 0, this.backoff, this.samplesSinceStepUp);
        }
        final boolean stepUpTakenBack = this.samplesSinceStepUp != NO_STEP_UP;
        final int backoff = stepUpTakenBack ? Math.min(this.backoff * 2, MAX_BACKOFF) : this.backoff;
        return new GovernorState(this.workers - 1, 0, 0, backoff, NO_STEP_UP);
    }

    private GovernorState quiet(final GovernorLimits limits) {
        final int quiet = this.quietSamples + 1;
        if (quiet < limits.quietSamples() * this.backoff || this.workers >= limits.ceiling()) {
            return new GovernorState(this.workers, 0, quiet, this.backoff, this.samplesSinceStepUp);
        }
        return new GovernorState(this.workers + 1, 0, 0, this.backoff, 0);
    }

    private GovernorState holding() {
        return new GovernorState(this.workers, 0, 0, this.backoff, this.samplesSinceStepUp);
    }

    private GovernorState withWorkers(final int workers) {
        return new GovernorState(workers, this.pressedSamples, this.quietSamples, this.backoff, this.samplesSinceStepUp);
    }
}
