package net.kindlingmc.os;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;

/** The nice value of a thread as Linux reports it in its {@code stat} file: "tid (name) state ... priority nice ...". */
public final class ThreadNice {

    private static final String TASK_STAT = "/proc/self/task/%d/stat";
    /** The state is field 3 of the file, the first after the name; the nice value is field 19. */
    private static final int NICE_FIELD_AFTER_NAME = 19 - 3;

    private ThreadNice() {
    }

    /** The nice value in the content of a thread's {@code stat} file; empty when it has none. */
    public static OptionalInt parse(final String stat) {
        // The name may contain spaces and brackets, so the fields start after the last closing bracket.
        final int nameEnd = stat.lastIndexOf(')');
        if (nameEnd < 0) {
            return OptionalInt.empty();
        }
        final String[] fields = stat.substring(nameEnd + 1).strip().split("\\s+");
        if (fields.length <= NICE_FIELD_AFTER_NAME) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(fields[NICE_FIELD_AFTER_NAME]));
        } catch (final NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /** The nice value of a thread of this process; empty when its {@code stat} file cannot be read. */
    static OptionalInt ofThread(final int threadId) {
        try {
            return parse(Files.readString(Path.of(TASK_STAT.formatted(threadId))));
        } catch (final IOException e) {
            return OptionalInt.empty();
        }
    }
}
