package densitycompiler.eval;

/** Which of the two exact techniques a compiled sampler uses: corner-level pruning, block-level column tiling. */
public record Techniques(boolean pruning, boolean tiling) {
    public static final Techniques DENSE = new Techniques(false, false);
    public static final Techniques PRUNE = new Techniques(true, false);
    public static final Techniques TILE = new Techniques(false, true);
    public static final Techniques ALL = new Techniques(true, true);
}
