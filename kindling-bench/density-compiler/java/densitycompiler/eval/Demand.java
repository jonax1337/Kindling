package densitycompiler.eval;

/** The indices of a region whose value someone reads. Pooled by Frame; capacity grows, never shrinks. */
final class Demand {
    boolean[] mask;
    int size;
    int count;

    Demand(int capacity) {
        this.mask = new boolean[capacity];
    }

    void reset(int size) {
        if (this.mask.length < size) {
            this.mask = new boolean[size];
        }
        this.size = size;
        this.count = 0;
    }

    void setAll(int size) {
        this.reset(size);
        java.util.Arrays.fill(this.mask, 0, size, true);
        this.count = size;
    }

    void copyFrom(Demand other) {
        this.reset(other.size);
        System.arraycopy(other.mask, 0, this.mask, 0, other.size);
        this.count = other.count;
    }

    boolean isEmpty() {
        return this.count == 0;
    }

    boolean isAll() {
        return this.count == this.size;
    }
}
