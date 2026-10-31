package aquifer;

import java.util.Arrays;

/** An append-only log of three-long events; the traces of sampler and fluid picker calls are kept in these. */
final class EventLog {
    private static final int WIDTH = 3;

    private long[] data = new long[WIDTH * 1024];
    private int size;

    void add(long a, long b, long c) {
        if (this.size + WIDTH > this.data.length) {
            this.data = Arrays.copyOf(this.data, this.data.length * 2);
        }
        this.data[this.size] = a;
        this.data[this.size + 1] = b;
        this.data[this.size + 2] = c;
        this.size += WIDTH;
    }

    /** Number of events. */
    int count() {
        return this.size / WIDTH;
    }

    boolean sameEvent(int index, EventLog other, int otherIndex) {
        int at = index * WIDTH;
        int otherAt = otherIndex * WIDTH;
        return this.data[at] == other.data[otherAt] && this.data[at + 1] == other.data[otherAt + 1]
            && this.data[at + 2] == other.data[otherAt + 2];
    }

    String describe(int index) {
        int at = index * WIDTH;
        return String.format("[%x, %x, %x]", this.data[at], this.data[at + 1], this.data[at + 2]);
    }
}
